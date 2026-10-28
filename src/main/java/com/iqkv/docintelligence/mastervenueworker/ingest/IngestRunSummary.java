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
 * The outcome of one ingest cycle — what the poller found in the ingest tree and what it managed
 * to write into the master catalog.
 *
 * @param discovered record folders found ready for ingestion
 * @param ingested   records upserted into the master catalog and removed from the ingest tree
 * @param failed     records left on disk for the next cycle: unreadable documents and records
 *                   whose ingestion threw
 */
public record IngestRunSummary(int discovered, int ingested, int failed) {
}
