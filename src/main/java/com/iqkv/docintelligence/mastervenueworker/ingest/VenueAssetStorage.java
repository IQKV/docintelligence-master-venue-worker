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

import java.util.UUID;

/**
 * The object store that holds master venue assets.
 *
 * <p>Stores the photo bytes of an ingest record and returns the key to persist on the
 * {@code public.master_venue_asset} row. Keys follow the master-catalog convention
 * {@code docintelligence/master-catalog/venues/{venueId}/assets/{assetId}/{fileName}}, where
 * {@code venueId} and {@code assetId} are UUIDs without hyphens.
 */
public interface VenueAssetStorage {

  /**
   * Stores one asset file under a venue and returns the key it was stored at.
   *
   * <p>The asset id is supplied by the caller so the stored key and the id written to
   * {@code master_venue_asset} agree.
   *
   * @param masterVenueId the venue of record the asset belongs to
   * @param assetId       the id to place in the storage key
   * @param asset         the photo file to store
   * @return the storage key to write to {@code master_venue_asset.s3_key}
   */
  String store(UUID masterVenueId, UUID assetId, VenueIngestAsset asset);
}
