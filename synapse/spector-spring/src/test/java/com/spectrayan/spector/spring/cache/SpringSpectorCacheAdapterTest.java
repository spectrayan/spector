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
package com.spectrayan.spector.spring.cache;

import com.spectrayan.spector.commons.cache.SpectorCache;
import com.spectrayan.spector.commons.cache.SpectorCacheErrorHandler;
import com.spectrayan.spector.commons.cache.SpectorCacheKeyGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class SpringSpectorCacheAdapterTest {

    @Test
    @DisplayName("adapter delegates get, put, evict to Spring CacheManager with key prefixing")
    void adapter_delegatesWithKeyPrefix() {
        var springManager = new ConcurrentMapCacheManager("test-cache");
        var managerAdapter = SpringSpectorCacheManagerAdapter.builder(springManager)
                .keyGenerator(SpectorCacheKeyGenerator.forNamespace("user-999"))
                .errorHandler(SpectorCacheErrorHandler.STRICT)
                .build();

        SpectorCache cache = managerAdapter.getCache("test-cache");
        assertThat(cache.getName()).isEqualTo("test-cache");

        cache.put("keyA", "valA");
        assertThat(cache.get("keyA", String.class)).contains("valA");

        // Inspect underlying Spring cache — key should be prefixed
        var rawSpringCache = springManager.getCache("test-cache");
        assertThat(rawSpringCache).isNotNull();
        assertThat(rawSpringCache.get("ns:user-999:keyA", String.class)).isEqualTo("valA");

        cache.evict("keyA");
        assertThat(cache.get("keyA", String.class)).isEmpty();
    }

    @Test
    @DisplayName("load-through gets from valueLoader on miss")
    void getWithValueLoader_worksWithAdapter() {
        var springManager = new ConcurrentMapCacheManager("test-cache");
        var managerAdapter = SpringSpectorCacheManagerAdapter.builder(springManager)
                .keyGenerator(SpectorCacheKeyGenerator.forNamespace("user-abc"))
                .build();

        SpectorCache cache = managerAdapter.getCache("test-cache");
        var counter = new AtomicInteger(0);

        String first = cache.get("k", String.class, () -> "res-" + counter.incrementAndGet());
        assertThat(first).isEqualTo("res-1");

        String second = cache.get("k", String.class, () -> "res-" + counter.incrementAndGet());
        assertThat(second).isEqualTo("res-1");
        assertThat(counter.get()).isEqualTo(1);
    }
}
