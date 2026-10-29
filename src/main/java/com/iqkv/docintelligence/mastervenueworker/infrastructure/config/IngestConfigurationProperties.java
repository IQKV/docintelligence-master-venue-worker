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

package com.iqkv.docintelligence.mastervenueworker.infrastructure.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Binds {@code iqkv.ingest.*} from application.yml.
 *
 * <p>{@code source} holds one entry per data provider, keyed by the provider key — e.g.
 * {@code iqkv.ingest.source.tagvenue.enabled}. Each entry's {@code enabled} flag drives
 * {@code @ConditionalOnProperty} on the configuration class that wires that provider's
 * {@code VenueDataProvider}, so a source is switched off by its own flag rather than by a switch
 * shared with the other sources.
 *
 * <p>{@code dir} is the ingest tree written by
 * {@code docintelligence-mc-ingest-tagvenue-scraper} into the volume shared with this worker: one
 * record folder per venue, each holding a {@code venue.json} document and an {@code assets/}
 * folder of photo files. The scraper calls that same directory {@code ingestDir}, which is
 * {@code <outputDir>/mc-ingest} on its side; this worker polls it, upserts every ready record into
 * the master catalog, and removes the folder once it has been ingested.
 *
 * <p>In the container the shared volume is mounted at {@code /data}, so {@code dir} is
 * {@code /data/mc-ingest}. Keep it an absolute path: the worker resolves record folders directly
 * against it.
 *
 * <p>{@code pollInterval} is the delay between two scans of that tree — the fix delay of the
 * scheduled poller, so a cycle starts that long after the previous one finished rather than on a
 * fixed wall-clock cadence. It bounds how long a freshly scraped record waits before it lands in
 * the catalog, and defaults to half an hour.
 */
@Validated
@ConfigurationProperties(prefix = "iqkv.ingest")
public record IngestConfigurationProperties(
    // The ingest tree written by the scraper, e.g. /data/mc-ingest.
    @NotBlank String dir,
    // Delay between two scans of the ingest tree, e.g. PT30M.
    @NotNull Duration pollInterval,
    @Valid @NotNull Map<String, Source> source
) {

  /**
   * The toggle of one data provider, keyed in {@code source} by that provider's key.
   */
  public record Source(
      // Whether this source is polled. Off means its provider beans are not created at all.
      boolean enabled
  ) {
  }
}
