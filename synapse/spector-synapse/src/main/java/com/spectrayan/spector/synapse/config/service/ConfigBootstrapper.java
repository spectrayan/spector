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
import com.spectrayan.spector.synapse.memory.MemoryRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Loads and applies saved configuration overrides from database on startup.
 * Registers a {@code NamespaceOpenListener} on {@link MemoryRegistry} to apply
 * dynamic scoped configuration overlays whenever a namespace is opened (ADR-0085).
 */
@Component
public class ConfigBootstrapper implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ConfigBootstrapper.class);

    private final ConfigResolutionService resolutionService;
    private final ConfigApplicator applicator;
    private final ObjectProvider<MemoryRegistry> memoryRegistryProvider;

    public ConfigBootstrapper(ConfigResolutionService resolutionService,
                              ConfigApplicator applicator,
                              ObjectProvider<MemoryRegistry> memoryRegistryProvider) {
        this.resolutionService = resolutionService;
        this.applicator = applicator;
        this.memoryRegistryProvider = memoryRegistryProvider;
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("Bootstrapping configurations: applying defaults/overrides to shared/default namespace...");
        for (ConfigCategory category : ConfigCategory.values()) {
            try {
                Map<String, Object> effective = resolutionService.resolve("default", "default", category);
                applicator.apply("default", "default", category, effective);
                log.debug("Successfully bootstrapped configuration for category: {}", category.key());
            } catch (Exception e) {
                log.error("Failed to bootstrap configuration for category: {}", category.key(), e);
            }
        }

        MemoryRegistry memoryRegistry = memoryRegistryProvider.getIfAvailable();
        if (memoryRegistry != null && memoryRegistry.namespaceResolver() != null) {
            memoryRegistry.namespaceResolver().addOpenListener((tenantId, namespaceId, memory) -> {
                log.debug("[ConfigBootstrapper] Overlaying configurations for opened namespace ns={}, tenant={}",
                        namespaceId, tenantId);
                for (ConfigCategory category : ConfigCategory.values()) {
                    try {
                        Map<String, Object> effective = resolutionService.resolve(tenantId, namespaceId, category);
                        applicator.applyToMemory(memory, category, effective);
                    } catch (Exception e) {
                        log.warn("[ConfigBootstrapper] Failed to apply config for category {} on opened namespace {}: {}",
                                category.key(), namespaceId, e.getMessage());
                    }
                }
            });
            log.info("[ConfigBootstrapper] Registered NamespaceOpenListener with MemoryRegistry");
        }
    }
}
