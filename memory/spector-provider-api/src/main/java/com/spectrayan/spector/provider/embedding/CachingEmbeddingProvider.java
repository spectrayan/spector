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

import com.spectrayan.spector.commons.cache.NoOpSpectorCache;
import com.spectrayan.spector.commons.cache.SpectorCache;
import com.spectrayan.spector.commons.cache.SpectorCacheManager;
import com.spectrayan.spector.provider.ProviderFingerprint;

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
 *   <li><b>Key</b> — SHA-256 hash of the input text, prefixed with the {@linkplain ProviderFingerprint
 *       fingerprint} of the provider that produced the vector (keeps memory low and safe across all
 *       cache providers)</li>
 *   <li><b>Cache Backend</b> — delegates to {@link SpectorCache}, seamlessly supporting standalone in-memory,
 *       Caffeine, and distributed Redis caches</li>
 * </ul>
 *
 * <h3>Why the key is model-scoped</h3>
 * <p>One cache is shared process-wide ({@value #DEFAULT_CACHE_NAME}). Hashing the text alone was safe
 * only for as long as exactly one embedding provider existed per process. Once two namespaces can be
 * configured with different models, a text-only key makes the first model's vector answer the second
 * model's request — a silent, undetectable wrong answer rather than an error. The fingerprint prefix
 * makes collisions across models impossible while preserving sharing between namespaces that really
 * do use the same model.</p>
 *
 * <p>Cached vectors are defensively copied on store and on every hit, so callers
 * can never mutate cached state.</p>
 */
public final class CachingEmbeddingProvider implements EmbeddingProvider {

    public static final String DEFAULT_CACHE_NAME = "spector-embeddings";

    /**
     * Key prefix used when no fingerprint is supplied.
     *
     * <p>Deliberately not the empty string: an unscoped entry must be distinguishable from a
     * fingerprinted one, so that a provider constructed without a fingerprint can never read a
     * fingerprinted entry or vice versa.</p>
     */
    static final String UNSCOPED = "unscoped";

    private static final char KEY_SEPARATOR = ':';

    private final EmbeddingProvider delegate;
    private final SpectorCache cache;
    private final String keyScope;

    /**
     * Constructs a caching decorator with a specific {@link SpectorCache}, scoping cache keys to a
     * provider fingerprint.
     *
     * @param delegate    the underlying provider
     * @param cache       the cache SPI instance
     * @param fingerprint fingerprint of the configuration that produced {@code delegate}; may be
     *                    {@code null}, in which case keys are scoped as {@link #UNSCOPED}
     */
    public CachingEmbeddingProvider(EmbeddingProvider delegate, SpectorCache cache,
                                    ProviderFingerprint fingerprint) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.cache = Objects.requireNonNull(cache, "cache must not be null");
        this.keyScope = fingerprint == null ? UNSCOPED : fingerprint.digest();
    }

    /**
     * Constructs a caching decorator with a specific {@link SpectorCache} and no fingerprint.
     *
     * @param delegate the underlying provider
     * @param cache    the cache SPI instance
     */
    public CachingEmbeddingProvider(EmbeddingProvider delegate, SpectorCache cache) {
        this(delegate, cache, null);
    }

    /**
     * Constructs a caching decorator obtaining the default embedding cache from a {@link SpectorCacheManager}.
     *
     * @param delegate     the underlying provider
     * @param cacheManager the cache manager instance
     */
    public CachingEmbeddingProvider(EmbeddingProvider delegate, SpectorCacheManager cacheManager) {
        this(delegate, Objects.requireNonNull(cacheManager, "cacheManager must not be null").getCache(DEFAULT_CACHE_NAME));
    }

    /**
     * Constructs a caching decorator obtaining the default embedding cache from a
     * {@link SpectorCacheManager}, scoping cache keys to a provider fingerprint.
     *
     * @param delegate     the underlying provider
     * @param cacheManager the cache manager instance
     * @param fingerprint  fingerprint of the configuration that produced {@code delegate}
     */
    public CachingEmbeddingProvider(EmbeddingProvider delegate, SpectorCacheManager cacheManager,
                                    ProviderFingerprint fingerprint) {
        this(delegate, Objects.requireNonNull(cacheManager, "cacheManager must not be null").getCache(DEFAULT_CACHE_NAME),
                fingerprint);
    }

    /**
     * Constructs a caching decorator obtaining a named cache from a {@link SpectorCacheManager}.
     *
     * @param delegate     the underlying provider
     * @param cacheManager the cache manager instance
     * @param cacheName    name of the cache
     */
    public CachingEmbeddingProvider(EmbeddingProvider delegate, SpectorCacheManager cacheManager, String cacheName) {
        this(delegate, Objects.requireNonNull(cacheManager, "cacheManager must not be null")
                .getCache(Objects.requireNonNull(cacheName, "cacheName must not be null")));
    }

    /**
     * Wraps a provider with caching using an explicit {@link SpectorCache}.
     *
     * @param provider the provider to wrap
     * @param cache    the cache SPI instance
     * @return the caching decorator, or {@code provider} itself if already wrapped or cache is null/noop
     */
    public static EmbeddingProvider wrap(EmbeddingProvider provider, SpectorCache cache) {
        return wrap(provider, cache, null);
    }

    /**
     * Wraps a provider with caching using an explicit {@link SpectorCache}, scoping cache keys to a
     * provider fingerprint.
     *
     * @param provider    the provider to wrap
     * @param cache       the cache SPI instance
     * @param fingerprint fingerprint of the configuration that produced {@code provider}
     * @return the caching decorator, or {@code provider} itself if already wrapped or cache is null/noop
     */
    public static EmbeddingProvider wrap(EmbeddingProvider provider, SpectorCache cache,
                                         ProviderFingerprint fingerprint) {
        Objects.requireNonNull(provider, "provider must not be null");
        if (cache == null || cache instanceof NoOpSpectorCache || provider instanceof CachingEmbeddingProvider) {
            return provider;
        }
        return new CachingEmbeddingProvider(provider, cache, fingerprint);
    }

    /**
     * Wraps a provider with caching using the default embedding cache from a {@link SpectorCacheManager}.
     *
     * @param provider     the provider to wrap
     * @param cacheManager the cache manager instance
     * @return the caching decorator, or {@code provider} itself if already wrapped or manager is null
     */
    public static EmbeddingProvider wrap(EmbeddingProvider provider, SpectorCacheManager cacheManager) {
        return wrap(provider, cacheManager, (ProviderFingerprint) null);
    }

    /**
     * Wraps a provider with caching using the default embedding cache from a {@link SpectorCacheManager},
     * scoping cache keys to a provider fingerprint.
     *
     * @param provider     the provider to wrap
     * @param cacheManager the cache manager instance
     * @param fingerprint  fingerprint of the configuration that produced {@code provider}
     * @return the caching decorator, or {@code provider} itself if already wrapped or manager is null
     */
    public static EmbeddingProvider wrap(EmbeddingProvider provider, SpectorCacheManager cacheManager,
                                         ProviderFingerprint fingerprint) {
        if (cacheManager == null || provider instanceof CachingEmbeddingProvider) {
            return provider;
        }
        return wrap(provider, cacheManager.getCache(DEFAULT_CACHE_NAME), fingerprint);
    }

    /**
     * Wraps a provider with caching using a named cache from a {@link SpectorCacheManager}.
     *
     * @param provider     the provider to wrap
     * @param cacheManager the cache manager instance
     * @param cacheName    name of the cache
     * @return the caching decorator, or {@code provider} itself if already wrapped or manager is null
     */
    public static EmbeddingProvider wrap(EmbeddingProvider provider, SpectorCacheManager cacheManager, String cacheName) {
        if (cacheManager == null || provider instanceof CachingEmbeddingProvider) {
            return provider;
        }
        return wrap(provider, cacheManager.getCache(cacheName));
    }

    @Override
    public EmbeddingResult embed(String text) {
        if (text == null) {
            return delegate.embed(null); // let the delegate apply its own validation
        }
        String key = cacheKey(text);
        Optional<EmbeddingResult> cached = cache.get(key, EmbeddingResult.class);
        if (cached.isPresent()) {
            EmbeddingResult res = cached.get();
            return new EmbeddingResult(res.vector().clone(), res.tokenCount(), res.model());
        }
        EmbeddingResult result = delegate.embed(text);
        cache.put(key, new EmbeddingResult(result.vector().clone(), result.tokenCount(), result.model()));
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
            } else {
                List<Integer> positions = pending.computeIfAbsent(key, k -> {
                    pendingTexts.add(text);
                    return new ArrayList<>();
                });
                positions.add(i);
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

    /**
     * Returns the fingerprint digest this instance prefixes its cache keys with, or
     * {@value #UNSCOPED} when constructed without a fingerprint.
     *
     * @return the key scope token
     */
    public String keyScope() {
        return keyScope;
    }

    /**
     * Computes the cache key for a text under this provider's fingerprint.
     *
     * <p>The scope is a prefix rather than part of the hashed input so that entries remain
     * attributable to a provider when inspecting the cache, which matters when diagnosing a
     * suspected cross-model hit.</p>
     */
    private String cacheKey(String text) {
        return keyScope + KEY_SEPARATOR + sha256Hex(text);
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
