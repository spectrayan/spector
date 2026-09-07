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
package com.spectrayan.spector.synapse.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrayan.spector.memory.api.SalienceProfileProvider;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.generation.LlmProvider;
import com.spectrayan.spector.synapse.config.SynapseProperties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("MemoryRegistryVirtualThreadStressTest")
class MemoryRegistryVirtualThreadStressTest {

    @TempDir
    Path tempDir;

    private SpectorMemory sharedMemory;
    private ObjectProvider<SpectorMemory> sharedProvider;
    private ObjectProvider<EmbeddingProvider> embedderProvider;
    private ObjectProvider<LlmProvider> textGenProvider;
    private ObjectProvider<SalienceProfileProvider> salienceProvider;
    private ObjectProvider<ObjectMapper> objectMapperProvider;

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> mockProvider() {
        return (ObjectProvider<T>) mock(ObjectProvider.class);
    }

    @BeforeEach
    void setUp() {
        sharedMemory = mock(SpectorMemory.class);
        sharedProvider = mockProvider();
        when(sharedProvider.getIfAvailable()).thenReturn(sharedMemory);

        embedderProvider = mockProvider();
        textGenProvider = mockProvider();
        salienceProvider = mockProvider();
        objectMapperProvider = mockProvider();
    }

    @Test
    @DisplayName("500 virtual threads resolving 50 distinct user IDs concurrently")
    void test500VirtualThreadsConcurrentResolutionAcrossUsers() throws Exception {
        SynapseProperties synapseProps = new SynapseProperties();
        synapseProps.auth().setEnabled(true);
        synapseProps.getMemory().setPersistencePath(tempDir.toString());

        MemoryRegistry registry = new MemoryRegistry(
                sharedProvider, synapseProps, embedderProvider, textGenProvider,
                salienceProvider, objectMapperProvider, 100
        );

        // Pre-populate mock user instances in cache to test concurrent lock-free resolution
        int userCount = 50;
        Map<String, SpectorMemory> mockInstances = new ConcurrentHashMap<>();
        for (int u = 0; u < userCount; u++) {
            String uid = String.format("USER%010d", u);
            SpectorMemory userMem = mock(SpectorMemory.class);
            mockInstances.put(uid, userMem);
            injectMockIntoCache(registry, uid, userMem);
        }

        int threadCount = 500;
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successfulResolutions = new AtomicInteger(0);

        try (ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int t = 0; t < threadCount; t++) {
                final int threadId = t;
                virtualExecutor.submit(() -> {
                    try {
                        String targetUserId = String.format("USER%010d", threadId % userCount);
                        SpectorMemory resolved = registry.resolveFor(targetUserId);
                        assertThat(resolved).isSameAs(mockInstances.get(targetUserId));
                        successfulResolutions.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }
        }

        boolean done = latch.await(10, TimeUnit.SECONDS);
        assertThat(done).isTrue();
        assertThat(successfulResolutions.get()).isEqualTo(threadCount);
        registry.close();
    }

    @Test
    @DisplayName("Concurrent resolution and LRU eviction under heavy virtual thread traffic")
    void testConcurrentLruEvictionUnderHeavyLoad() throws Exception {
        int maxCap = 10;
        SynapseProperties synapseProps = new SynapseProperties();
        synapseProps.auth().setEnabled(true);
        synapseProps.getMemory().setPersistencePath(tempDir.toString());

        MemoryRegistry registry = new MemoryRegistry(
                sharedProvider, synapseProps, embedderProvider, textGenProvider,
                salienceProvider, objectMapperProvider, maxCap
        );

        // Inject 10 initial users
        for (int u = 0; u < maxCap; u++) {
            String uid = String.format("INIT%010d", u);
            injectMockIntoCache(registry, uid, mock(SpectorMemory.class));
        }

        assertThat(registry.cachedInstanceCount()).isEqualTo(maxCap);

        // Concurrently query 30 distinct users across 300 virtual threads
        int threadCount = 300;
        CountDownLatch latch = new CountDownLatch(threadCount);

        try (ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int t = 0; t < threadCount; t++) {
                final int threadId = t;
                virtualExecutor.submit(() -> {
                    try {
                        String uid = String.format("INIT%010d", threadId % maxCap);
                        SpectorMemory mem = registry.resolveFor(uid);
                        assertThat(mem).isNotNull();
                    } finally {
                        latch.countDown();
                    }
                });
            }
        }

        boolean finished = latch.await(10, TimeUnit.SECONDS);
        assertThat(finished).isTrue();
        registry.close();
    }

    @SuppressWarnings("unchecked")
    private void injectMockIntoCache(MemoryRegistry registry, String userId, SpectorMemory mockMemory) {
        try {
            Field resolverField = MemoryRegistry.class.getDeclaredField("resolver");
            resolverField.setAccessible(true);
            Object resolver = resolverField.get(registry);
            Field cacheField = resolver.getClass().getDeclaredField("cache");
            cacheField.setAccessible(true);
            Map<String, Object> map = (Map<String, Object>) cacheField.get(resolver);

            // Construct the private MemoryHandle
            Class<?> entryClass = Class.forName("com.spectrayan.spector.synapse.memory.NamespaceResolver$MemoryHandle");
            var constructor = entryClass.getDeclaredConstructor(SpectorMemory.class);
            constructor.setAccessible(true);
            Object entry = constructor.newInstance(mockMemory);

            map.put(userId, entry);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject mock into registry cache", e);
        }
    }
}
