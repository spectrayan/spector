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
package com.spectrayan.spector.commons.cache;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class TtlConcurrentMapCacheTest {

    @Test
    @DisplayName("put and get returns cached value")
    void putAndGet_returnsValue() {
        var cache = new TtlConcurrentMapCache(
                "test-cache",
                SpectorCacheKeyGenerator.identity(),
                PassthroughCacheSerializer.INSTANCE,
                SpectorCacheErrorHandler.STRICT,
                Duration.ofMinutes(5),
                100);

        cache.put("key1", "hello-world");

        Optional<String> result = cache.get("key1", String.class);
        assertThat(result).isPresent().contains("hello-world");
    }

    @Test
    @DisplayName("get with valueLoader computes on miss and caches result")
    void getWithValueLoader_computesAndCaches() {
        var cache = new TtlConcurrentMapCache(
                "test-cache",
                SpectorCacheKeyGenerator.identity(),
                PassthroughCacheSerializer.INSTANCE,
                SpectorCacheErrorHandler.STRICT,
                Duration.ofMinutes(5),
                100);

        var counter = new AtomicInteger(0);
        String val1 = cache.get("keyA", String.class, () -> "computed-" + counter.incrementAndGet());
        assertThat(val1).isEqualTo("computed-1");

        // Second call should hit cache, counter should remain 1
        String val2 = cache.get("keyA", String.class, () -> "computed-" + counter.incrementAndGet());
        assertThat(val2).isEqualTo("computed-1");
        assertThat(counter.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("putIfAbsent only puts when absent and returns existing value")
    void putIfAbsent_worksCorrectly() {
        var cache = new TtlConcurrentMapCache(
                "test-cache",
                SpectorCacheKeyGenerator.identity(),
                PassthroughCacheSerializer.INSTANCE,
                SpectorCacheErrorHandler.STRICT,
                Duration.ofMinutes(5),
                100);

        String first = cache.putIfAbsent("k1", "v1", String.class);
        assertThat(first).isNull();

        String second = cache.putIfAbsent("k1", "v2", String.class);
        assertThat(second).isEqualTo("v1");

        assertThat(cache.get("k1", String.class)).contains("v1");
    }

    @Test
    @DisplayName("evict removes specific key while clear purges all")
    void evictAndClear_workCorrectly() {
        var cache = new TtlConcurrentMapCache(
                "test-cache",
                SpectorCacheKeyGenerator.identity(),
                PassthroughCacheSerializer.INSTANCE,
                SpectorCacheErrorHandler.STRICT,
                Duration.ofMinutes(5),
                100);

        cache.put("k1", "v1");
        cache.put("k2", "v2");
        assertThat(cache.size()).isEqualTo(2);

        cache.evict("k1");
        assertThat(cache.get("k1", String.class)).isEmpty();
        assertThat(cache.get("k2", String.class)).contains("v2");

        cache.clear();
        assertThat(cache.size()).isZero();
        assertThat(cache.get("k2", String.class)).isEmpty();
    }

    @Test
    @DisplayName("expired entries are evicted on read")
    void ttlExpiration_evictsExpired() throws InterruptedException {
        var cache = new TtlConcurrentMapCache(
                "test-cache",
                SpectorCacheKeyGenerator.identity(),
                PassthroughCacheSerializer.INSTANCE,
                SpectorCacheErrorHandler.STRICT,
                Duration.ofMillis(50),
                100);

        cache.put("expiring", "quick");
        assertThat(cache.get("expiring", String.class)).contains("quick");

        Thread.sleep(80);

        assertThat(cache.get("expiring", String.class)).isEmpty();
    }

    @Test
    @DisplayName("capacity bounding limits size to maxSize")
    void capacityBounding_evictsOldest() {
        var cache = new TtlConcurrentMapCache(
                "test-cache",
                SpectorCacheKeyGenerator.identity(),
                PassthroughCacheSerializer.INSTANCE,
                SpectorCacheErrorHandler.STRICT,
                Duration.ofMinutes(5),
                3);

        cache.put("a", "1");
        cache.put("b", "2");
        cache.put("c", "3");
        cache.put("d", "4");

        assertThat(cache.size()).isLessThanOrEqualTo(3);
    }
}
