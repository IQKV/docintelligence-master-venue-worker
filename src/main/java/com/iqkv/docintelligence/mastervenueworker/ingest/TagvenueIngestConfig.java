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

import com.iqkv.docintelligence.mastervenueworker.infrastructure.config.IngestConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/**
 * Wires the {@code tagvenue} {@link VenueDataProvider} in only while its source is enabled.
 *
 * <p>{@code iqkv.ingest.source.tagvenue.enabled=false} removes the provider bean, so the slice
 * stops reading that source's record folders while the other providers keep running.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "iqkv.ingest.source.tagvenue.enabled", havingValue = "true")
class TagvenueIngestConfig {

  @Bean
  VenueDataProvider tagvenueVenueDataProvider(final IngestConfigurationProperties properties,
                                              final JsonMapper jsonMapper) {
    return new TagvenueVenueDataProvider(properties, jsonMapper);
  }
}
