/*
 * Copyright 2026 Spectrayan
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
package com.spectrayan.spector.synapse.config.service;

import com.spectrayan.spector.synapse.config.model.ConfigCategory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Loads and applies saved configuration overrides from database on startup.
 */
@Component
public class ConfigBootstrapper implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ConfigBootstrapper.class);

    private final ConfigResolutionService resolutionService;
    private final ConfigApplicator applicator;

    public ConfigBootstrapper(ConfigResolutionService resolutionService,
                              ConfigApplicator applicator) {
        this.resolutionService = resolutionService;
        this.applicator = applicator;
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("Loading dynamic configurations from database...");
        for (ConfigCategory category : ConfigCategory.values()) {
            try {
                Map<String, Object> effective = resolutionService.resolve("default", "default", category);
                applicator.apply("default", "default", category, effective);
                log.info("Successfully applied configuration for category: {}", category.key());
            } catch (Exception e) {
                log.error("Failed to apply configuration for category: {}", category.key(), e);
            }
        }
    }
}
