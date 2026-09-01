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
package com.spectrayan.spector.memory;

import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.MemoryPersistenceMode;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.synapse.SynapticTagEncoder;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.embedding.EmbeddingResult;
import com.spectrayan.spector.provider.generation.GenerationOptions;
import com.spectrayan.spector.provider.generation.LlmProvider;
import com.spectrayan.spector.provider.model.LlmRequest;
import com.spectrayan.spector.provider.model.LlmResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration tests for query-time partition pruning across frozen and active partitions (#447).
 */
class PartitionPruningIntegrationTest {

    private static final int DIMENSIONS = 32;
    private SpectorMemory memory;
    private TestEmbeddingProvider embeddingProvider;

    private SpectorMemory build(Path dir, int semanticCap) {
        embeddingProvider = new TestEmbeddingProvider(DIMENSIONS);
        MockLlmProvider llmProvider = new MockLlmProvider();

        return DefaultSpectorMemory.builder()
                .dimensions(DIMENSIONS)
                .embeddingProvider(embeddingProvider)
                .LlmProvider(llmProvider)
                .persistenceMode(MemoryPersistenceMode.DISK)
                .persistence(dir)
                .workingCapacity(32)
                .episodicPartitionCapacity(16)
                .semanticCapacity(semanticCap)
                .proceduralCapacity(32)
                .surpriseWarmup(1)
                .build();
    }

    @AfterEach
    void tearDown() {
        if (memory != null) {
            memory.close();
            memory = null;
        }
    }

    @Test
    @DisplayName("Query-time partition pruning skips partitions with non-overlapping tag masks")
    void tagFilterPruningAcrossPartitions(@TempDir Path dir) {
        // Build with capacity 2 to force partition rolls every 2 semantic records
        memory = build(dir, /*semanticCap*/ 2);

        float[] vDb = new float[DIMENSIONS]; vDb[0] = 1.0f;
        float[] vUi = new float[DIMENSIONS]; vUi[1] = 1.0f;
        float[] vSec = new float[DIMENSIONS]; vSec[2] = 1.0f;

        String tDb1 = "Postgres connection pool max size is 100";
        String tDb2 = "Postgres replication lag is under 10ms";
        String tUi1 = "Dashboard layout uses 12-column CSS grid";
        String tUi2 = "Color theme defaults to dark mode";
        String tSec1 = "JWT signing key rotation cycle is 30 days";

        embeddingProvider.register(tDb1, vDb);
        embeddingProvider.register(tDb2, vDb);
        embeddingProvider.register(tUi1, vUi);
        embeddingProvider.register(tUi2, vUi);
        embeddingProvider.register(tSec1, vSec);
        embeddingProvider.register("layout and theme", vUi);
        embeddingProvider.register("database pool connection", vDb);
        embeddingProvider.register("jwt rotation cycle", vSec);
        embeddingProvider.register("database and jwt", vDb);

        // Partition 000: database tag
        memory.remember("db-1", tDb1, MemoryType.SEMANTIC, MemorySource.USER_STATED, "database");
        memory.remember("db-2", tDb2, MemoryType.SEMANTIC, MemorySource.USER_STATED, "database");

        // Partition 001: frontend tag (causes roll from 000)
        memory.remember("ui-1", tUi1, MemoryType.SEMANTIC, MemorySource.USER_STATED, "frontend");
        memory.remember("ui-2", tUi2, MemoryType.SEMANTIC, MemorySource.USER_STATED, "frontend");

        // Partition 002: security tag (causes roll from 001)
        memory.remember("sec-1", tSec1, MemoryType.SEMANTIC, MemorySource.USER_STATED, "security");

        // 1. Recall for "frontend" tags only (lives in partition 001)
        RecallOptions uiOpts = RecallOptions.builder()
                .topK(10)
                .hyperfocusMask("frontend")
                .build();

        List<CognitiveResult> uiResults = memory.recall("layout and theme", uiOpts);
        assertThat(uiResults).isNotEmpty();
        for (CognitiveResult res : uiResults) {
            assertThat(res.id()).startsWith("ui-");
        }

        // 2. Recall for "database" tags only (lives in frozen partition 000)
        RecallOptions dbOpts = RecallOptions.builder()
                .topK(10)
                .hyperfocusMask("database")
                .build();

        List<CognitiveResult> dbResults = memory.recall("database pool connection", dbOpts);
        assertThat(dbResults).isNotEmpty();
        for (CognitiveResult res : dbResults) {
            assertThat(res.id()).startsWith("db-");
        }

        // 3. Recall for "security" tags only (lives in active partition 002)
        RecallOptions secOpts = RecallOptions.builder()
                .topK(10)
                .hyperfocusMask("security")
                .build();

        List<CognitiveResult> secResults = memory.recall("jwt rotation cycle", secOpts);
        assertThat(secResults).isNotEmpty();
        for (CognitiveResult res : secResults) {
            assertThat(res.id()).startsWith("sec-");
        }

        // 4. Unfiltered recall finds memories across all partitions
        RecallOptions allOpts = RecallOptions.builder()
                .topK(10)
                .build();

        List<CognitiveResult> allResults = memory.recall("database and jwt", allOpts);
        assertThat(allResults).isNotEmpty();
    }

    @Test
    @DisplayName("Hyperfocus query pruning skips partitions missing any required focus tags")
    void hyperfocusPruningAcrossPartitions(@TempDir Path dir) {
        memory = build(dir, /*semanticCap*/ 2);

        float[] vec = new float[DIMENSIONS]; vec[0] = 1.0f;

        String t1 = "Production cluster deployment runbook";
        String t2 = "Staging cluster deployment runbook";
        String t3 = "Development cluster setup notes";

        embeddingProvider.register(t1, vec);
        embeddingProvider.register(t2, vec);
        embeddingProvider.register(t3, vec);
        embeddingProvider.register("deployment runbook", vec);

        // Partition 000: tagged with "infra" and "production"
        memory.remember("p-1", t1, MemoryType.SEMANTIC, MemorySource.USER_STATED, "infra", "production");
        memory.remember("p-2", t1, MemoryType.SEMANTIC, MemorySource.USER_STATED, "infra", "production");

        // Partition 001: tagged with "infra" only (missing "production")
        memory.remember("s-1", t2, MemoryType.SEMANTIC, MemorySource.USER_STATED, "infra");
        memory.remember("s-2", t3, MemoryType.SEMANTIC, MemorySource.USER_STATED, "infra");

        long hyperfocusMask = SynapticTagEncoder.encode("infra", "production");
        RecallOptions hfOpts = RecallOptions.builder()
                .topK(10)
                .hyperfocusMask(hyperfocusMask)
                .build();

        List<CognitiveResult> results = memory.recall("deployment runbook", hfOpts);
        assertThat(results).isNotEmpty();
        for (CognitiveResult res : results) {
            assertThat(res.id()).startsWith("p-");
        }
    }

    @Test
    @DisplayName("Temporal window pruning skips partitions whose timestamp ranges fall outside the query window")
    void temporalWindowPruningAcrossPartitions(@TempDir Path dir) {
        memory = build(dir, /*semanticCap*/ 2);

        float[] vec = new float[DIMENSIONS]; vec[0] = 1.0f;

        String t1 = "Quarter 1 financial report";
        String t2 = "Quarter 2 financial report";
        String t3 = "Quarter 3 financial report";

        embeddingProvider.register(t1, vec);
        embeddingProvider.register(t2, vec);
        embeddingProvider.register(t3, vec);
        embeddingProvider.register("financial report", vec);

        long now = System.currentTimeMillis();

        // Partition 000
        memory.remember("q-1", t1, MemoryType.SEMANTIC, MemorySource.USER_STATED, "finance");
        memory.remember("q-2", t2, MemoryType.SEMANTIC, MemorySource.USER_STATED, "finance");

        // Partition 001 (triggers roll)
        memory.remember("q-3", t3, MemoryType.SEMANTIC, MemorySource.USER_STATED, "finance");

        // Query with maxTimestamp before Partition 000 was created -> should prune
        RecallOptions oldOpts = RecallOptions.builder()
                .topK(10)
                .maxTimestamp(now - 100_000L)
                .build();

        List<CognitiveResult> emptyResults = memory.recall("financial report", oldOpts);
        assertThat(emptyResults).isEmpty();

        // Query with minTimestamp in the future -> should prune
        RecallOptions futureOpts = RecallOptions.builder()
                .topK(10)
                .minTimestamp(now + 1_000_000L)
                .build();

        List<CognitiveResult> futureResults = memory.recall("financial report", futureOpts);
        assertThat(futureResults).isEmpty();

        // Query with valid window overlapping current time
        RecallOptions validOpts = RecallOptions.builder()
                .topK(10)
                .minTimestamp(now - 10_000L)
                .maxTimestamp(now + 10_000L)
                .build();

        List<CognitiveResult> validResults = memory.recall("financial report", validOpts);
        assertThat(validResults).isNotEmpty();
    }

    // ── Test Doubles ──

    static class TestEmbeddingProvider implements EmbeddingProvider {
        private final int dims;
        private final Map<String, float[]> registry = new HashMap<>();

        TestEmbeddingProvider(int dims) { this.dims = dims; }

        void register(String text, float[] vector) {
            registry.put(text, vector);
        }

        @Override
        public EmbeddingResult embed(String text) {
            float[] vec = registry.get(text);
            if (vec == null) {
                vec = new float[dims];
                int h = text.hashCode();
                for (int i = 0; i < dims; i++) {
                    vec[i] = (float) Math.sin(h * (i + 1));
                }
                float norm = 0f;
                for (float v : vec) norm += v * v;
                norm = (float) Math.sqrt(norm);
                if (norm > 1e-6f) {
                    for (int i = 0; i < dims; i++) vec[i] /= norm;
                }
            }
            return new EmbeddingResult(vec, text.split("\\s+").length, "test");
        }

        @Override public int dimensions() { return dims; }
        @Override public String modelName() { return "test"; }
    }

    static class MockLlmProvider implements LlmProvider {
        @Override
        public LlmResponse generate(LlmRequest request, GenerationOptions options) {
            return new LlmResponse("Consolidated response", 10, 10, "mock-llm");
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public String modelName() {
            return "mock-llm";
        }
    }
}
