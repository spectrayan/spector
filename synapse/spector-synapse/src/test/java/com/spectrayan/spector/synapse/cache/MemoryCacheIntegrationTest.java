/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-synapse/LICENSE
 *
 * Change Date: July 6, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.synapse.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrayan.spector.commons.cache.SpectorCache;
import com.spectrayan.spector.commons.cache.SpectorCacheErrorHandler;
import com.spectrayan.spector.commons.cache.SpectorCacheKeyGenerator;
import com.spectrayan.spector.memory.persist.DataEncryptor;
import com.spectrayan.spector.memory.cortex.cache.MemoryCacheNames;
import com.spectrayan.spector.spring.cache.EncryptingJsonCacheSerializer;
import com.spectrayan.spector.spring.cache.SpringSpectorCacheManagerAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryCacheIntegrationTest {

    record MockStats(int count, String tier) {}

    @Test
    @DisplayName("multi-user namespaced SpectorCacheManager isolates cache entries under shared Spring CacheManager")
    void multiUserCacheIsolation_keysNeverCross() {
        var springCacheManager = new ConcurrentMapCacheManager(
                MemoryCacheNames.GRAPH_OVERVIEW,
                MemoryCacheNames.TOPOLOGY_STATS,
                MemoryCacheNames.MEMORY_STATS,
                MemoryCacheNames.SCORING_STATS
        );

        // User 1 manager
        var user1Manager = SpringSpectorCacheManagerAdapter.builder(springCacheManager)
                .keyGenerator(SpectorCacheKeyGenerator.forNamespace("user-AAA"))
                .errorHandler(SpectorCacheErrorHandler.STRICT)
                .build();

        // User 2 manager
        var user2Manager = SpringSpectorCacheManagerAdapter.builder(springCacheManager)
                .keyGenerator(SpectorCacheKeyGenerator.forNamespace("user-BBB"))
                .errorHandler(SpectorCacheErrorHandler.STRICT)
                .build();

        SpectorCache cache1 = user1Manager.getCache(MemoryCacheNames.MEMORY_STATS);
        SpectorCache cache2 = user2Manager.getCache(MemoryCacheNames.MEMORY_STATS);

        var counter1 = new AtomicInteger(0);
        var counter2 = new AtomicInteger(0);

        MockStats stats1 = cache1.get("current", MockStats.class, () -> new MockStats(counter1.incrementAndGet(), "USER_A_DATA"));
        MockStats stats2 = cache2.get("current", MockStats.class, () -> new MockStats(counter2.incrementAndGet(), "USER_B_DATA"));

        assertThat(stats1.tier()).isEqualTo("USER_A_DATA");
        assertThat(stats2.tier()).isEqualTo("USER_B_DATA");

        // Verify underlying Spring cache contains both partitioned keys
        var rawSpringCache = springCacheManager.getCache(MemoryCacheNames.MEMORY_STATS);
        assertThat(rawSpringCache).isNotNull();
        assertThat(rawSpringCache.get("ns:user-AAA:current", MockStats.class)).isEqualTo(stats1);
        assertThat(rawSpringCache.get("ns:user-BBB:current", MockStats.class)).isEqualTo(stats2);

        // Evict user 1 does not affect user 2
        cache1.evict("current");
        assertThat(cache1.get("current", MockStats.class)).isEmpty();
        assertThat(cache2.get("current", MockStats.class)).isPresent().contains(stats2);
    }

    @Test
    @DisplayName("encrypted per-user cache encrypts stored bytes transparently")
    void encryptedPerUserCache_encryptsPayload() {
        var springCacheManager = new ConcurrentMapCacheManager(MemoryCacheNames.GRAPH_OVERVIEW);
        var mapper = new ObjectMapper();

        var mockEncryptor = new DataEncryptor() {
            @Override
            public byte[] encryptText(byte[] plaintext) { return encryptPayload(plaintext); }

            @Override
            public byte[] decryptText(byte[] ciphertext) { return decryptPayload(ciphertext); }

            @Override
            public byte[] encryptPayload(byte[] plaintext) {
                byte[] out = new byte[plaintext.length];
                for (int i = 0; i < plaintext.length; i++) out[i] = (byte) (plaintext[i] ^ 0x3C);
                return out;
            }

            @Override
            public byte[] decryptPayload(byte[] ciphertext) { return encryptPayload(ciphertext); }

            @Override
            public long encodeTag(String tag) { return 0; }

            @Override
            public boolean isEnabled() { return true; }
        };

        var userManager = SpringSpectorCacheManagerAdapter.builder(springCacheManager)
                .keyGenerator(SpectorCacheKeyGenerator.forNamespace("user-SECURE"))
                .serializer(new EncryptingJsonCacheSerializer(mapper, mockEncryptor))
                .errorHandler(SpectorCacheErrorHandler.STRICT)
                .build();

        SpectorCache cache = userManager.getCache(MemoryCacheNames.GRAPH_OVERVIEW);
        var original = new MockStats(100, "CONFIDENTIAL_GRAPH");

        cache.put("overview:50", original);

        // Cache read should return decrypted typed object
        var retrieved = cache.get("overview:50", MockStats.class);
        assertThat(retrieved).isPresent().contains(original);

        // Spring store should contain raw encrypted byte array
        var rawSpringCache = springCacheManager.getCache(MemoryCacheNames.GRAPH_OVERVIEW);
        assertThat(rawSpringCache).isNotNull();
        byte[] storedBytes = rawSpringCache.get("ns:user-SECURE:overview:50", byte[].class);
        assertThat(storedBytes).isNotNull();
    }
}
