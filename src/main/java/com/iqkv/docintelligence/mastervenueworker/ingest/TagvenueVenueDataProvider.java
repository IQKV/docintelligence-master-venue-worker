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
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import com.iqkv.docintelligence.mastervenueworker.infrastructure.config.IngestConfigurationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The {@code tagvenue} {@link VenueDataProvider}: reads the record folders
 * {@code docintelligence-mc-ingest-tagvenue-scraper} writes into the shared ingest tree.
 *
 * <p>Each record folder is named {@code tagvenue-<venue_id>} and holds the venue's canonical
 * {@code venue.json} document next to an {@code assets/} folder of downloaded photos. This
 * provider translates that document into the slice's canonical {@link VenueIngestRecord}:
 * {@code external_source} becomes the provider, {@code external_id} the provider external id,
 * {@code location} the coordinates, {@code metadata} the canonical metadata document and
 * {@code photos.gallery} the asset list, with the complete document kept as the raw payload.
 */
public final class TagvenueVenueDataProvider implements VenueDataProvider {

  private static final Logger log = LoggerFactory.getLogger(TagvenueVenueDataProvider.class);

  private static final String KEY = "tagvenue";
  private static final String RECORD_FILE = "venue.json";
  private static final String ASSETS_DIR = "assets";
  private static final String DEFAULT_METADATA = "{\"_schema_version\":1}";

  private final IngestConfigurationProperties properties;

  private final JsonMapper jsonMapper;

  public TagvenueVenueDataProvider(final IngestConfigurationProperties properties,
                                   final JsonMapper jsonMapper) {
    this.properties = properties;
    this.jsonMapper = jsonMapper;
  }

  @Override
  public String key() {
    return KEY;
  }

  @Override
  public List<VenueRecordRef> findPending() {
    final Path root = Path.of(this.properties.dir());
    if (!Files.isDirectory(root)) {
      log.debug("ingest tree {} does not exist yet", root);
      return List.of();
    }
    try (Stream<Path> entries = Files.list(root)) {
      return entries
          .filter(Files::isDirectory)
          .filter(this::matches)
          .filter(dir -> Files.isRegularFile(dir.resolve(RECORD_FILE)))
          .sorted(Comparator.comparing(Path::toString))
          .map(VenueRecordRef::new)
          .toList();
    } catch (final IOException ex) {
      log.warn("cannot list ingest tree {}", root, ex);
      return List.of();
    }
  }

  @Override
  public Optional<VenueIngestRecord> read(final VenueRecordRef ref) {
    final Path document = ref.directory().resolve(RECORD_FILE);
    try {
      final String raw = Files.readString(document, StandardCharsets.UTF_8);
      return Optional.of(toRecord(ref, this.jsonMapper.readTree(raw), raw));
    } catch (final IOException | RuntimeException ex) {
      log.warn("cannot read ingest record {}", ref.directory(), ex);
      return Optional.empty();
    }
  }

  @Override
  public void remove(final VenueRecordRef ref) {
    try (Stream<Path> paths = Files.walk(ref.directory())) {
      paths.sorted(Comparator.reverseOrder()).forEach(TagvenueVenueDataProvider::deleteQuietly);
    } catch (final IOException ex) {
      log.warn("cannot remove ingest record {}", ref.directory(), ex);
    }
  }

  private VenueIngestRecord toRecord(final VenueRecordRef ref, final JsonNode root, final String raw) {
    final String provider = text(root, "external_source");
    final String externalId = text(root, "external_id");
    final String name = text(root, "name");
    if (externalId == null || name == null) {
      throw new IllegalArgumentException("record without external_id or name: " + ref.directory());
    }
    final JsonNode location = root.path("location");
    final JsonNode metadata = root.path("metadata");
    final String source = text(root, "source");
    return new VenueIngestRecord(
        provider == null ? KEY : provider,
        externalId,
        source == null ? "web_scrape" : source,
        name,
        text(root, "address"),
        text(root, "city"),
        decimal(location, "lat"),
        decimal(location, "lng"),
        metadata.isObject() ? metadata.toString() : DEFAULT_METADATA,
        decimal(root, "confidence"),
        raw,
        instant(root.path("scrape_run"), "at"),
        assets(ref, root.path("photos").path("gallery"))
    );
  }

  private List<VenueIngestAsset> assets(final VenueRecordRef ref, final JsonNode gallery) {
    final List<VenueIngestAsset> assets = new ArrayList<>();
    if (!gallery.isArray()) {
      return assets;
    }
    final Path assetsDir = ref.directory().resolve(ASSETS_DIR);
    int order = 0;
    for (final JsonNode photo : gallery) {
      final String fileName = text(photo, "file");
      if (fileName == null) {
        continue;
      }
      final Path file = assetsDir.resolve(fileName);
      if (!Files.isRegularFile(file)) {
        continue;
      }
      assets.add(new VenueIngestAsset(fileName, file, text(photo, "url"), order++));
    }
    return assets;
  }

  private static String text(final JsonNode node, final String field) {
    final JsonNode value = node.path(field);
    if (!value.isValueNode()) {
      return null;
    }
    final String text = value.asString();
    return text.isBlank() ? null : text;
  }

  private static BigDecimal decimal(final JsonNode node, final String field) {
    final JsonNode value = node.path(field);
    return value.isNumber() ? value.decimalValue() : null;
  }

  private static Instant instant(final JsonNode node, final String field) {
    final String value = text(node, field);
    if (value == null) {
      return null;
    }
    try {
      return Instant.parse(value);
    } catch (final DateTimeParseException ex) {
      return null;
    }
  }

  private static void deleteQuietly(final Path path) {
    try {
      Files.deleteIfExists(path);
    } catch (final IOException ex) {
      log.warn("cannot delete {}", path, ex);
    }
  }
}
