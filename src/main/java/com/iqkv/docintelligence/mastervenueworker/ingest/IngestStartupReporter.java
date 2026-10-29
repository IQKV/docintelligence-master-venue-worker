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

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import com.iqkv.docintelligence.mastervenueworker.infrastructure.config.IngestConfigurationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Reports the ingest setup once the application is ready — when it came up, the directories it
 * reads, the providers that are switched on and how often it polls.
 *
 * <p>The line is written at startup so an operator can confirm from the console that the worker
 * sees the volume the scraper writes to and enabled the expected sources, without waiting for the
 * first ingest cycle.
 */
@Component
public class IngestStartupReporter {

  private static final Logger log = LoggerFactory.getLogger(IngestStartupReporter.class);

  private final IngestConfigurationProperties properties;

  private final ObjectProvider<VenueDataProvider> providers;

  public IngestStartupReporter(final IngestConfigurationProperties properties,
                               final ObjectProvider<VenueDataProvider> providers) {
    this.properties = properties;
    this.providers = providers;
  }

  @EventListener(ApplicationReadyEvent.class)
  void report() {
    final Path ingestDir = Path.of(this.properties.dir()).toAbsolutePath().normalize();
    final List<String> keys = this.providers.orderedStream().map(VenueDataProvider::key).toList();
    log.info("ingest setup: startedAt={} workingDir={} ingestDir={} exists={} providers={} pollInterval={}",
        Instant.now(), System.getProperty("user.dir"), ingestDir, Files.isDirectory(ingestDir), keys,
        this.properties.pollInterval());
  }
}
