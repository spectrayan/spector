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
package com.spectrayan.spector.provider;

import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.generation.LlmProvider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;

/**
 * Discovers {@link ProviderFactory} implementations via {@link ServiceLoader}
 * and creates providers from a list of {@link ProviderConfig} entries.
 *
 * <p>This class bridges the SPI discovery mechanism with the runtime configuration.
 * Given a list of provider configurations, it matches each to a discovered factory
 * by name, creates the appropriate providers, and registers them in a
 * {@link ProviderRegistry}.</p>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 *   List<ProviderConfig> configs = loadFromYaml();
 *   ProviderRegistry registry = ProviderDiscovery.discover(configs);
 *   EmbeddingProvider active = registry.activeEmbedding().orElseThrow();
 * }</pre>
 *
 * <h3>Thread Safety</h3>
 * <p>This class is stateless and thread-safe. The returned registry is also thread-safe.</p>
 */
public final class ProviderDiscovery {

    private static final Logger log = LoggerFactory.getLogger(ProviderDiscovery.class);

    private ProviderDiscovery() {
        // Utility class
    }

    /**
     * Discovers provider factories via ServiceLoader and creates providers
     * from the given configurations.
     *
     * <p>For each config, the factory whose {@link ProviderFactory#name()} matches
     * the config's {@link ProviderConfig#type()} is used to create providers.
     * Created providers are registered in a new {@link DefaultProviderRegistry}.</p>
     *
     * @param configs list of provider configurations
     * @return a populated registry with all successfully created providers
     * @throws IllegalArgumentException if a config references an unknown factory type
     */
    public static ProviderRegistry discover(List<ProviderConfig> configs) {
        Objects.requireNonNull(configs, "configs must not be null");

        // Step 1: Discover all factories via ServiceLoader
        Map<String, ProviderFactory> factories = discoverFactories();

        log.info("Discovered {} provider factories: {}", factories.size(), factories.keySet());

        // Step 2: Create providers from configs and register them
        DefaultProviderRegistry registry = new DefaultProviderRegistry();

        for (ProviderConfig config : configs) {
            ProviderFactory factory = factories.get(config.type());
            if (factory == null) {
                log.warn("No factory found for provider type '{}' (config: {}). Available: {}",
                        config.type(), config.name(), factories.keySet());
                continue;
            }

            createAndRegister(factory, config, registry);
        }

        log.info("Provider discovery complete: {} embedding, {} generation providers registered",
                registry.embeddingNames().size(), registry.generationNames().size());

        return registry;
    }

    /**
     * Discovers all {@link ProviderFactory} implementations on the classpath.
     *
     * @return map of factory name → factory instance
     */
    public static Map<String, ProviderFactory> discoverFactories() {
        Map<String, ProviderFactory> factories = new HashMap<>();

        ServiceLoader<ProviderFactory> loader = ServiceLoader.load(ProviderFactory.class);
        for (ProviderFactory factory : loader) {
            String name = factory.name();
            if (factories.containsKey(name)) {
                log.warn("Duplicate provider factory name '{}': {} vs {}. Using first.",
                        name, factories.get(name).getClass().getName(),
                        factory.getClass().getName());
            } else {
                factories.put(name, factory);
                log.debug("Discovered provider factory: {} ({}) — embedding={}, generation={}",
                        factory.displayName(), name,
                        factory.supportsEmbedding(), factory.supportsGeneration());
            }
        }

        return Map.copyOf(factories);
    }

    /**
     * Creates embedding and/or generation providers from a factory+config pair
     * and registers them in the given registry.
     */
    private static void createAndRegister(ProviderFactory factory,
                                          ProviderConfig config,
                                          DefaultProviderRegistry registry) {
        String instanceName = config.name();

        // Create embedding provider if supported
        if (factory.supportsEmbedding()) {
            try {
                factory.createEmbeddingProvider(config).ifPresent(provider -> {
                    registry.registerEmbedding(instanceName, provider);
                    log.info("Created embedding provider '{}' via {} (model={})",
                            instanceName, factory.displayName(), provider.modelName());
                });
            } catch (Exception e) {
                log.error("Failed to create embedding provider '{}' via {}: {}",
                        instanceName, factory.displayName(), e.getMessage(), e);
            }
        }

        // Create generation provider if supported
        if (factory.supportsGeneration()) {
            try {
                factory.createGenerationProvider(config).ifPresent(provider -> {
                    registry.registerGeneration(instanceName, provider);
                    log.info("Created generation provider '{}' via {} (model={})",
                            instanceName, factory.displayName(), provider.modelName());
                });
            } catch (Exception e) {
                log.error("Failed to create generation provider '{}' via {}: {}",
                        instanceName, factory.displayName(), e.getMessage(), e);
            }
        }
    }
}
