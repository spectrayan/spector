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

import com.spectrayan.spector.embed.EmbeddingProvider;
import com.spectrayan.spector.embed.TextGenerationProvider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Default thread-safe implementation of {@link ProviderRegistry}.
 *
 * <p>Uses a read-write lock for the active provider references to allow
 * concurrent reads (embed/generate calls) with exclusive writes (activate).
 * Provider registrations use {@link ConcurrentHashMap} for lock-free reads.</p>
 *
 * <h3>Auto-activation</h3>
 * <p>The first provider registered for each type (embedding, generation) is
 * automatically activated, providing sensible defaults without explicit
 * activation calls.</p>
 *
 * <h3>Thread Safety</h3>
 * <p>All methods are safe for concurrent access. Active provider switching
 * is atomic with respect to ongoing operations.</p>
 */
public class DefaultProviderRegistry implements ProviderRegistry {

    private static final Logger log = LoggerFactory.getLogger(DefaultProviderRegistry.class);

    private final Map<String, EmbeddingProvider> embeddingProviders = new ConcurrentHashMap<>();
    private final Map<String, TextGenerationProvider> generationProviders = new ConcurrentHashMap<>();

    private final ReentrantReadWriteLock embeddingLock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock generationLock = new ReentrantReadWriteLock();

    private volatile String activeEmbeddingName;
    private volatile String activeGenerationName;

    // ─────────────── Embedding ───────────────

    @Override
    public void registerEmbedding(String name, EmbeddingProvider provider) {
        embeddingProviders.put(name, provider);
        log.info("Registered embedding provider: {} (model={})", name, provider.modelName());

        // Auto-activate first registered provider
        if (activeEmbeddingName == null) {
            activateEmbedding(name);
        }
    }

    @Override
    public Optional<EmbeddingProvider> activeEmbedding() {
        embeddingLock.readLock().lock();
        try {
            String name = activeEmbeddingName;
            return name != null ? Optional.ofNullable(embeddingProviders.get(name)) : Optional.empty();
        } finally {
            embeddingLock.readLock().unlock();
        }
    }

    @Override
    public void activateEmbedding(String name) {
        if (!embeddingProviders.containsKey(name)) {
            throw new IllegalArgumentException("No embedding provider registered with name: " + name
                    + ". Available: " + embeddingProviders.keySet());
        }
        embeddingLock.writeLock().lock();
        try {
            String previous = activeEmbeddingName;
            activeEmbeddingName = name;
            log.info("Active embedding provider switched: {} → {}", previous, name);
        } finally {
            embeddingLock.writeLock().unlock();
        }
    }

    @Override
    public Set<String> embeddingNames() {
        return Set.copyOf(embeddingProviders.keySet());
    }

    // ─────────────── Generation ───────────────

    @Override
    public void registerGeneration(String name, TextGenerationProvider provider) {
        generationProviders.put(name, provider);
        log.info("Registered generation provider: {} (model={})", name, provider.modelName());

        if (activeGenerationName == null) {
            activateGeneration(name);
        }
    }

    @Override
    public Optional<TextGenerationProvider> activeGeneration() {
        generationLock.readLock().lock();
        try {
            String name = activeGenerationName;
            return name != null ? Optional.ofNullable(generationProviders.get(name)) : Optional.empty();
        } finally {
            generationLock.readLock().unlock();
        }
    }

    @Override
    public void activateGeneration(String name) {
        if (!generationProviders.containsKey(name)) {
            throw new IllegalArgumentException("No generation provider registered with name: " + name
                    + ". Available: " + generationProviders.keySet());
        }
        generationLock.writeLock().lock();
        try {
            String previous = activeGenerationName;
            activeGenerationName = name;
            log.info("Active generation provider switched: {} → {}", previous, name);
        } finally {
            generationLock.writeLock().unlock();
        }
    }

    @Override
    public Set<String> generationNames() {
        return Set.copyOf(generationProviders.keySet());
    }

    // ─────────────── Health ───────────────

    @Override
    public ProviderHealth health(String name) {
        // Check embedding first, then generation
        EmbeddingProvider embeddingProvider = embeddingProviders.get(name);
        if (embeddingProvider != null) {
            return checkEmbeddingProviderHealth(name, embeddingProvider);
        }

        TextGenerationProvider generationProvider = generationProviders.get(name);
        if (generationProvider != null) {
            return checkGenerationProviderHealth(name, generationProvider);
        }

        return ProviderHealth.unknown(name);
    }

    // ─────────────── AutoCloseable ───────────────

    @Override
    public void close() {
        log.info("Closing DefaultProviderRegistry: {} embedding, {} generation providers",
                embeddingProviders.size(), generationProviders.size());
        embeddingProviders.clear();
        generationProviders.clear();
        activeEmbeddingName = null;
        activeGenerationName = null;
    }

    // ─────────────── Private helpers ───────────────

    private ProviderHealth checkEmbeddingProviderHealth(String name, EmbeddingProvider provider) {
        Instant start = Instant.now();
        try {
            provider.embed("health check");
            Duration latency = Duration.between(start, Instant.now());
            return ProviderHealth.healthy(name, latency);
        } catch (Exception e) {
            Duration latency = Duration.between(start, Instant.now());
            return ProviderHealth.unhealthy(name, latency, e.getMessage());
        }
    }

    private ProviderHealth checkGenerationProviderHealth(String name, TextGenerationProvider provider) {
        Instant start = Instant.now();
        try {
            if (provider.isAvailable()) {
                Duration latency = Duration.between(start, Instant.now());
                return ProviderHealth.healthy(name, latency);
            } else {
                Duration latency = Duration.between(start, Instant.now());
                return ProviderHealth.unhealthy(name, latency, "Provider reports unavailable");
            }
        } catch (Exception e) {
            Duration latency = Duration.between(start, Instant.now());
            return ProviderHealth.unhealthy(name, latency, e.getMessage());
        }
    }
}
