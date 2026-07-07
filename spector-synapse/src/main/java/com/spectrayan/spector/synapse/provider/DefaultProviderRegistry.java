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
package com.spectrayan.spector.synapse.provider;

import com.spectrayan.spector.embed.EmbeddingProvider;
import com.spectrayan.spector.embed.TextGenerationProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Default implementation of {@link ProviderRegistry}.
 *
 * <p>Thread-safe: uses {@link ConcurrentHashMap} for registries and
 * {@link AtomicReference} for active provider switching.</p>
 */
@Component
public class DefaultProviderRegistry implements ProviderRegistry {

    private static final Logger log = LoggerFactory.getLogger(DefaultProviderRegistry.class);

    private final ConcurrentHashMap<String, EmbeddingProvider> embeddingProviders = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, TextGenerationProvider> generationProviders = new ConcurrentHashMap<>();
    private final AtomicReference<String> activeEmbeddingRef = new AtomicReference<>();
    private final AtomicReference<String> activeGenerationRef = new AtomicReference<>();

    // ─────────────── Embedding Providers ───────────────

    @Override
    public void registerEmbedding(String name, EmbeddingProvider provider) {
        embeddingProviders.put(name, provider);
        log.info("[ProviderRegistry] Registered embedding provider: {}", name);

        // Auto-activate first registered provider
        activeEmbeddingRef.compareAndSet(null, name);
    }

    @Override
    public Optional<EmbeddingProvider> activeEmbedding() {
        String name = activeEmbeddingRef.get();
        return name != null ? Optional.ofNullable(embeddingProviders.get(name)) : Optional.empty();
    }

    @Override
    public void activateEmbedding(String name) {
        if (!embeddingProviders.containsKey(name)) {
            throw new IllegalArgumentException("Unknown embedding provider: " + name);
        }
        String prev = activeEmbeddingRef.getAndSet(name);
        log.info("[ProviderRegistry] Switched embedding provider: {} → {}", prev, name);
    }

    @Override
    public Set<String> embeddingProviderNames() {
        return Set.copyOf(embeddingProviders.keySet());
    }

    @Override
    public Optional<String> activeEmbeddingName() {
        return Optional.ofNullable(activeEmbeddingRef.get());
    }

    // ─────────────── Generation Providers ───────────────

    @Override
    public void registerGeneration(String name, TextGenerationProvider provider) {
        generationProviders.put(name, provider);
        log.info("[ProviderRegistry] Registered generation provider: {}", name);

        activeGenerationRef.compareAndSet(null, name);
    }

    @Override
    public Optional<TextGenerationProvider> activeGeneration() {
        String name = activeGenerationRef.get();
        return name != null ? Optional.ofNullable(generationProviders.get(name)) : Optional.empty();
    }

    @Override
    public void activateGeneration(String name) {
        if (!generationProviders.containsKey(name)) {
            throw new IllegalArgumentException("Unknown generation provider: " + name);
        }
        String prev = activeGenerationRef.getAndSet(name);
        log.info("[ProviderRegistry] Switched generation provider: {} → {}", prev, name);
    }

    @Override
    public Set<String> generationProviderNames() {
        return Set.copyOf(generationProviders.keySet());
    }

    @Override
    public Optional<String> activeGenerationName() {
        return Optional.ofNullable(activeGenerationRef.get());
    }

    // ─────────────── Health ───────────────

    @Override
    public ProviderHealth checkEmbeddingHealth(String name) {
        EmbeddingProvider provider = embeddingProviders.get(name);
        if (provider == null) {
            return ProviderHealth.unknown(name);
        }
        Instant start = Instant.now();
        try {
            // Simple health check — try to get embedding dimensions
            provider.dimensions();
            Duration latency = Duration.between(start, Instant.now());
            return ProviderHealth.healthy(name, latency);
        } catch (Exception e) {
            Duration latency = Duration.between(start, Instant.now());
            return ProviderHealth.unhealthy(name, latency, e.getMessage());
        }
    }

    @Override
    public ProviderHealth checkGenerationHealth(String name) {
        TextGenerationProvider provider = generationProviders.get(name);
        if (provider == null) {
            return ProviderHealth.unknown(name);
        }
        Instant start = Instant.now();
        try {
            // Simple health check — verify provider is accessible
            provider.modelName();
            Duration latency = Duration.between(start, Instant.now());
            return ProviderHealth.healthy(name, latency);
        } catch (Exception e) {
            Duration latency = Duration.between(start, Instant.now());
            return ProviderHealth.unhealthy(name, latency, e.getMessage());
        }
    }

    @Override
    public Map<String, ProviderHealth> allHealth() {
        Map<String, ProviderHealth> results = new HashMap<>();
        for (String name : embeddingProviders.keySet()) {
            results.put("embedding:" + name, checkEmbeddingHealth(name));
        }
        for (String name : generationProviders.keySet()) {
            results.put("generation:" + name, checkGenerationHealth(name));
        }
        return results;
    }
}
