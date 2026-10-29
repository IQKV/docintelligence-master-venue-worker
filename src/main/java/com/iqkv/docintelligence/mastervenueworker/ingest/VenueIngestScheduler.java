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

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Polls the ingest tree on a fixed delay, driving {@link VenueIngestService#ingestPending()}.
 *
 * <p>The delay is {@code iqkv.ingest.poll-interval} and is a fix delay, so a cycle starts that
 * long after the previous one finished rather than on a fixed wall clock cadence — a slow cycle
 * never overlaps the next one.
 */
@Component
public class VenueIngestScheduler {

  private final VenueIngestService venueIngestService;

  public VenueIngestScheduler(final VenueIngestService venueIngestService) {
    this.venueIngestService = venueIngestService;
  }

  @Scheduled(fixedDelayString = "${iqkv.ingest.poll-interval}")
  void poll() {
    this.venueIngestService.ingestPending();
  }
}
