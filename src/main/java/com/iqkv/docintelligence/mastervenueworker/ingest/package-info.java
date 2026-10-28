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

/**
 * Venue ingest slice.
 *
 * <p>Consumes the {@code mc-ingest} tree that {@code docintelligence-mc-ingest-tagvenue-scraper}
 * writes into the mounted data volume: one record folder per venue, each holding a
 * {@code venue.json} document and a sibling {@code assets/} folder of photo files. The worker
 * polls that tree on a schedule, upserts every ready record into the master catalog —
 * {@code master_venue}, {@code master_venue_external} and {@code master_venue_asset} — and
 * removes the record once it has been ingested.
 *
 * <p>One tree can hold the output of several scrapers, so a record folder is named after the
 * provider that wrote it — {@code tagvenue-<venue_id>} — and every provider claims only the
 * folders carrying its own prefix. {@code VenueDataProvider} holds that rule.
 *
 * <p>The slice holds its models and its interfaces together; implementations land beside them,
 * as in {@code catalogservice.mastervenue}.
 */
package com.iqkv.docintelligence.mastervenueworker.ingest;
