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

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.iqkv.docintelligence.venue.MasterVenue;
import com.iqkv.docintelligence.venue.MasterVenueAsset;
import com.iqkv.docintelligence.venue.MasterVenueAssetMapper;
import com.iqkv.docintelligence.venue.MasterVenueExternal;
import com.iqkv.docintelligence.venue.MasterVenueMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * The default {@link VenueIngestService}: upserts every ready record of every enabled provider
 * into {@code public.master_venue}, {@code public.master_venue_external} and
 * {@code public.master_venue_asset}, then removes the record folder.
 *
 * <p>Writes go through the conflict-aware statements of {@code docintelligence-venue}, so a
 * re-delivered record refreshes the venue it already resolved to rather than duplicating it, and
 * an asset re-resolves the id its storage key is built around before its bytes are stored. The
 * three writes are therefore idempotent and need no transaction: a record that fails part-way
 * stays on disk and the next cycle completes it.
 */
@Service
public class VenueIngestServiceImpl implements VenueIngestService {

  private static final Logger log = LoggerFactory.getLogger(VenueIngestServiceImpl.class);

  private static final String PHOTO = "PHOTO";

  private static final String DEFAULT_SOURCE = "web_scrape";

  private final ObjectProvider<VenueDataProvider> providers;

  private final MasterVenueMapper masterVenueMapper;

  private final MasterVenueAssetMapper masterVenueAssetMapper;

  private final VenueAssetStorage assetStorage;

  public VenueIngestServiceImpl(final ObjectProvider<VenueDataProvider> providers,
                                final MasterVenueMapper masterVenueMapper,
                                final MasterVenueAssetMapper masterVenueAssetMapper,
                                final VenueAssetStorage assetStorage) {
    this.providers = providers;
    this.masterVenueMapper = masterVenueMapper;
    this.masterVenueAssetMapper = masterVenueAssetMapper;
    this.assetStorage = assetStorage;
  }

  @Override
  public IngestRunSummary ingestPending() {
    int discovered = 0;
    int ingested = 0;
    int failed = 0;
    for (final VenueDataProvider provider : this.providers.orderedStream().toList()) {
      for (final VenueRecordRef ref : provider.findPending()) {
        discovered++;
        try {
          final Optional<VenueIngestRecord> record = provider.read(ref);
          if (record.isEmpty()) {
            failed++;
            continue;
          }
          ingest(record.get());
          provider.remove(ref);
          ingested++;
        } catch (final RuntimeException ex) {
          failed++;
          log.warn("ingest failed for {} — left for the next cycle", ref.directory(), ex);
        }
      }
    }
    if (discovered > 0) {
      log.info("ingest cycle: discovered={} ingested={} failed={}", discovered, ingested, failed);
    }
    return new IngestRunSummary(discovered, ingested, failed);
  }

  private void ingest(final VenueIngestRecord record) {
    final MasterVenue stored = this.masterVenueMapper.upsertWithExternalIdentity(venue(record), external(record));
    storeAssets(stored.getId(), record);
    log.info("ingested venue provider={} externalId={} id={} name=\"{}\" city={} assets={}",
        record.provider(), record.providerExternalId(), stored.getId(), stored.getName(), record.city(),
        record.assets().size());
  }

  private static MasterVenue venue(final VenueIngestRecord record) {
    final MasterVenue venue = new MasterVenue();
    venue.setId(UUID.randomUUID());
    venue.setName(record.name());
    venue.setAddress(record.address());
    venue.setCity(record.city());
    venue.setLocationLat(record.locationLat());
    venue.setLocationLng(record.locationLng());
    venue.setMetadata(record.metadataJson());
    venue.setConfidence(record.confidence());
    venue.setSource(record.source() == null ? DEFAULT_SOURCE : record.source());
    venue.setPrimaryPhotoUrl(record.assets().isEmpty() ? null : record.assets().get(0).sourceUrl());
    return venue;
  }

  private static MasterVenueExternal external(final VenueIngestRecord record) {
    final MasterVenueExternal external = new MasterVenueExternal();
    external.setId(UUID.randomUUID());
    external.setProvider(record.provider());
    external.setProviderExternalId(record.providerExternalId());
    external.setRawPayload(record.rawPayloadJson());
    external.setConfidence(record.confidence());
    external.setCrawledAt(record.crawledAt());
    return external;
  }

  private void storeAssets(final UUID masterVenueId, final VenueIngestRecord record) {
    final List<VenueIngestAsset> assets = record.assets();
    for (int index = 0; index < assets.size(); index++) {
      final VenueIngestAsset asset = assets.get(index);
      // Re-resolve the id the bytes already occupy so a re-ingest overwrites in place instead of
      // orphaning the previously stored object.
      final UUID assetId = this.masterVenueAssetMapper
          .findByVenueAndFileName(masterVenueId, asset.fileName())
          .map(MasterVenueAsset::getId)
          .orElseGet(UUID::randomUUID);
      final String s3Key = this.assetStorage.store(masterVenueId, assetId, asset);

      final String contentType = AssetContentTypes.of(asset.fileName());
      final Long size = sizeBytes(asset);
      final MasterVenueAsset row = new MasterVenueAsset();
      row.setId(assetId);
      row.setMasterVenueId(masterVenueId);
      row.setAssetType(PHOTO);
      row.setDisplayOrder(asset.displayOrder());
      row.setFileName(asset.fileName());
      row.setContentType(contentType);
      row.setSizeBytes(size);
      row.setS3Key(s3Key);
      row.setSourceUrl(asset.sourceUrl());
      row.setProvider(record.provider());
      this.masterVenueAssetMapper.upsertByVenueAndFileName(row);
      log.info("stored asset venue={} id={} order={} type={} size={} file={} key={}",
          masterVenueId, assetId, asset.displayOrder(), contentType, size, asset.fileName(), s3Key);
    }
  }

  private static Long sizeBytes(final VenueIngestAsset asset) {
    try {
      return Files.size(asset.file());
    } catch (final IOException ex) {
      log.warn("cannot read size of {}", asset.file(), ex);
      return null;
    }
  }
}
