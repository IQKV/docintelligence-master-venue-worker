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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.iqkv.docintelligence.venue.MasterVenue;
import com.iqkv.docintelligence.venue.MasterVenueMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

/**
 * Generates pgvector embeddings for {@link MasterVenue} rows and persists them via
 * {@link MasterVenueMapper#updateEmbedding}.
 *
 * <p>Called immediately after every successful upsert in {@link VenueIngestServiceImpl}
 * so each ingested row is born with an embedding, and again by {@link #backfill()} for rows
 * that missed it. Failures are logged and swallowed — the row remains searchable via keyword
 * search and the column stays {@code NULL} until the next backfill pass.
 *
 * <p>The text fed to the model is a compact natural-language summary of the searchable
 * fields: name, address, city, and the raw metadata JSON document (which carries
 * venue_type, amenities, catering policy, capacity, and restrictions).
 */
@Service
class VenueEmbeddingService {

  private static final Logger log = LoggerFactory.getLogger(VenueEmbeddingService.class);

  private static final int BACKFILL_BATCH = 100;

  private final EmbeddingModel embeddingModel;

  private final MasterVenueMapper masterVenueMapper;

  VenueEmbeddingService(final EmbeddingModel embeddingModel,
                        final MasterVenueMapper masterVenueMapper) {
    this.embeddingModel = embeddingModel;
    this.masterVenueMapper = masterVenueMapper;
  }

  /**
   * Embeds {@code venue} and writes the vector to the database.
   * Swallows any {@link RuntimeException} so a slow or unavailable Ollama instance
   * never blocks the ingest pipeline.
   *
   * @param venue the venue to embed; must have a non-null {@code id}
   * @return {@code true} when the vector reached the database
   */
  boolean embed(final MasterVenue venue) {
    try {
      final float[] vector = embeddingModel.embed(buildEmbeddingText(venue));
      masterVenueMapper.updateEmbedding(venue.getId(), vector);
      log.debug("embedded venue {} ({} dims)", venue.getId(), vector.length);
      return true;
    } catch (final RuntimeException ex) {
      log.warn("embedding skipped for venue {} — Ollama unavailable or slow: {}",
          venue.getId(), ex.getMessage());
      return false;
    }
  }

  /**
   * Embeds every venue whose {@code embedding} column is still {@code NULL}, oldest first.
   *
   * <p>A venue only ever got an embedding at the moment it was ingested, so a row that was
   * written before the vector write path worked — or during an Ollama outage — stays
   * invisible to vector search forever. This pass is what recovers those rows.
   *
   * <p>Stops as soon as a whole batch fails, so an unreachable Ollama cannot make the
   * scheduler spin on the same rows forever.
   *
   * @return the number of venues embedded
   */
  int backfill() {
    int embedded = 0;
    while (true) {
      final List<UUID> pending = this.masterVenueMapper.findUnembeddedIds(BACKFILL_BATCH);
      if (pending.isEmpty()) {
        break;
      }
      final int embeddedBefore = embedded;
      for (final UUID id : pending) {
        final Optional<MasterVenue> venue = this.masterVenueMapper.findById(id);
        if (venue.isPresent() && embed(venue.get())) {
          embedded++;
        }
      }
      if (embedded == embeddedBefore) {
        break;
      }
    }
    if (embedded > 0) {
      log.info("embedding backfill: embedded {} venue(s)", embedded);
    }
    return embedded;
  }

  /**
   * Builds the text fed to the embedding model.
   * Includes name, address, city, and raw metadata JSON so the model captures
   * venue type, amenities, capacity, catering, and restrictions.
   */
  static String buildEmbeddingText(final MasterVenue venue) {
    final var sb = new StringBuilder();
    if (venue.getName() != null) {
      sb.append(venue.getName()).append(". ");
    }
    if (venue.getAddress() != null) {
      sb.append(venue.getAddress()).append(". ");
    }
    if (venue.getCity() != null) {
      sb.append(venue.getCity()).append(". ");
    }
    if (venue.getMetadata() != null && !venue.getMetadata().isBlank()) {
      sb.append(venue.getMetadata());
    }
    return sb.toString().strip();
  }
}
