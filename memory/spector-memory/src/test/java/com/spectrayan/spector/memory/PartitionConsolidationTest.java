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

import com.spectrayan.spector.memory.pathway.*;
import com.spectrayan.spector.memory.persist.*;
import com.spectrayan.spector.memory.assembly.*;

import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.model.CognitiveRecord;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.MemoryPersistenceMode;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.model.ReflectReport;
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
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verification test suite for Issue #446: Multi-Partition Consolidation, Reflection, and Decay.
 */
@DisplayName("Issue #446 — Multi-Partition Consolidation, Reflection, and Decay")
class PartitionConsolidationTest {

    private static final int DIMENSIONS = 32;
    private SpectorMemory memory;
    private TestEmbeddingProvider embeddingProvider;
    private MockLlmProvider llmProvider;

    private SpectorMemory build(Path dir, int semanticCap) {
        embeddingProvider = new TestEmbeddingProvider(DIMENSIONS);
        llmProvider = new MockLlmProvider();

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
    @DisplayName("Batch consolidation detects and merges duplicate memories straddling frozen and active partitions")
    void crossPartitionDuplicateConsolidation(@TempDir Path dir) {
        memory = build(dir, /*semanticCap*/ 2);

        float[] vector = new float[DIMENSIONS];
        vector[0] = 1.0f;

        String text0 = "The primary database host is db-primary.internal";
        String text1 = "Authentication service token TTL is 3600 seconds";
        String text2 = "The primary database host is db-primary.internal replica";

        embeddingProvider.register(text0, vector);
        embeddingProvider.register(text2, vector);

        // Partition 000 (capacity 2): fill semantic store
        memory.remember("sem-0", text0, MemoryType.SEMANTIC, MemorySource.USER_STATED, "infra");
        memory.remember("sem-1", text1, MemoryType.SEMANTIC, MemorySource.USER_STATED, "infra");

        // Overflow semantic tier (cap 2) -> triggers roll to active Partition
        memory.remember("sem-2", text2, MemoryType.SEMANTIC, MemorySource.USER_STATED, "infra");

        CognitiveRecord rec0Before = memory.inspect("sem-0");
        CognitiveRecord rec2Before = memory.inspect("sem-2");
        assertThat(rec0Before).isNotNull();
        assertThat(rec2Before).isNotNull();
        assertThat(rec0Before.partitionIndex()).isEqualTo(0);
        assertThat(rec2Before.partitionIndex()).isGreaterThan(0);
        assertThat(rec0Before.isTombstoned()).isFalse();
        assertThat(rec2Before.isTombstoned()).isFalse();

        // Run batch consolidation across frozen partition 000 and active partition
        memory.consolidate();

        // Both original memories across partitions should be tombstoned and de-indexed
        assertThat(memory.inspect("sem-0")).isNull();
        assertThat(memory.inspect("sem-2")).isNull();

        // Merged memory should be recallable
        List<CognitiveResult> results = memory.recall(text0, RecallOptions.builder().topK(10).build());
        assertThat(results).isNotEmpty();
    }

    @Test
    @DisplayName("CADP contradiction resolution marks loser contradicted in frozen partition in-place")
    void crossPartitionCadpContradictionResolution(@TempDir Path dir) throws Exception {
        memory = build(dir, /*semanticCap*/ 2);

        float[] vector = new float[DIMENSIONS];
        vector[5] = 1.0f;

        String textOld = "The api gateway port is 8080";
        String textOther = "Redis cache TTL is 300 seconds";
        String textNew = "The api gateway port is 9090";

        embeddingProvider.register(textOld, vector);
        embeddingProvider.register(textNew, vector);

        llmProvider.registerResponse(" 8080", "YES");
        llmProvider.registerResponse(" 9090", "YES");

        // Partition 000: initial facts
        memory.remember("port-old", textOld, MemoryType.SEMANTIC, MemorySource.USER_STATED, "config");
        memory.remember("cache-ttl", textOther, MemoryType.SEMANTIC, MemorySource.USER_STATED, "config");

        Thread.sleep(10);

        // Overflow to active Partition with contradictory statement
        memory.remember("port-new", textNew, MemoryType.SEMANTIC, MemorySource.USER_STATED, "config");

        CognitiveRecord oldRec = memory.inspect("port-old");
        CognitiveRecord newRec = memory.inspect("port-new");
        assertThat(oldRec).isNotNull();
        assertThat(newRec).isNotNull();
        assertThat(oldRec.partitionIndex()).isEqualTo(0);
        assertThat(newRec.partitionIndex()).isGreaterThan(0);

        // Run consolidation to trigger CADP
        memory.consolidate();

        CognitiveRecord oldRecAfter = memory.inspect("port-old");
        CognitiveRecord newRecAfter = memory.inspect("port-new");

        // Older memory in frozen partition should be marked contradicted in-place
        assertThat(oldRecAfter).isNotNull();
        assertThat(oldRecAfter.isContradicted())
                .as("Older memory in frozen partition must be marked contradicted")
                .isTrue();
        assertThat(newRecAfter).isNotNull();
        assertThat(newRecAfter.isContradicted())
                .as("Newer memory in active partition must not be marked contradicted")
                .isFalse();
    }

    @Test
    @DisplayName("Reflection cycle executes across multi-partition deployment without errors")
    void crossPartitionReflectionCycle(@TempDir Path dir) {
        memory = build(dir, /*semanticCap*/ 2);

        memory.remember("sem-0", "Microservice A communicates via gRPC",
                MemoryType.SEMANTIC, MemorySource.USER_STATED, "arch");
        memory.remember("sem-1", "Microservice B communicates via REST",
                MemoryType.SEMANTIC, MemorySource.USER_STATED, "arch");

        // Roll partition
        memory.remember("sem-2", "Microservice C communicates via Kafka",
                MemoryType.SEMANTIC, MemorySource.USER_STATED, "arch");

        // Run reflection orchestrator
        ReflectReport report = memory.reflect();

        assertThat(report).isNotNull();
        assertThat(report.duration()).isNotNull();
    }

    static class TestEmbeddingProvider implements EmbeddingProvider {
        private final int dims;
        private final Map<String, float[]> presetVectors = new HashMap<>();

        TestEmbeddingProvider(int dims) {
            this.dims = dims;
        }

        void register(String text, float[] vector) {
            presetVectors.put(text, vector);
        }

        @Override
        public EmbeddingResult embed(String text) {
            float[] vec = presetVectors.get(text);
            if (vec == null) {
                vec = new float[dims];
                Random rand = new Random(text.hashCode());
                float normSq = 0;
                for (int i = 0; i < dims; i++) {
                    vec[i] = (rand.nextFloat() - 0.5f);
                    normSq += vec[i] * vec[i];
                }
                float norm = (float) Math.sqrt(normSq);
                for (int i = 0; i < dims; i++) {
                    vec[i] /= norm;
                }
            }
            return new EmbeddingResult(vec, 10, "test-embedder");
        }

        @Override
        public int dimensions() {
            return dims;
        }

        @Override
        public String modelName() {
            return "test-embedder";
        }
    }

    static class MockLlmProvider implements LlmProvider {
        private final Map<String, String> responses = new HashMap<>();

        void registerResponse(String keyword, String reply) {
            responses.put(keyword, reply);
        }

        @Override
        public LlmResponse generate(LlmRequest request, GenerationOptions options) {
            String prompt = request.messages().isEmpty() ? "" : request.messages().get(0).text();
            if (prompt.contains("Merge these")) {
                return new LlmResponse("The primary database host is db-primary.internal replica", 10, 10, "mock-llm");
            }
            String foundReply = "NO";
            for (Map.Entry<String, String> entry : responses.entrySet()) {
                if (prompt.contains(entry.getKey())) {
                    foundReply = entry.getValue();
                    break;
                }
            }
            return new LlmResponse(foundReply, 10, 10, "mock-llm");
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
