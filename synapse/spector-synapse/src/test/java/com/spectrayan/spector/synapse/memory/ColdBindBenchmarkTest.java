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
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.synapse.catalog.*;
import com.spectrayan.spector.synapse.config.SynapseProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;

import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("benchmark")
@Disabled("Manual execution for R12 baseline and delta benchmarking")
@DisplayName("Cold-bind baseline and optimization benchmark (R12 / Group 1)")
class ColdBindBenchmarkTest {

    @TempDir
    Path tempDir;

    private AccountCatalog catalog;
    private SynapseProperties synapseProps;
    private ObjectProvider<EmbeddingProvider> embedderProvider;
    private ObjectProvider<ObjectMapper> objectMapperProvider;
    private EmbeddingProvider mockEmbedder;

    private static final String ACCOUNT_ID = "0195500000001";

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        catalog = mock(AccountCatalog.class);
        synapseProps = new SynapseProperties();
        synapseProps.getMemory().setPersistencePath(tempDir.toString());
        synapseProps.getMemory().setDimensions(64);

        mockEmbedder = mock(EmbeddingProvider.class);
        when(mockEmbedder.embed(any(String.class))).thenReturn(
                com.spectrayan.spector.provider.embedding.EmbeddingResult.of(new float[64], "mock"));
        when(mockEmbedder.embedBatch(any())).thenReturn(
                List.of(com.spectrayan.spector.provider.embedding.EmbeddingResult.of(new float[64], "mock")));

        embedderProvider = mock(ObjectProvider.class);
        when(embedderProvider.getIfAvailable()).thenReturn(mockEmbedder);

        objectMapperProvider = mock(ObjectProvider.class);
        when(objectMapperProvider.getIfAvailable(any())).thenReturn(new ObjectMapper());
    }

    private void runColdBindMeasurement(int count) throws Exception {
        Account account = new Account(
                ACCOUNT_ID, PrincipalKind.HUMAN, AccountProfile.HUMAN_SOLO,
                "Benchmark User", new AccountQuotas(count * 2, count * 2, -1, -1),
                new AccountFlags(true, true, true), "ns-0", Instant.now()
        );
        when(catalog.getOrCreateAccount(ACCOUNT_ID)).thenReturn(account);

        for (int i = 0; i < count; i++) {
            String nsId = "ns-" + i;
            NamespaceRecord rec = new NamespaceRecord(
                    nsId, nsId, ACCOUNT_ID, NamespaceType.PROJECT,
                    NamespaceStatus.ACTIVE, nsId, "", null, Instant.now(), null
            );
            when(catalog.resolve(ACCOUNT_ID, nsId)).thenReturn(Optional.of(rec));
        }

        NamespaceResolver resolver = new NamespaceResolver(
                catalog, synapseProps, embedderProvider, null, null,
                objectMapperProvider, null, null, null, null, null, count * 2
        );

        // Force GC before measuring heap
        System.gc();
        Thread.sleep(150);
        System.gc();
        long heapBefore = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        long[] durationsNanos = new long[count];
        for (int i = 0; i < count; i++) {
            String nsId = "ns-" + i;
            long start = System.nanoTime();
            SpectorMemory mem = resolver.resolve(ACCOUNT_ID, nsId);
            durationsNanos[i] = System.nanoTime() - start;
            assertThat(mem).isNotNull();
        }

        System.gc();
        Thread.sleep(150);
        System.gc();
        long heapAfter = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        Arrays.sort(durationsNanos);
        long p50Nanos = durationsNanos[(int) (count * 0.50)];
        int p99Idx = (int) Math.min(count - 1, Math.ceil(count * 0.99) - 1);
        long p99Nanos = durationsNanos[p99Idx];

        double p50Ms = p50Nanos / 1_000_000.0;
        double p99Ms = p99Nanos / 1_000_000.0;
        long totalHeapBytes = Math.max(0, heapAfter - heapBefore);
        long heapPerBindBytes = totalHeapBytes / count;

        System.out.printf("[COLD-BIND-BENCH] Count=%d | p50=%.2f ms | p99=%.2f ms | Heap/bind=%d bytes (Total=%.2f KiB)%n",
                count, p50Ms, p99Ms, heapPerBindBytes, totalHeapBytes / 1024.0);

        resolver.close();
    }

    @Test
    @DisplayName("Measure cold-bind latency and heap at 1, 10, and 100 hot namespaces")
    void measureColdBind() throws Exception {
        runColdBindMeasurement(1);
        runColdBindMeasurement(10);
        runColdBindMeasurement(100);
    }
}
