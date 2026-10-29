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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import com.iqkv.docintelligence.venue.MasterVenue;
import com.iqkv.docintelligence.venue.MasterVenueAsset;
import com.iqkv.docintelligence.venue.MasterVenueAssetMapper;
import com.iqkv.docintelligence.venue.MasterVenueExternal;
import com.iqkv.docintelligence.venue.MasterVenueMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class VenueIngestServiceImplTest {

  private static final String ASSET_FILE = "11896-the-last-talisman-venue.jpg";

  @Mock
  private ObjectProvider<VenueDataProvider> providers;

  @Mock
  private VenueDataProvider provider;

  @Mock
  private MasterVenueMapper masterVenueMapper;

  @Mock
  private MasterVenueAssetMapper masterVenueAssetMapper;

  @Mock
  private VenueAssetStorage assetStorage;

  @TempDir
  Path root;

  private VenueIngestServiceImpl service;

  @BeforeEach
  void setUp() {
    this.service = new VenueIngestServiceImpl(
        this.providers, this.masterVenueMapper, this.masterVenueAssetMapper, this.assetStorage);
  }

  @Test
  @DisplayName("upserts a ready record, stores its asset and drops its folder")
  void shouldIngestReadyRecordAndDropItsFolder() {
    // Arrange
    final VenueRecordRef ref = new VenueRecordRef(this.root.resolve("tagvenue-11896"));
    final Path file = writeAsset();
    final UUID venueId = UUID.randomUUID();
    when(this.providers.orderedStream()).thenReturn(Stream.of(this.provider));
    when(this.provider.findPending()).thenReturn(List.of(ref));
    when(this.provider.read(ref)).thenReturn(Optional.of(record(file)));
    when(this.masterVenueMapper.upsertWithExternalIdentity(any(), any())).thenReturn(venue(venueId));
    when(this.masterVenueAssetMapper.findByVenueAndFileName(venueId, ASSET_FILE)).thenReturn(Optional.empty());
    when(this.assetStorage.store(any(), any(), any())).thenReturn("stored-key");

    // Act
    final IngestRunSummary summary = this.service.ingestPending();

    // Assert
    assertThat(summary).isEqualTo(new IngestRunSummary(1, 1, 0));
    verify(this.provider).remove(ref);

    final ArgumentCaptor<MasterVenue> venueCaptor = ArgumentCaptor.forClass(MasterVenue.class);
    final ArgumentCaptor<MasterVenueExternal> externalCaptor = ArgumentCaptor.forClass(MasterVenueExternal.class);
    verify(this.masterVenueMapper).upsertWithExternalIdentity(venueCaptor.capture(), externalCaptor.capture());
    assertThat(venueCaptor.getValue().getName()).isEqualTo("The Last Talisman");
    assertThat(venueCaptor.getValue().getCity()).isEqualTo("London");
    assertThat(venueCaptor.getValue().getLocationLat()).isEqualByComparingTo("51.5074");
    assertThat(venueCaptor.getValue().getMetadata()).contains("_schema_version");
    assertThat(venueCaptor.getValue().getSource()).isEqualTo("web_scrape");
    assertThat(externalCaptor.getValue().getProvider()).isEqualTo("tagvenue");
    assertThat(externalCaptor.getValue().getProviderExternalId()).isEqualTo("tagvenue:venue:11896");
    assertThat(externalCaptor.getValue().getRawPayload()).contains("The Last Talisman");

    final ArgumentCaptor<UUID> storedId = ArgumentCaptor.forClass(UUID.class);
    verify(this.assetStorage).store(eq(venueId), storedId.capture(), any());
    final ArgumentCaptor<MasterVenueAsset> rowCaptor = ArgumentCaptor.forClass(MasterVenueAsset.class);
    verify(this.masterVenueAssetMapper).upsertByVenueAndFileName(rowCaptor.capture());
    assertThat(rowCaptor.getValue().getId()).isEqualTo(storedId.getValue());
    assertThat(rowCaptor.getValue().getMasterVenueId()).isEqualTo(venueId);
    assertThat(rowCaptor.getValue().getAssetType()).isEqualTo("PHOTO");
    assertThat(rowCaptor.getValue().getContentType()).isEqualTo("image/jpeg");
    assertThat(rowCaptor.getValue().getSizeBytes()).isPositive();
    assertThat(rowCaptor.getValue().getS3Key()).isEqualTo("stored-key");
    assertThat(rowCaptor.getValue().getSourceUrl()).isEqualTo("https://example.test/photo.jpg");
    assertThat(rowCaptor.getValue().getProvider()).isEqualTo("tagvenue");
  }

  @Test
  @DisplayName("re-resolves the stored asset id so a re-ingest overwrites in place")
  void shouldReuseExistingAssetIdOnReingest() {
    // Arrange
    final VenueRecordRef ref = new VenueRecordRef(this.root.resolve("tagvenue-11896"));
    final Path file = writeAsset();
    final UUID venueId = UUID.randomUUID();
    final UUID existingId = UUID.randomUUID();
    when(this.providers.orderedStream()).thenReturn(Stream.of(this.provider));
    when(this.provider.findPending()).thenReturn(List.of(ref));
    when(this.provider.read(ref)).thenReturn(Optional.of(record(file)));
    when(this.masterVenueMapper.upsertWithExternalIdentity(any(), any())).thenReturn(venue(venueId));
    when(this.masterVenueAssetMapper.findByVenueAndFileName(venueId, ASSET_FILE))
        .thenReturn(Optional.of(asset(existingId)));
    when(this.assetStorage.store(any(), any(), any())).thenReturn("stored-key");

    // Act
    this.service.ingestPending();

    // Assert
    verify(this.assetStorage).store(eq(venueId), eq(existingId), any());
    final ArgumentCaptor<MasterVenueAsset> rowCaptor = ArgumentCaptor.forClass(MasterVenueAsset.class);
    verify(this.masterVenueAssetMapper).upsertByVenueAndFileName(rowCaptor.capture());
    assertThat(rowCaptor.getValue().getId()).isEqualTo(existingId);
  }

  @Test
  @DisplayName("leaves an unreadable record in place and reports it as failed")
  void shouldLeaveUnreadableRecordInPlace() {
    // Arrange
    final VenueRecordRef ref = new VenueRecordRef(this.root.resolve("tagvenue-11896"));
    when(this.providers.orderedStream()).thenReturn(Stream.of(this.provider));
    when(this.provider.findPending()).thenReturn(List.of(ref));
    when(this.provider.read(ref)).thenReturn(Optional.empty());

    // Act
    final IngestRunSummary summary = this.service.ingestPending();

    // Assert
    assertThat(summary).isEqualTo(new IngestRunSummary(1, 0, 1));
    verify(this.provider, never()).remove(any());
    verifyNoInteractions(this.masterVenueMapper, this.masterVenueAssetMapper, this.assetStorage);
  }

  @Test
  @DisplayName("keeps a record whose ingestion threw for the next cycle")
  void shouldKeepRecordWhenIngestionThrows() {
    // Arrange
    final VenueRecordRef ref = new VenueRecordRef(this.root.resolve("tagvenue-11896"));
    final Path file = writeAsset();
    when(this.providers.orderedStream()).thenReturn(Stream.of(this.provider));
    when(this.provider.findPending()).thenReturn(List.of(ref));
    when(this.provider.read(ref)).thenReturn(Optional.of(record(file)));
    when(this.masterVenueMapper.upsertWithExternalIdentity(any(), any()))
        .thenThrow(new IllegalStateException("database unavailable"));

    // Act
    final IngestRunSummary summary = this.service.ingestPending();

    // Assert
    assertThat(summary).isEqualTo(new IngestRunSummary(1, 0, 1));
    verify(this.provider, never()).remove(any());
  }

  @Test
  @DisplayName("does nothing when no provider is enabled")
  void shouldDoNothingWithoutProviders() {
    // Arrange
    when(this.providers.orderedStream()).thenReturn(Stream.empty());

    // Act
    final IngestRunSummary summary = this.service.ingestPending();

    // Assert
    assertThat(summary).isEqualTo(new IngestRunSummary(0, 0, 0));
    verifyNoInteractions(this.masterVenueMapper, this.masterVenueAssetMapper, this.assetStorage);
  }

  private Path writeAsset() {
    try {
      final Path assets = Files.createDirectories(this.root.resolve("tagvenue-11896").resolve("assets"));
      return Files.writeString(assets.resolve(ASSET_FILE), "bytes", StandardCharsets.UTF_8);
    } catch (final IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }

  private static VenueIngestRecord record(final Path file) {
    return new VenueIngestRecord(
        "tagvenue", "tagvenue:venue:11896", "web_scrape", "The Last Talisman", "1 Main Street, London",
        "London", new BigDecimal("51.5074"), new BigDecimal("-0.1278"), "{\"_schema_version\":1}",
        new BigDecimal("0.82"), "{\"name\":\"The Last Talisman\"}", Instant.parse("2026-10-29T08:15:30Z"),
        List.of(new VenueIngestAsset(ASSET_FILE, file, "https://example.test/photo.jpg", 0)));
  }

  private static MasterVenue venue(final UUID id) {
    final MasterVenue venue = new MasterVenue();
    venue.setId(id);
    venue.setName("The Last Talisman");
    return venue;
  }

  private static MasterVenueAsset asset(final UUID id) {
    final MasterVenueAsset asset = new MasterVenueAsset();
    asset.setId(id);
    return asset;
  }
}
