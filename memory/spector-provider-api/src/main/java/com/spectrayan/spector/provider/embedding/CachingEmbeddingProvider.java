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
package com.spectrayan.spector.provider.embedding;

import com.spectrayan.spector.commons.cache.SpectorCache;
import com.spectrayan.spector.commons.cache.SpectorCacheManager;
import com.spectrayan.spector.commons.cache.TtlConcurrentMapCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.LongSupplier;

/**
 * Decorator that caches embedding results of any {@link EmbeddingProvider} using
 * Spector's central {@link SpectorCache} SPI.
 *
 * <p>Every remote embedding call costs a network round-trip (~5-15ms for a local
 * Ollama server). When the same text is embedded repeatedly — e.g. during
 * iterative recall refinement or batch re-ingestion — this cache serves the
 * vector from the configured {@link SpectorCache} instead.</p>
 *
 * <h3>Design</h3>
 * <ul>
 *   <li><b>Key</b> — SHA-256 hash of the input text (keeps memory low and safe across all cache providers)</li>
 *   <li><b>Cache Backend</b> — delegates to {@link SpectorCache}, seamlessly supporting standalone in-memory,
 *       Caffeine, and distributed Redis caches</li>
 *   <li><b>Statistics</b> — hits/misses counted and logged at INFO every
 *       {@link EmbeddingCacheConfig#statsLogInterval()}</li>
 * </ul>
 *
 * <p>Cached vectors are defensively copied on store and on every hit, so callers
 * can never mutate cached state.</p>
 */
public final class CachingEmbeddingProvider implements EmbeddingProvider {

    private static final Logger log = LoggerFactory.getLogger(CachingEmbeddingProvider.class);

    public static final String DEFAULT_CACHE_NAME = "spector-embeddings";

    private final EmbeddingProvider delegate;
    private final SpectorCache cache;
    private final EmbeddingCacheConfig config;
    private final LongSupplier nanoClock;
    private final LongAdder hits = new LongAdder();
    private final LongAdder misses = new LongAdder();
    private final LongAdder evictions = new LongAdder();
    private final AtomicLong lastStatsLogNanos;

    /**
     * Cache statistics snapshot.
     *
     * @param hits      number of requests served from the cache
     * @param misses    number of requests delegated to the wrapped provider
     * @param evictions number of entries evicted by policy (when supported)
     * @param size      current number of cached entries (when supported)
     */
    public record CacheStats(long hits, long misses, long evictions, int size) {

        /** Total number of cache lookups. */
        public long requests() {
            return hits + misses;
        }

        /** Fraction of lookups served from the cache (0.0 when no requests yet). */
        public double hitRatio() {
            long total = requests();
            return total == 0 ? 0.0 : (double) hits / total;
        }
    }

    /**
     * Constructs a caching decorator with a specific {@link SpectorCache} and default cache configuration.
     *
     * @param delegate the underlying provider
     * @param cache    the cache SPI instance
     */
    public CachingEmbeddingProvider(EmbeddingProvider delegate, SpectorCache cache) {
        this(delegate, cache, EmbeddingCacheConfig.DEFAULT, System::nanoTime);
    }

    /**
     * Constructs a caching decorator with a specific {@link SpectorCache} and custom cache configuration.
     *
     * @param delegate the underlying provider
     * @param cache    the cache SPI instance
     * @param config   cache configuration
     */
    public CachingEmbeddingProvider(EmbeddingProvider delegate, SpectorCache cache, EmbeddingCacheConfig config) {
        this(delegate, cache, config, System::nanoTime);
    }

    /**
     * Constructs a caching decorator using a standalone JDK {@link SpectorCache} built from {@link EmbeddingCacheConfig}.
     *
     * @param delegate the underlying provider
     * @param config   cache configuration
     */
    public CachingEmbeddingProvider(EmbeddingProvider delegate, EmbeddingCacheConfig config) {
        this(
                Objects.requireNonNull(delegate, "delegate must not be null"),
                createDefaultCache(Objects.requireNonNull(config, "config must not be null")),
                config,
                System::nanoTime
        );
    }

    CachingEmbeddingProvider(EmbeddingProvider delegate, EmbeddingCacheConfig config, LongSupplier nanoClock) {
        this(
                Objects.requireNonNull(delegate, "delegate must not be null"),
                createDefaultCache(Objects.requireNonNull(config, "config must not be null")),
                config,
                nanoClock
        );
    }

    CachingEmbeddingProvider(EmbeddingProvider delegate, SpectorCache cache, EmbeddingCacheConfig config, LongSupplier nanoClock) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.cache = Objects.requireNonNull(cache, "cache must not be null");
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.nanoClock = Objects.requireNonNull(nanoClock, "nanoClock must not be null");
        this.lastStatsLogNanos = new AtomicLong(nanoClock.getAsLong());
    }

    private static SpectorCache createDefaultCache(EmbeddingCacheConfig config) {
        return SpectorCacheManager.builder()
                .defaultTtl(config.ttl())
                .defaultMaxSize(config.maxSize())
                .build()
                .getCache(DEFAULT_CACHE_NAME);
    }

    /**
     * Wraps a provider with caching if the config enables it.
     *
     * <p>Returns the provider unchanged when caching is disabled or the provider
     * is already a {@code CachingEmbeddingProvider} (no double-wrapping).</p>
     *
     * @param provider the provider to wrap
     * @param config   cache configuration
     * @return the caching decorator, or {@code provider} itself when caching is off
     */
    public static EmbeddingProvider wrap(EmbeddingProvider provider, EmbeddingCacheConfig config) {
        Objects.requireNonNull(provider, "provider must not be null");
        Objects.requireNonNull(config, "config must not be null");
        if (!config.enabled() || provider instanceof CachingEmbeddingProvider) {
            return provider;
        }
        return new CachingEmbeddingProvider(provider, config);
    }

    /**
     * Wraps a provider with caching using an explicit {@link SpectorCache}.
     *
     * @param provider the provider to wrap
     * @param cache    the cache SPI instance
     * @return the caching decorator, or {@code provider} itself if already wrapped
     */
    public static EmbeddingProvider wrap(EmbeddingProvider provider, SpectorCache cache) {
        return wrap(provider, cache, EmbeddingCacheConfig.DEFAULT);
    }

    /**
     * Wraps a provider with caching using an explicit {@link SpectorCache} and configuration.
     *
     * @param provider the provider to wrap
     * @param cache    the cache SPI instance
     * @param config   cache configuration
     * @return the caching decorator, or {@code provider} itself when caching is off
     */
    public static EmbeddingProvider wrap(EmbeddingProvider provider, SpectorCache cache, EmbeddingCacheConfig config) {
        Objects.requireNonNull(provider, "provider must not be null");
        Objects.requireNonNull(cache, "cache must not be null");
        Objects.requireNonNull(config, "config must not be null");
        if (!config.enabled() || provider instanceof CachingEmbeddingProvider) {
            return provider;
        }
        return new CachingEmbeddingProvider(provider, cache, config);
    }

    @Override
    public EmbeddingResult embed(String text) {
        if (text == null) {
            return delegate.embed(null); // let the delegate apply its own validation
        }
        String key = cacheKey(text);
        Optional<EmbeddingResult> cached = cache.get(key, EmbeddingResult.class);
        if (cached.isPresent()) {
            hits.increment();
            maybeLogStats();
            EmbeddingResult res = cached.get();
            return new EmbeddingResult(res.vector().clone(), res.tokenCount(), res.model());
        }
        EmbeddingResult result = delegate.embed(text);
        cache.put(key, new EmbeddingResult(result.vector().clone(), result.tokenCount(), result.model()));
        misses.increment();
        maybeLogStats();
        return result;
    }

    @Override
    public List<EmbeddingResult> embedBatch(List<String> texts) {
        Objects.requireNonNull(texts, "texts must not be null");
        if (texts.isEmpty()) {
            return List.of();
        }

        EmbeddingResult[] results = new EmbeddingResult[texts.size()];
        Map<String, List<Integer>> pending = new LinkedHashMap<>();
        List<String> pendingTexts = new ArrayList<>();

        for (int i = 0; i < texts.size(); i++) {
            String text = texts.get(i);
            if (text == null) {
                return delegate.embedBatch(texts); // let the delegate apply its own validation
            }
            String key = cacheKey(text);
            Optional<EmbeddingResult> cached = cache.get(key, EmbeddingResult.class);
            if (cached.isPresent()) {
                EmbeddingResult res = cached.get();
                results[i] = new EmbeddingResult(res.vector().clone(), res.tokenCount(), res.model());
                hits.increment();
            } else {
                List<Integer> positions = pending.computeIfAbsent(key, k -> {
                    pendingTexts.add(text);
                    return new ArrayList<>();
                });
                positions.add(i);
                misses.increment();
            }
        }

        if (!pendingTexts.isEmpty()) {
            List<EmbeddingResult> fresh = delegate.embedBatch(pendingTexts);
            if (fresh.size() != pendingTexts.size()) {
                throw new IllegalStateException("Delegate returned " + fresh.size()
                        + " embeddings for " + pendingTexts.size() + " texts");
            }
            int freshIdx = 0;
            for (Map.Entry<String, List<Integer>> entry : pending.entrySet()) {
                EmbeddingResult result = fresh.get(freshIdx++);
                cache.put(entry.getKey(), new EmbeddingResult(result.vector().clone(), result.tokenCount(), result.model()));
                for (int position : entry.getValue()) {
                    results[position] = new EmbeddingResult(
                            result.vector().clone(), result.tokenCount(), result.model());
                }
            }
        }

        maybeLogStats();
        return List.of(results);
    }

    @Override
    public int dimensions() {
        return delegate.dimensions();
    }

    @Override
    public String modelName() {
        return delegate.modelName();
    }

    @Override
    public int maxTokens() {
        return delegate.maxTokens();
    }

    @Override
    public void close() {
        cache.clear();
        delegate.close();
    }

    /** Returns the wrapped provider. */
    public EmbeddingProvider delegate() {
        return delegate;
    }

    /** Returns the underlying cache instance. */
    public SpectorCache cache() {
        return cache;
    }

    /** Returns a snapshot of the cache statistics. */
    public CacheStats stats() {
        int size = 0;
        if (cache instanceof TtlConcurrentMapCache ttlCache) {
            size = ttlCache.size();
        }
        return new CacheStats(hits.sum(), misses.sum(), evictions.sum(), size);
    }

    private void maybeLogStats() {
        long intervalNanos = config.statsLogInterval().toNanos();
        if (intervalNanos <= 0) {
            return;
        }
        long now = nanoClock.getAsLong();
        long last = lastStatsLogNanos.get();
        if (now - last >= intervalNanos && lastStatsLogNanos.compareAndSet(last, now)) {
            CacheStats stats = stats();
            log.info("[EmbeddingCache] model={}, cache={}, hits={}, misses={}, hitRatio={}%",
                    delegate.modelName(), cache.getName(), stats.hits(), stats.misses(),
                    String.format("%.1f", stats.hitRatio() * 100.0));
        }
    }

    private static String cacheKey(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
