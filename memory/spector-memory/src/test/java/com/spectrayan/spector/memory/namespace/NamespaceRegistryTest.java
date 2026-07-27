/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-memory/LICENSE
 *
 * Change Date: May 27, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.memory.namespace;

import static org.assertj.core.api.Assertions.assertThat;

import com.spectrayan.spector.memory.DefaultSpectorMemory;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.model.MemoryPersistenceMode;
import com.spectrayan.spector.memory.test.FakeEmbeddingProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

@DisplayName("NamespaceRegistry Tests")
class NamespaceRegistryTest {

    private FakeEmbeddingProvider embedProvider;
    private NamespaceRegistry registry;

    @BeforeEach
    void setUp() {
        embedProvider = new FakeEmbeddingProvider();
    }

    @AfterEach
    void tearDown() {
        if (registry != null) {
            registry.close();
        }
    }

    private SpectorMemory createTestMemory(String namespaceId) {
        return DefaultSpectorMemory.builder()
                .dimensions(embedProvider.dimensions())
                .embeddingProvider(embedProvider)
                .persistenceMode(MemoryPersistenceMode.IN_MEMORY)
                .namespaceId(namespaceId)
                .managedByRegistry(true)
                .build();
    }

    @Test
    @DisplayName("Verify LRU Eviction behavior")
    void testLruEviction() {
        registry = new NamespaceRegistry(2);

        SpectorMemory ns1 = registry.getOrOpen("ns1", () -> createTestMemory("ns1"));
        SpectorMemory ns2 = registry.getOrOpen("ns2", () -> createTestMemory("ns2"));

        assertThat(registry.activeCount()).isEqualTo(2);
        assertThat(registry.evictedCount()).isEqualTo(0);

        // Touch ns1 to make it most recently used
        registry.getOrOpen("ns1", () -> createTestMemory("ns1"));

        // Open ns3, which should trigger eviction of ns2 (since ns1 was touched)
        registry.getOrOpen("ns3", () -> createTestMemory("ns3"));

        assertThat(registry.activeCount()).isEqualTo(2);
        assertThat(registry.evictedCount()).isEqualTo(1);

        // Check that ns2 is no longer tracked
        SpectorMemory cachedNs2 = registry.getOrOpen("ns2", () -> {
            // Should call opener to reopen
            return createTestMemory("ns2");
        });

        // Now ns1 (oldest MRU) should be evicted
        assertThat(registry.evictedCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("Verify active lease prevents eviction")
    void testLeasePreventsEviction() {
        registry = new NamespaceRegistry(2);

        SpectorMemory ns1 = registry.getOrOpen("ns1", () -> createTestMemory("ns1"));
        SpectorMemory ns2 = registry.getOrOpen("ns2", () -> createTestMemory("ns2"));

        // Acquire lease on ns1
        ((DefaultSpectorMemory) ns1).acquireLease();

        // Open ns3. Normally ns1 (eldest) is evicted, but since it has a lease, ns2 should be evicted instead
        registry.getOrOpen("ns3", () -> createTestMemory("ns3"));

        assertThat(registry.activeCount()).isEqualTo(2);
        assertThat(registry.evictedCount()).isEqualTo(1);

        // Verify ns1 is still active
        assertThat(registry.getOrOpen("ns1", () -> {
            throw new AssertionError("Should not reopen ns1 as it must still be active");
        })).isSameAs(ns1);

        // Release lease
        ((DefaultSpectorMemory) ns1).releaseLease();
    }

    @Test
    @DisplayName("Verify warm reopen logic")
    void testWarmReopen() {
        registry = new NamespaceRegistry(1);
        AtomicInteger openCount = new AtomicInteger(0);

        SpectorMemory ns1 = registry.getOrOpen("ns1", () -> {
            openCount.incrementAndGet();
            return createTestMemory("ns1");
        });

        // Trigger eviction of ns1
        registry.getOrOpen("ns2", () -> createTestMemory("ns2"));
        assertThat(registry.evictedCount()).isEqualTo(1);

        // Touch ns1 again, should reload it
        SpectorMemory ns1Reloaded = registry.getOrOpen("ns1", () -> {
            openCount.incrementAndGet();
            return createTestMemory("ns1");
        });

        assertThat(openCount.get()).isEqualTo(2);
        assertThat(ns1Reloaded).isNotSameAs(ns1);
    }

    @Test
    @DisplayName("Verify FD diagnostics executes without failure")
    void testFdDiagnostics() {
        NamespaceRegistry.logFdDiagnostics(10);
        NamespaceRegistry.logFdDiagnostics(1000000); // Trigger warning if Unix MXBean is available
    }
}
