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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.spectrayan.spector.commons.cache.SpectorCache;
import com.spectrayan.spector.provider.ProviderConfig;
import com.spectrayan.spector.provider.ProviderFingerprint;

/**
 * Verifies that the shared process-wide embedding cache is keyed by provider fingerprint, not by
 * text alone.
 *
 * <p>These tests exist because a text-only cache key is only safe while exactly one embedding
 * provider exists per process. Once namespaces can be configured with different models, a text-only
 * key lets the first model's vector answer the second model's request. That failure is silent — no
 * exception, no dimension mismatch when the two models share a width — and produces confidently
 * wrong neighbours, so it must be covered by an assertion on the vector itself rather than on
 * whether a call succeeded.</p>
 */
@DisplayName("Model-Scoped Embedding Cache")
class ModelScopedEmbeddingCacheTest {

    private static final String TEXT = "the same sentence embedded twice";

    /** Cache that records every key written, so entry counts and scoping can be asserted. */
    static final class RecordingCache implements SpectorCache {
        private final Map<String, Object> entries = new ConcurrentHashMap<>();

        @Override
        public String getName() {
            return "recording";
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> Optional<T> get(String key, Class<T> targetClass) {
            return Optional.ofNullable((T) entries.get(key));
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T get(String key, Class<T> targetClass, Supplier<T> valueLoader) {
            return (T) entries.computeIfAbsent(key, k -> valueLoader.get());
        }

        @Override
        public void put(String key, Object value) {
            entries.put(key, value);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T putIfAbsent(String key, Object value, Class<T> targetClass) {
            return (T) entries.putIfAbsent(key, value);
        }

        @Override
        public void evict(String key) {
            entries.remove(key);
        }

        @Override
        public void clear() {
            entries.clear();
        }

        Set<String> keys() {
            return Set.copyOf(entries.keySet());
        }

        int size() {
            return entries.size();
        }
    }

    /**
     * Provider whose vector depends on its model name, so a cross-model cache hit is detectable by
     * inspecting the returned vector.
     */
    static final class ModelBoundProvider implements EmbeddingProvider {
        private final String model;
        private final float marker;
        final AtomicInteger embedCalls = new AtomicInteger();

        ModelBoundProvider(String model, float marker) {
            this.model = model;
            this.marker = marker;
        }

        @Override
        public EmbeddingResult embed(String text) {
            embedCalls.incrementAndGet();
            return new EmbeddingResult(new float[]{marker, text.length()}, text.length(), model);
        }

        @Override
        public List<EmbeddingResult> embedBatch(List<String> texts) {
            return texts.stream().map(this::embed).toList();
        }

        @Override
        public int dimensions() {
            return 2;
        }

        @Override
        public String modelName() {
            return model;
        }

        @Override
        public int maxTokens() {
            return 8192;
        }

        @Override
        public void close() {
            // no resources
        }
    }

    private static ProviderFingerprint fingerprintOf(String model) {
        return ProviderFingerprint.of(new ProviderConfig(
                "ollama", "ollama", model, "", "http://localhost:11434", 768, Map.of()));
    }

    @Test
    @DisplayName("same text under two different models yields two cache entries and two distinct vectors")
    void differentModelsDoNotShareCacheEntries() {
        var cache = new RecordingCache();
        var modelA = new ModelBoundProvider("nomic-embed-text", 1.0f);
        var modelB = new ModelBoundProvider("mxbai-embed-large", 2.0f);

        var cachedA = CachingEmbeddingProvider.wrap(modelA, cache, fingerprintOf("nomic-embed-text"));
        var cachedB = CachingEmbeddingProvider.wrap(modelB, cache, fingerprintOf("mxbai-embed-large"));

        EmbeddingResult first = cachedA.embed(TEXT);
        EmbeddingResult second = cachedB.embed(TEXT);

        // Each delegate was actually consulted: B did not read A's entry.
        assertThat(modelA.embedCalls.get()).isEqualTo(1);
        assertThat(modelB.embedCalls.get()).isEqualTo(1);

        // Two entries in the one shared cache, under distinct keys.
        assertThat(cache.size()).isEqualTo(2);
        assertThat(cache.keys()).hasSize(2);

        // The assertion that matters: the vectors differ, and each names its own model. Asserting
        // only that both calls returned would pass against the text-only key this test replaces.
        assertThat(first.vector()).containsExactly(1.0f, (float) TEXT.length());
        assertThat(second.vector()).containsExactly(2.0f, (float) TEXT.length());
        assertThat(first.model()).isEqualTo("nomic-embed-text");
        assertThat(second.model()).isEqualTo("mxbai-embed-large");
        assertThat(first.vector()).isNotEqualTo(second.vector());
    }

    @Test
    @DisplayName("same text under the same model shares one cache entry across namespaces")
    void identicalConfigurationSharesCacheEntries() {
        var cache = new RecordingCache();
        var delegate = new ModelBoundProvider("nomic-embed-text", 1.0f);

        // Two wrappers standing in for two namespaces resolved to the same configuration.
        var namespaceOne = CachingEmbeddingProvider.wrap(delegate, cache, fingerprintOf("nomic-embed-text"));
        var namespaceTwo = CachingEmbeddingProvider.wrap(delegate, cache, fingerprintOf("nomic-embed-text"));

        EmbeddingResult first = namespaceOne.embed(TEXT);
        EmbeddingResult second = namespaceTwo.embed(TEXT);

        // Sharing is preserved where it is correct: one delegate call, one entry.
        assertThat(delegate.embedCalls.get()).isEqualTo(1);
        assertThat(cache.size()).isEqualTo(1);
        assertThat(first.vector()).containsExactly(second.vector());
    }

    @Test
    @DisplayName("batch embedding is scoped by fingerprint too")
    void batchRespectsFingerprintScope() {
        var cache = new RecordingCache();
        var modelA = new ModelBoundProvider("nomic-embed-text", 1.0f);
        var modelB = new ModelBoundProvider("mxbai-embed-large", 2.0f);

        var cachedA = CachingEmbeddingProvider.wrap(modelA, cache, fingerprintOf("nomic-embed-text"));
        var cachedB = CachingEmbeddingProvider.wrap(modelB, cache, fingerprintOf("mxbai-embed-large"));

        List<EmbeddingResult> fromA = cachedA.embedBatch(List.of(TEXT, "another"));
        List<EmbeddingResult> fromB = cachedB.embedBatch(List.of(TEXT, "another"));

        assertThat(modelA.embedCalls.get()).isEqualTo(2);
        assertThat(modelB.embedCalls.get()).isEqualTo(2);
        assertThat(cache.size()).isEqualTo(4);
        assertThat(fromA.get(0).vector()).isNotEqualTo(fromB.get(0).vector());
    }

    @Test
    @DisplayName("an unscoped wrapper cannot read a fingerprinted entry")
    void unscopedAndScopedEntriesDoNotCollide() {
        var cache = new RecordingCache();
        var scopedDelegate = new ModelBoundProvider("nomic-embed-text", 1.0f);
        var unscopedDelegate = new ModelBoundProvider("nomic-embed-text", 9.0f);

        var scoped = CachingEmbeddingProvider.wrap(scopedDelegate, cache, fingerprintOf("nomic-embed-text"));
        var unscoped = CachingEmbeddingProvider.wrap(unscopedDelegate, cache, (ProviderFingerprint) null);

        scoped.embed(TEXT);
        EmbeddingResult unscopedResult = unscoped.embed(TEXT);

        assertThat(unscopedDelegate.embedCalls.get()).isEqualTo(1);
        assertThat(cache.size()).isEqualTo(2);
        assertThat(unscopedResult.vector()).containsExactly(9.0f, (float) TEXT.length());
        assertThat(((CachingEmbeddingProvider) unscoped).keyScope())
                .isEqualTo(CachingEmbeddingProvider.UNSCOPED);
    }

    @Test
    @DisplayName("cache keys carry the fingerprint digest as a readable prefix")
    void keysArePrefixedWithTheDigest() {
        var cache = new RecordingCache();
        var fingerprint = fingerprintOf("nomic-embed-text");
        var cached = CachingEmbeddingProvider.wrap(
                new ModelBoundProvider("nomic-embed-text", 1.0f), cache, fingerprint);

        cached.embed(TEXT);

        assertThat(cache.keys()).allSatisfy(key ->
                assertThat(key).startsWith(fingerprint.digest() + ":"));
    }
}
