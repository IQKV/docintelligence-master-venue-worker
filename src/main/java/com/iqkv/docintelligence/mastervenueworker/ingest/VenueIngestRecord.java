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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * One scrape record read from the ingest tree — the {@code venue.json} document of a single venue
 * folder together with the photo files its {@code assets/} folder holds.
 *
 * <p>Field names follow the canonical scrape schema written by
 * {@code docintelligence-mc-ingest-tagvenue-scraper}: {@code provider} is the document's
 * {@code external_source} and {@code providerExternalId} its {@code external_id}, which follows
 * the suite-wide format {@code <provider>:venue:<provider venue id>}.
 *
 * <p>{@code metadataJson} and {@code rawPayloadJson} are JSON documents carried as text:
 * the first becomes {@code master_venue.metadata} and must keep its {@code _schema_version} key,
 * the second is the complete scrape document kept as {@code master_venue_external.raw_payload}.
 *
 * @param provider           the provider the record was scraped from, e.g. {@code tagvenue}
 * @param providerExternalId the provider's canonical venue identifier, the conflict target of
 *                           {@code master_venue_external}
 * @param source             how the record was produced, e.g. {@code web_scrape}
 * @param name               the venue name
 * @param address            the venue address as scraped; nullable
 * @param city               the venue city; nullable
 * @param locationLat        latitude of the venue; nullable when the scrape carried no coordinates
 * @param locationLng        longitude of the venue; nullable when the scrape carried no coordinates
 * @param metadataJson       the canonical metadata document as JSON text
 * @param confidence         the extractor confidence score; nullable
 * @param rawPayloadJson     the complete scrape document, kept as provenance
 * @param crawledAt          when the provider page was fetched, from {@code scrape_run.at}
 * @param assets             the photo files downloaded next to the record; never {@code null}
 */
public record VenueIngestRecord(
    String provider,
    String providerExternalId,
    String source,
    String name,
    String address,
    String city,
    BigDecimal locationLat,
    BigDecimal locationLng,
    String metadataJson,
    BigDecimal confidence,
    String rawPayloadJson,
    Instant crawledAt,
    List<VenueIngestAsset> assets
) {
}
