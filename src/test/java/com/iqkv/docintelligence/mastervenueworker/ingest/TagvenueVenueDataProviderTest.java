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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import com.iqkv.docintelligence.mastervenueworker.infrastructure.config.IngestConfigurationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class TagvenueVenueDataProviderTest {

  private static final String VENUE_JSON = """
      {
        "_schema_version": 1,
        "external_id": "tagvenue:venue:11896",
        "external_source": "tagvenue",
        "source": "web_scrape",
        "name": "The Last Talisman",
        "address": "1 Main Street, London",
        "city": "London",
        "location": { "lat": 51.5074, "lng": -0.1278 },
        "photos": {
          "count": 1,
          "gallery": [
            {
              "file": "11896-the-last-talisman-venue.jpg",
              "url": "https://www.tagvenue.com/resize/14/2f/widen-1680-noupsize;11896-the-last-talisman-venue.jpg",
              "transform": "widen-1680-noupsize"
            }
          ]
        },
        "source_url": "https://www.tagvenue.com/venues/london/11896/the-last-talisman",
        "metadata": {
          "_schema_version": 1,
          "items": [{ "group": "capacity", "field": "max_total", "value": 100 }]
        },
        "confidence": 0.82,
        "scrape_run": { "at": "2026-10-29T08:15:30.000Z" }
      }
      """;

  private static final String ASSET_FILE = "11896-the-last-talisman-venue.jpg";

  @TempDir
  Path root;

  private TagvenueVenueDataProvider provider;

  @BeforeEach
  void setUp() {
    final IngestConfigurationProperties properties = new IngestConfigurationProperties(
        root.toString(),
        Duration.ofSeconds(30),
        Map.of("tagvenue", new IngestConfigurationProperties.Source(true)));
    this.provider = new TagvenueVenueDataProvider(properties, JsonMapper.builder().build());
  }

  @Test
  @DisplayName("claims only the record folders named with its own prefix")
  void shouldClaimOnlyItsOwnFolders() {
    // Arrange, Act & Assert
    assertThat(this.provider.key()).isEqualTo("tagvenue");
    assertThat(this.provider.matches(this.root.resolve("tagvenue-11896"))).isTrue();
    assertThat(this.provider.matches(this.root.resolve("cvent-42"))).isFalse();
  }

  @Test
  @DisplayName("lists only its own folders that carry a venue.json document")
  void shouldListOnlyReadyRecords() {
    // Arrange
    write(this.root.resolve("tagvenue-11896").resolve("venue.json"), VENUE_JSON);
    write(this.root.resolve("tagvenue-2").resolve("assets").resolve("x.jpg"), "bytes");
    write(this.root.resolve("cvent-9").resolve("venue.json"), VENUE_JSON);

    // Act
    final var pending = this.provider.findPending();

    // Assert
    assertThat(pending).hasSize(1);
    assertThat(pending.getFirst().directory().getFileName().toString()).isEqualTo("tagvenue-11896");
  }

  @Test
  @DisplayName("maps the canonical document and its photo files into an ingest record")
  void shouldMapCanonicalDocument() {
    // Arrange
    final Path record = this.root.resolve("tagvenue-11896");
    write(record.resolve("venue.json"), VENUE_JSON);
    write(record.resolve("assets").resolve(ASSET_FILE), "bytes");

    // Act
    final var ingest = this.provider.read(new VenueRecordRef(record)).orElseThrow();

    // Assert
    assertThat(ingest.provider()).isEqualTo("tagvenue");
    assertThat(ingest.providerExternalId()).isEqualTo("tagvenue:venue:11896");
    assertThat(ingest.source()).isEqualTo("web_scrape");
    assertThat(ingest.name()).isEqualTo("The Last Talisman");
    assertThat(ingest.address()).isEqualTo("1 Main Street, London");
    assertThat(ingest.city()).isEqualTo("London");
    assertThat(ingest.locationLat()).isEqualByComparingTo("51.5074");
    assertThat(ingest.locationLng()).isEqualByComparingTo("-0.1278");
    assertThat(ingest.metadataJson()).contains("_schema_version");
    assertThat(ingest.confidence()).isEqualByComparingTo("0.82");
    assertThat(ingest.rawPayloadJson()).contains("The Last Talisman");
    assertThat(ingest.crawledAt()).isEqualTo(Instant.parse("2026-10-29T08:15:30Z"));
    assertThat(ingest.assets()).singleElement().satisfies(asset -> {
      assertThat(asset.fileName()).isEqualTo(ASSET_FILE);
      assertThat(asset.file()).isEqualTo(record.resolve("assets").resolve(ASSET_FILE));
      assertThat(asset.sourceUrl()).startsWith("https://www.tagvenue.com/resize");
      assertThat(asset.displayOrder()).isZero();
    });
  }

  @Test
  @DisplayName("returns empty for a document that cannot be parsed or names no venue")
  void shouldReturnEmptyForUnreadableDocument() {
    // Arrange
    write(this.root.resolve("tagvenue-1").resolve("venue.json"), "{\"external_id\": \"x\"}");
    write(this.root.resolve("tagvenue-2").resolve("venue.json"), "{ not json");

    // Act & Assert
    assertThat(this.provider.read(new VenueRecordRef(this.root.resolve("tagvenue-1")))).isEmpty();
    assertThat(this.provider.read(new VenueRecordRef(this.root.resolve("tagvenue-2")))).isEmpty();
  }

  @Test
  @DisplayName("drops the record folder once it has been ingested")
  void shouldRemoveRecordFolder() {
    // Arrange
    final Path record = this.root.resolve("tagvenue-11896");
    write(record.resolve("venue.json"), VENUE_JSON);
    write(record.resolve("assets").resolve(ASSET_FILE), "bytes");

    // Act
    this.provider.remove(new VenueRecordRef(record));

    // Assert
    assertThat(record).doesNotExist();
  }

  private static void write(final Path file, final String content) {
    try {
      Files.createDirectories(file.getParent());
      Files.writeString(file, content, StandardCharsets.UTF_8);
    } catch (final IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }
}
