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

/**
 * The single entry point into the venue ingest slice, driven by the scheduled poller.
 *
 * <p>One call discovers every record folder ready in the ingest tree, upserts each one into the
 * master catalog, and removes the record once its venue, external identity and assets are all
 * written. A record that fails is left on disk and counted in {@link IngestRunSummary#failed()},
 * so the next cycle retries it.
 */
public interface VenueIngestService {

  /**
   * Ingests every record folder currently ready in the ingest tree.
   *
   * <p>Runs single-threaded and returns only when every discovered record has been ingested or
   * recorded as failed.
   *
   * @return the counts of the cycle; never {@code null}
   */
  IngestRunSummary ingestPending();
}
