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
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.LongSupplier;

/**
 * Decorator that caches embedding results of any {@link EmbeddingProvider}.
 *
 * <p>Every remote embedding call costs a network round-trip (~5-15ms for a local
 * Ollama server). When the same text is embedded repeatedly — e.g. during
 * iterative recall refinement or batch re-ingestion — this cache serves the
 * vector from memory instead.</p>
 *
 * <h3>Design</h3>
 * <ul>
 *   <li><b>Key</b> — SHA-256 hash of the input text (keeps memory low for long texts)</li>
 *   <li><b>Eviction</b> — LRU via access-ordered {@link LinkedHashMap}, bounded by
 *       {@link EmbeddingCacheConfig#maxSize()}</li>
 *   <li><b>TTL</b> — optional time-based expiry ({@link EmbeddingCacheConfig#ttl()})</li>
 *   <li><b>Thread safety</b> — all map access is synchronized on the map itself;
 *       the lock is never held during a delegate call, so concurrent misses on the
 *       same key may each hit the delegate once (benign race)</li>
 *   <li><b>Statistics</b> — hits/misses/evictions counted and logged at INFO every
 *       {@link EmbeddingCacheConfig#statsLogInterval()}</li>
 * </ul>
 *
 * <p>Cached vectors are defensively copied on store and on every hit, so callers
 * can never mutate cached state.</p>
 *
 * <p>Zero external dependencies — uses only the JDK, per this module's contract.</p>
 */
public final class CachingEmbeddingProvider implements EmbeddingProvider {

    private static final Logger log = LoggerFactory.getLogger(CachingEmbeddingProvider.class);

    private final EmbeddingProvider delegate;
    private final EmbeddingCacheConfig config;
    private final LongSupplier nanoClock;
    private final LinkedHashMap<String, CacheEntry> cache;
    private final LongAdder hits = new LongAdder();
    private final LongAdder misses = new LongAdder();
    private final LongAdder evictions = new LongAdder();
    private final AtomicLong lastStatsLogNanos;

    private record CacheEntry(EmbeddingResult result, long expiresAtNanos) {}

    /**
     * Cache statistics snapshot.
     *
     * @param hits      number of requests served from the cache
     * @param misses    number of requests delegated to the wrapped provider
     * @param evictions number of entries evicted by the LRU policy
     * @param size      current number of cached entries
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

    public CachingEmbeddingProvider(EmbeddingProvider delegate, EmbeddingCacheConfig config) {
        this(delegate, config, System::nanoTime);
    }

    /**
     * Constructor with an injectable clock for deterministic TTL tests.
     */
    CachingEmbeddingProvider(EmbeddingProvider delegate, EmbeddingCacheConfig config, LongSupplier nanoClock) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.nanoClock = Objects.requireNonNull(nanoClock, "nanoClock must not be null");
        this.cache = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
                boolean evict = size() > config.maxSize();
                if (evict) {
                    evictions.increment();
                }
                return evict;
            }
        };
        this.lastStatsLogNanos = new AtomicLong(nanoClock.getAsLong());
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

    @Override
    public EmbeddingResult embed(String text) {
        if (text == null) {
            return delegate.embed(null); // let the delegate apply its own validation
        }
        String key = cacheKey(text);
        EmbeddingResult cached = lookup(key);
        if (cached != null) {
            hits.increment();
            maybeLogStats();
            return cached;
        }
        EmbeddingResult result = delegate.embed(text);
        store(key, result);
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
        // key → positions in the batch still needing an embedding (handles duplicates)
        Map<String, List<Integer>> pending = new LinkedHashMap<>();
        List<String> pendingTexts = new ArrayList<>();

        for (int i = 0; i < texts.size(); i++) {
            String text = texts.get(i);
            if (text == null) {
                return delegate.embedBatch(texts); // let the delegate apply its own validation
            }
            String key = cacheKey(text);
            EmbeddingResult cached = lookup(key);
            if (cached != null) {
                results[i] = cached;
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
                store(entry.getKey(), result);
                for (int position : entry.getValue()) {
                    results[position] = result;
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
        synchronized (cache) {
            cache.clear();
        }
        delegate.close();
    }

    /** Returns the wrapped provider. */
    public EmbeddingProvider delegate() {
        return delegate;
    }

    /** Returns a snapshot of the cache statistics. */
    public CacheStats stats() {
        int size;
        synchronized (cache) {
            size = cache.size();
        }
        return new CacheStats(hits.sum(), misses.sum(), evictions.sum(), size);
    }

    private EmbeddingResult lookup(String key) {
        long now = nanoClock.getAsLong();
        synchronized (cache) {
            CacheEntry entry = cache.get(key);
            if (entry == null) {
                return null;
            }
            if (config.ttlEnabled() && now - entry.expiresAtNanos() >= 0) {
                cache.remove(key);
                return null;
            }
            EmbeddingResult result = entry.result();
            return new EmbeddingResult(result.vector().clone(), result.tokenCount(), result.model());
        }
    }

    private void store(String key, EmbeddingResult result) {
        long expiresAt = config.ttlEnabled()
                ? nanoClock.getAsLong() + config.ttl().toNanos()
                : Long.MAX_VALUE;
        var copy = new EmbeddingResult(result.vector().clone(), result.tokenCount(), result.model());
        synchronized (cache) {
            cache.put(key, new CacheEntry(copy, expiresAt));
        }
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
            log.info("[EmbeddingCache] model={}, size={}/{}, hits={}, misses={}, hitRatio={}%, evictions={}",
                    delegate.modelName(), stats.size(), config.maxSize(), stats.hits(), stats.misses(),
                    String.format("%.1f", stats.hitRatio() * 100.0), stats.evictions());
        }
    }

    private static String cacheKey(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the JDK spec; this cannot happen on a compliant JVM
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
