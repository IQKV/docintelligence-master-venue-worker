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

import java.util.Locale;

/**
 * The content type of a scrape asset file, derived from its file name.
 *
 * <p>Both the object store that uploads the bytes and the {@code master_venue_asset} row that
 * records them need the same content type, and the scraper only ever writes JPEG, PNG and WebP
 * photos, so the mapping is derived from the extension rather than probed from the filesystem —
 * {@code Files.probeContentType} depends on the platform's content-type map and is unreliable
 * for WebP in a container.
 */
final class AssetContentTypes {

  private AssetContentTypes() {
  }

  /**
   * The MIME type of the named asset file.
   *
   * @param fileName the asset file name, e.g. {@code 11896-the-last-talisman-venue.jpg}
   * @return the MIME type, or {@code application/octet-stream} for an unrecognised extension
   */
  static String of(final String fileName) {
    final int dot = fileName.lastIndexOf('.');
    final String extension = dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    return switch (extension) {
      case "jpg", "jpeg" -> "image/jpeg";
      case "png" -> "image/png";
      case "webp" -> "image/webp";
      default -> "application/octet-stream";
    };
  }
}
