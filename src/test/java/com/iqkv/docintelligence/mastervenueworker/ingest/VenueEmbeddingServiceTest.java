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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.iqkv.docintelligence.venue.MasterVenue;
import com.iqkv.docintelligence.venue.MasterVenueMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;

@ExtendWith(MockitoExtension.class)
class VenueEmbeddingServiceTest {

  @Mock
  private EmbeddingModel embeddingModel;

  @Mock
  private MasterVenueMapper masterVenueMapper;

  private VenueEmbeddingService embeddingService;

  @BeforeEach
  void setUp() {
    embeddingService = new VenueEmbeddingService(embeddingModel, masterVenueMapper);
  }

  @Test
  @DisplayName("embed writes the vector and reports success")
  void shouldWriteVector() {
    // Arrange
    final UUID id = UUID.randomUUID();
    when(embeddingModel.embed(anyString())).thenReturn(new float[]{1.0f, 2.0f});
    final var vector = ArgumentCaptor.forClass(float[].class);

    // Act
    final boolean embedded = embeddingService.embed(venue(id));

    // Assert
    assertThat(embedded).isTrue();
    verify(masterVenueMapper).updateEmbedding(eq(id), vector.capture());
    assertThat(vector.getValue()).containsExactly(1.0f, 2.0f);
  }

  @Test
  @DisplayName("embed reports failure and writes nothing when Ollama is unavailable")
  void shouldReportFailureWithoutWriting() {
    // Arrange
    when(embeddingModel.embed(anyString())).thenThrow(new IllegalStateException("ollama down"));

    // Act
    final boolean embedded = embeddingService.embed(venue(UUID.randomUUID()));

    // Assert
    assertThat(embedded).isFalse();
    verify(masterVenueMapper, never()).updateEmbedding(any(), any());
  }

  @Test
  @DisplayName("backfill embeds every pending venue and stops once the table is drained")
  void shouldBackfillUntilDrained() {
    // Arrange
    final UUID first = UUID.randomUUID();
    final UUID second = UUID.randomUUID();
    when(masterVenueMapper.findUnembeddedIds(anyInt()))
        .thenReturn(List.of(first, second))
        .thenReturn(List.of());
    when(masterVenueMapper.findById(first)).thenReturn(Optional.of(venue(first)));
    when(masterVenueMapper.findById(second)).thenReturn(Optional.of(venue(second)));
    when(embeddingModel.embed(anyString())).thenReturn(new float[]{0.5f});
    final var ids = ArgumentCaptor.forClass(UUID.class);

    // Act
    final int embedded = embeddingService.backfill();

    // Assert
    assertThat(embedded).isEqualTo(2);
    verify(masterVenueMapper, times(2)).updateEmbedding(ids.capture(), any(float[].class));
    assertThat(ids.getAllValues()).containsExactly(first, second);
  }

  @Test
  @DisplayName("backfill stops after one batch when the whole batch fails")
  void shouldStopWhenEveryEmbeddingFails() {
    // Arrange
    final UUID pending = UUID.randomUUID();
    when(masterVenueMapper.findUnembeddedIds(anyInt())).thenReturn(List.of(pending));
    when(masterVenueMapper.findById(pending)).thenReturn(Optional.of(venue(pending)));
    when(embeddingModel.embed(anyString())).thenThrow(new IllegalStateException("ollama down"));

    // Act
    final int embedded = embeddingService.backfill();

    // Assert
    assertThat(embedded).isZero();
    verify(masterVenueMapper, times(1)).findUnembeddedIds(anyInt());
  }

  private static MasterVenue venue(final UUID id) {
    final MasterVenue venue = new MasterVenue();
    venue.setId(id);
    venue.setName("Test Venue");
    venue.setCity("Berlin");
    return venue;
  }
}
