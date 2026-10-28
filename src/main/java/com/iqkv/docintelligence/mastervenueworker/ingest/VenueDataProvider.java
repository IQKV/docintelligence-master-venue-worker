/*
 * Copyright 2026 iQKV Foundation Team.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.iqkv.docintelligence.mastervenueworker.ingest;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * The single contract a venue data provider implements: a source of scrape records the ingest
 * slice can pull from.
 *
 * <p>The slice knows only these three steps — enumerate what is ready, read one record into the
 * canonical model, and drop it once it has been ingested — so it stays independent of where the
 * records physically come from. Today the only implementation is the {@code mc-ingest} tree that
 * {@code docintelligence-mc-ingest-tagvenue-scraper} writes into the mounted data volume: one
 * record folder per venue, holding a {@code venue.json} document and a sibling {@code assets/}
 * folder of photo files. A record counts as ready only once its {@code venue.json} exists — the
 * scraper downloads the assets first and writes the document last, so a folder without it is
 * still being populated.
 *
 * <p>Every provider reads that one shared tree, so each provider claims the record folders named
 * after it — {@code tagvenue-<venue_id>} for Tagvenue — and leaves the folders of the other
 * providers untouched. Which names those are is the provider's own rule, expressed by
 * {@link #matches(java.nio.file.Path)}.
 *
 * <p>Each implementation is wired in only while its own source is enabled, keyed by {@link #key()}
 * in {@code iqkv.ingest.source.<key>.enabled} — e.g. {@code iqkv.ingest.source.tagvenue.enabled}.
 * Switching a source off removes its provider bean, so nothing reads that source's records.
 */
public interface VenueDataProvider {

  /**
   * The key this provider is configured and watched under, e.g. {@code tagvenue} for
   * {@code iqkv.ingest.source.tagvenue}. It names the provider's entry in the source toggles, it
   * prefixes the names of its record folders in the ingest tree, and it matches the
   * {@code provider} the records it produces carry into {@code master_venue_external}.
   *
   * @return the provider key; never {@code null}
   */
  String key();

  /**
   * Whether a directory of the shared ingest tree is a record folder of this provider.
   *
   * <p>This is the rule that keeps one shared tree workable for several providers: a provider
   * reads only the folders named after it and never touches the folders of the others. The
   * convention the scrapers follow names a record folder {@code <provider key>-<provider venue
   * id>} — {@code tagvenue-11896} — so the default claims any name that starts with {@link #key()}
   * and a hyphen. A source that names its folders differently overrides this method and nothing
   * else.
   *
   * @param directory one directory of the ingest tree
   * @return {@code true} when the directory belongs to this provider
   */
  default boolean matches(Path directory) {
    Path name = directory.getFileName();
    return name != null && name.toString().startsWith(key() + "-");
  }

  /**
   * Lists the records that are ready to be ingested.
   *
   * <p>Only folders this provider {@link #matches(Path) claims} and whose {@code venue.json} is
   * present are listed; everything else in the shared tree is left for its own provider or for a
   * later cycle.
   *
   * <p>Never {@code null}; a provider with nothing pending yields an empty list. Ordering is
   * unspecified, so callers must not depend on it.
   *
   * @return one handle per ready record
   */
  List<VenueRecordRef> findPending();

  /**
   * Reads one record into the canonical ingest model, resolving its photo files against the
   * assets the provider holds for it.
   *
   * @param ref the record to read, as returned by {@link #findPending()}
   * @return the parsed record, or empty when the record is unreadable or cannot be parsed — the
   *         caller leaves such a record in place for the next cycle
   */
  Optional<VenueIngestRecord> read(VenueRecordRef ref);

  /**
   * Drops a record from the provider after it has been ingested, so the next cycle does not
   * repeat it.
   *
   * @param ref the record to drop, as returned by {@link #findPending()}
   */
  void remove(VenueRecordRef ref);
}
