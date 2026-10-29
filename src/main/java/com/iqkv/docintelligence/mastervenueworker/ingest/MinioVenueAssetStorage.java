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

import com.iqkv.docintelligence.mastervenueworker.infrastructure.config.ObjectStorageConfigurationProperties;
import io.minio.MinioClient;
import io.minio.UploadObjectArgs;
import org.springframework.stereotype.Component;

/**
 * The MinIO/S3-backed {@link VenueAssetStorage} the worker stores master venue photos in.
 *
 * <p>Writes into the bucket shared with {@code docintelligence-catalog-service}
 * ({@code catalog-assets}) under the master-catalog prefix convention
 * {@code docintelligence/master-catalog/venues/{venueId}/assets/{assetId}/{fileName}}, with the
 * two ids written as UUIDs without hyphens.
 */
@Component
public class MinioVenueAssetStorage implements VenueAssetStorage {

  private static final String KEY_PREFIX = "docintelligence/master-catalog/venues";

  private final MinioClient minioClient;

  private final ObjectStorageConfigurationProperties properties;

  public MinioVenueAssetStorage(final MinioClient minioClient,
                                final ObjectStorageConfigurationProperties properties) {
    this.minioClient = minioClient;
    this.properties = properties;
  }

  @Override
  public String store(final UUID masterVenueId, final UUID assetId, final VenueIngestAsset asset) {
    final String key = key(masterVenueId, assetId, asset.fileName());
    try {
      this.minioClient.uploadObject(
          UploadObjectArgs.builder()
              .bucket(this.properties.bucketName())
              .object(key)
              .filename(asset.file().toString())
              .contentType(AssetContentTypes.of(asset.fileName()))
              .build());
    } catch (final Exception ex) {
      throw new IllegalStateException("cannot store asset " + key, ex);
    }
    return key;
  }

  private static String key(final UUID masterVenueId, final UUID assetId, final String fileName) {
    return KEY_PREFIX + "/" + compact(masterVenueId) + "/assets/" + compact(assetId) + "/" + fileName;
  }

  private static String compact(final UUID id) {
    return id.toString().replace("-", "");
  }
}
