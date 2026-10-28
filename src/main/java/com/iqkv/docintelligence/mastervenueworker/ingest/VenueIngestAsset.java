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

/**
 * One photo file of an ingest record — a file under the record's {@code assets/} folder that
 * becomes a {@code public.master_venue_asset} row of type {@code PHOTO}.
 *
 * @param fileName     the file name the scrape document names it by, e.g.
 *                     {@code 11896-the-last-talisman-venue.jpg}
 * @param file         absolute path of the downloaded file on the mounted data volume
 * @param sourceUrl    the provider URL the file was downloaded from; nullable when the scrape
 *                     document cited none
 * @param displayOrder zero-based position in the venue gallery, which is the render order
 */
public record VenueIngestAsset(String fileName, Path file, String sourceUrl, int displayOrder) {
}
