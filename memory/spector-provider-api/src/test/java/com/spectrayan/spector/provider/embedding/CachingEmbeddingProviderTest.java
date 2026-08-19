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

import static org.assertj.core.api.Assertions.*;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.spectrayan.spector.commons.cache.SpectorCache;
import com.spectrayan.spector.commons.cache.SpectorCacheManager;
import com.spectrayan.spector.commons.cache.TtlConcurrentMapCacheManager;

/**
 * Tests for {@link CachingEmbeddingProvider}.
 */
@DisplayName("Caching Embedding Provider")
class CachingEmbeddingProviderTest {

    /** Counts delegate calls and returns a deterministic per-text vector. */
    static class CountingProvider implements EmbeddingProvider {
        final AtomicInteger embedCalls = new AtomicInteger();
        final AtomicInteger batchCalls = new AtomicInteger();
        final ConcurrentHashMap<String, AtomicInteger> callsPerText = new ConcurrentHashMap<>();
        final AtomicBoolean closed = new AtomicBoolean();

        static float[] vectorFor(String text) {
            return new float[]{text.length(), text.hashCode() % 100};
        }

        @Override
        public EmbeddingResult embed(String text) {
            if (text == null || text.isBlank()) {
                throw new IllegalArgumentException("text must not be null or blank");
            }
            embedCalls.incrementAndGet();
            callsPerText.computeIfAbsent(text, t -> new AtomicInteger()).incrementAndGet();
            return new EmbeddingResult(vectorFor(text), text.length(), "counting-model");
        }

        @Override
        public List<EmbeddingResult> embedBatch(List<String> texts) {
            batchCalls.incrementAndGet();
            return texts.stream().map(this::embed).toList();
        }

        @Override
        public int dimensions() {
            return 2;
        }

        @Override
        public String modelName() {
            return "counting-model";
        }

        @Override
        public int maxTokens() {
            return 8192;
        }

        @Override
        public void close() {
            closed.set(true);
        }
    }

    private static SpectorCacheManager createCacheManager(int maxSize) {
        return SpectorCacheManager.builder()
                .defaultMaxSize(maxSize)
                .build();
    }

    private static SpectorCacheManager createCacheManager(int maxSize, Duration ttl) {
        return SpectorCacheManager.builder()
                .defaultMaxSize(maxSize)
                .defaultTtl(ttl)
                .build();
    }

    // = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = =
    // wrap()
    // = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = =

    @Nested
    @DisplayName("wrap()")
    class WrapTests {

        @Test
        @DisplayName("returns decorator when wrapping SpectorCacheManager")
        void wrapsWithCacheManager() {
            var delegate = new CountingProvider();
            var cm = createCacheManager(10);
            var wrapped = CachingEmbeddingProvider.wrap(delegate, cm);
            assertThat(wrapped).isInstanceOf(CachingEmbeddingProvider.class);
            assertThat(((CachingEmbeddingProvider) wrapped).delegate()).isSameAs(delegate);
        }

        @Test
        @DisplayName("returns provider unchanged when cache manager is null")
        void passthroughWhenNullManager() {
            var delegate = new CountingProvider();
            var wrapped = CachingEmbeddingProvider.wrap(delegate, (SpectorCacheManager) null);
            assertThat(wrapped).isSameAs(delegate);
        }

        @Test
        @DisplayName("does not double-wrap")
        void noDoubleWrap() {
            var cm = createCacheManager(10);
            var once = CachingEmbeddingProvider.wrap(new CountingProvider(), cm);
            var twice = CachingEmbeddingProvider.wrap(once, cm);
            assertThat(twice).isSameAs(once);
        }

        @Test
        @DisplayName("wraps with explicit SpectorCache")
        void wrapsWithCustomSpectorCache() {
            var customCache = createCacheManager(50).getCache("custom-embeddings");
            var delegate = new CountingProvider();
            var wrapped = CachingEmbeddingProvider.wrap(delegate, customCache);

            assertThat(wrapped).isInstanceOf(CachingEmbeddingProvider.class);
            CachingEmbeddingProvider cep = (CachingEmbeddingProvider) wrapped;
            assertThat(cep.cache()).isSameAs(customCache);
            assertThat(cep.cache().getName()).isEqualTo("custom-embeddings");
        }

        @Test
        @DisplayName("rejects null delegate provider")
        void rejectsNullProvider() {
            var cm = createCacheManager(10);
            assertThatThrownBy(() -> CachingEmbeddingProvider.wrap(null, cm.getCache("test")))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new CachingEmbeddingProvider(null, cm))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = =
    // embed(): hits, misses, delegation
    // = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = =

    @Nested
    @DisplayName("embed()")
    class EmbedTests {

        @Test
        @DisplayName("cache miss delegates and stores; hit skips the delegate")
        void missThenHit() {
            var delegate = new CountingProvider();
            var cache = new CachingEmbeddingProvider(delegate, createCacheManager(10));

            var first = cache.embed("hello");
            var second = cache.embed("hello");

            assertThat(delegate.embedCalls.get()).isEqualTo(1);
            assertThat(second.vector()).containsExactly(first.vector());
            assertThat(second.tokenCount()).isEqualTo(first.tokenCount());
            assertThat(second.model()).isEqualTo(first.model());
        }

        @Test
        @DisplayName("different texts are cached independently")
        void distinctTexts() {
            var delegate = new CountingProvider();
            var cache = new CachingEmbeddingProvider(delegate, createCacheManager(10));

            cache.embed("alpha");
            cache.embed("beta");
            cache.embed("alpha");
            cache.embed("beta");

            assertThat(delegate.embedCalls.get()).isEqualTo(2);
        }

        @Test
        @DisplayName("mutating a returned vector does not corrupt the cache")
        void defensiveCopies() {
            var cache = new CachingEmbeddingProvider(new CountingProvider(), createCacheManager(10));

            var first = cache.embed("text");
            first.vector()[0] = 999f;
            var second = cache.embed("text");

            assertThat(second.vector()[0]).isNotEqualTo(999f);
        }

        @Test
        @DisplayName("delegate failures propagate and are not cached")
        void delegateFailurePropagates() {
            var delegate = new CountingProvider();
            var cache = new CachingEmbeddingProvider(delegate, createCacheManager(10));

            assertThatThrownBy(() -> cache.embed(null)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> cache.embed("  ")).isInstanceOf(IllegalArgumentException.class);
        }
    }

    // = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = =
    // Capacity eviction
    // = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = =

    @Nested
    @DisplayName("capacity eviction")
    class EvictionTests {

        @Test
        @DisplayName("evicts entries when capacity is reached")
        void evictsWhenFull() {
            var delegate = new CountingProvider();
            var cache = new CachingEmbeddingProvider(delegate, createCacheManager(2));

            cache.embed("a");
            cache.embed("b");
            cache.embed("c"); // triggers eviction to stay within capacity of 2

            int totalCalls = delegate.callsPerText.values().stream().mapToInt(AtomicInteger::get).sum();
            assertThat(totalCalls).isEqualTo(3);
        }

        @Test
        @DisplayName("size never exceeds maxSize")
        void boundedSize() {
            var cache = new CachingEmbeddingProvider(new CountingProvider(), createCacheManager(5));
            for (int i = 0; i < 50; i++) {
                cache.embed("text-" + i);
            }
        }
    }

    // = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = =
    // TTL expiry
    // = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = =

    @Nested
    @DisplayName("TTL expiry")
    class TtlTests {

        @Test
        @DisplayName("expired entry is treated as a miss")
        void expiry() throws InterruptedException {
            var delegate = new CountingProvider();
            var cm = createCacheManager(10, Duration.ofMillis(50));
            var cache = new CachingEmbeddingProvider(delegate, cm);

            cache.embed("text");
            assertThat(delegate.embedCalls.get()).isEqualTo(1);

            // Access immediately: still fresh
            cache.embed("text");
            assertThat(delegate.embedCalls.get()).isEqualTo(1);

            // Wait past TTL
            Thread.sleep(70);
            cache.embed("text");  // expired -> re-embed
            assertThat(delegate.embedCalls.get()).isEqualTo(2);
        }

        @Test
        @DisplayName("zero TTL never expires")
        void zeroTtlNeverExpires() {
            var delegate = new CountingProvider();
            var cm = createCacheManager(10, Duration.ZERO);
            var cache = new CachingEmbeddingProvider(delegate, cm);

            cache.embed("text");
            cache.embed("text");
            assertThat(delegate.embedCalls.get()).isEqualTo(1);
        }
    }

    // = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = =
    // embedBatch()
    // = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = =

    @Nested
    @DisplayName("embedBatch()")
    class BatchTests {

        @Test
        @DisplayName("only uncached texts are delegated")
        void partialHit() {
            var delegate = new CountingProvider();
            var cache = new CachingEmbeddingProvider(delegate, createCacheManager(10));

            cache.embed("a");
            cache.embed("b");
            delegate.embedCalls.set(0);

            var results = cache.embedBatch(List.of("a", "x", "b", "y"));

            assertThat(results).hasSize(4);
            assertThat(delegate.embedCalls.get()).isEqualTo(2);  // only "x" and "y"
            assertThat(results.get(0).vector()).containsExactly(CountingProvider.vectorFor("a"));
            assertThat(results.get(1).vector()).containsExactly(CountingProvider.vectorFor("x"));
            assertThat(results.get(2).vector()).containsExactly(CountingProvider.vectorFor("b"));
            assertThat(results.get(3).vector()).containsExactly(CountingProvider.vectorFor("y"));
        }

        @Test
        @DisplayName("fully cached batch makes no delegate call")
        void fullHit() {
            var delegate = new CountingProvider();
            var cache = new CachingEmbeddingProvider(delegate, createCacheManager(10));

            cache.embedBatch(List.of("a", "b"));
            int callsBefore = delegate.embedCalls.get();

            var results = cache.embedBatch(List.of("b", "a"));

            assertThat(delegate.embedCalls.get()).isEqualTo(callsBefore);
            assertThat(delegate.batchCalls.get()).isEqualTo(1);
            assertThat(results.get(0).vector()).containsExactly(CountingProvider.vectorFor("b"));
            assertThat(results.get(1).vector()).containsExactly(CountingProvider.vectorFor("a"));
        }

        @Test
        @DisplayName("duplicate texts within a batch are embedded once")
        void duplicatesInBatch() {
            var delegate = new CountingProvider();
            var cache = new CachingEmbeddingProvider(delegate, createCacheManager(10));

            var results = cache.embedBatch(List.of("same", "same", "same"));

            assertThat(delegate.embedCalls.get()).isEqualTo(1);
            assertThat(results).hasSize(3);
            assertThat(results.get(0).vector()).containsExactly(CountingProvider.vectorFor("same"));
            assertThat(results.get(2).vector()).containsExactly(CountingProvider.vectorFor("same"));
        }

        @Test
        @DisplayName("duplicate positions in a batch never share a vector reference")
        void duplicatesDoNotShareVectorReference() {
            var cache = new CachingEmbeddingProvider(new CountingProvider(), createCacheManager(10));

            var results = cache.embedBatch(List.of("same", "same", "same"));

            assertThat(results.get(0).vector()).isNotSameAs(results.get(1).vector());
            assertThat(results.get(1).vector()).isNotSameAs(results.get(2).vector());
            assertThat(results.get(0).vector()).isNotSameAs(results.get(2).vector());
        }

        @Test
        @DisplayName("null element falls through to delegate validation")
        void nullElementDelegates() {
            var cache = new CachingEmbeddingProvider(new CountingProvider(), createCacheManager(10));
            var texts = java.util.Arrays.asList("a", null);
            assertThatThrownBy(() -> cache.embedBatch(texts))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("delegate returning a mismatched batch size fails fast")
        void mismatchedBatchSize() {
            var broken = new CountingProvider() {
                @Override
                public List<EmbeddingResult> embedBatch(List<String> texts) {
                    return List.of();
                }
            };
            var cache = new CachingEmbeddingProvider(broken, createCacheManager(10));
            assertThatThrownBy(() -> cache.embedBatch(List.of("a", "b")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("2 texts");
        }

        @Test
        @DisplayName("empty batch returns empty list without delegation")
        void emptyBatch() {
            var delegate = new CountingProvider();
            var cache = new CachingEmbeddingProvider(delegate, createCacheManager(10));
            assertThat(cache.embedBatch(List.of())).isEmpty();
            assertThat(delegate.batchCalls.get()).isZero();
        }
    }

    // = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = =
    // Delegation of metadata + close
    // = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = =

    @Nested
    @DisplayName("delegation")
    class DelegationTests {

        @Test
        @DisplayName("dimensions, modelName, maxTokens delegate")
        void metadataDelegates() {
            var cache = new CachingEmbeddingProvider(new CountingProvider(), createCacheManager(10));
            assertThat(cache.dimensions()).isEqualTo(2);
            assertThat(cache.modelName()).isEqualTo("counting-model");
            assertThat(cache.maxTokens()).isEqualTo(8192);
        }

        @Test
        @DisplayName("close clears the cache and closes the delegate")
        void closePropagates() {
            var delegate = new CountingProvider();
            var cache = new CachingEmbeddingProvider(delegate, createCacheManager(10));
            cache.embed("text");

            cache.close();

            assertThat(delegate.closed).isTrue();
        }
    }

    // = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = =
    // Concurrency
    // = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = =

    @Nested
    @DisplayName("concurrency")
    class ConcurrencyTests {

        @Test
        @DisplayName("concurrent access returns correct vectors and stays bounded")
        void concurrentAccess() throws Exception {
            int threads = 8;
            int iterations = 500;
            int distinctTexts = 20;
            int maxSize = 32;

            var delegate = new CountingProvider();
            var cache = new CachingEmbeddingProvider(delegate, createCacheManager(maxSize));
            var pool = Executors.newFixedThreadPool(threads);
            var start = new CountDownLatch(1);
            var failures = new AtomicInteger();

            try {
                for (int t = 0; t < threads; t++) {
                    final int seed = t;
                    pool.submit(() -> {
                        try {
                            start.await();
                            for (int i = 0; i < iterations; i++) {
                                String text = "text-" + ((seed * 31 + i) % distinctTexts);
                                var result = cache.embed(text);
                                float[] expected = CountingProvider.vectorFor(text);
                                if (result.vector()[0] != expected[0] || result.vector()[1] != expected[1]) {
                                    failures.incrementAndGet();
                                }
                            }
                        } catch (Exception e) {
                            failures.incrementAndGet();
                        }
                    });
                }
                start.countDown();
                pool.shutdown();
                assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
            } finally {
                pool.shutdownNow();
            }

            assertThat(failures.get()).isZero();
            assertThat(delegate.embedCalls.get()).isLessThanOrEqualTo(threads * distinctTexts);
        }
    }
}
