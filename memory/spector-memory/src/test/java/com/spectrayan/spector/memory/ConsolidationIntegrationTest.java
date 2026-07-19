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
import com.spectrayan.spector.memory.model.CognitiveRecord;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.model.MemoryPersistenceMode;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.embedding.EmbeddingResult;
import com.spectrayan.spector.provider.generation.GenerationOptions;
import com.spectrayan.spector.provider.generation.LlmProvider;
import com.spectrayan.spector.provider.model.LlmRequest;
import com.spectrayan.spector.provider.model.LlmResponse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for memory consolidation.
 */
class ConsolidationIntegrationTest {

    private static final int DIMENSIONS = 32;
    private SpectorMemory memory;
    private TestEmbeddingProvider embeddingProvider;
    private MockLlmProvider llmProvider;

    @BeforeEach
    void setUp() {
        embeddingProvider = new TestEmbeddingProvider(DIMENSIONS);
        llmProvider = new MockLlmProvider();

        memory = DefaultSpectorMemory.builder()
                .dimensions(DIMENSIONS)
                .embeddingProvider(embeddingProvider)
                .LlmProvider(llmProvider)
                .persistenceMode(MemoryPersistenceMode.IN_MEMORY)
                .workingCapacity(20)
                .episodicPartitionCapacity(100)
                .semanticCapacity(100)
                .proceduralCapacity(100)
                .build();
    }

    @AfterEach
    void tearDown() {
        memory.close();
    }

    @Test
    void testMergeNonContradictoryDuplicates() throws Exception {
        // Create duplicate vectors
        float[] vector = new float[DIMENSIONS];
        vector[0] = 1.0f; // simple unit vector

        String textA = "User prefers light theme.";
        String textB = "User likes light theme UI.";

        embeddingProvider.register(textA, vector);
        embeddingProvider.register(textB, vector);

        // Store them in the SEMANTIC tier
        memory.remember("dup-a", textA, MemoryType.SEMANTIC, MemorySource.OBSERVED, "ui").get(5, TimeUnit.SECONDS);
        memory.remember("dup-b", textB, MemoryType.SEMANTIC, MemorySource.OBSERVED, "theme").get(5, TimeUnit.SECONDS);

        assertThat(memory.memoryCount(MemoryType.SEMANTIC)).isEqualTo(2);

        // Run consolidation
        memory.consolidate();

        // The original memories should be tombstoned/removed from the index
        assertThat(memory.inspect("dup-a")).isNull();
        assertThat(memory.inspect("dup-b")).isNull();

        // A new consolidated memory should be created
        List<CognitiveResult> results = memory.recall("light theme", RecallOptions.builder().topK(10).build());
        assertThat(results).isNotEmpty();

        CognitiveResult merged = results.get(0);
        assertThat(merged.id()).startsWith("cns-");
        assertThat(merged.text()).contains("light theme");
    }

    @Test
    void testContradictoryDuplicatesFlagged() throws Exception {
        // Create duplicate vectors
        float[] vector = new float[DIMENSIONS];
        vector[5] = 1.0f;

        String textA = "The capital of France is Paris.";
        String textB = "The capital of France is Lyon.";

        embeddingProvider.register(textA, vector);
        embeddingProvider.register(textB, vector);

        // Setup LLM mock to return YES for contradiction check
        llmProvider.registerResponse(" Paris", "YES");
        llmProvider.registerResponse(" Lyon", "YES");

        // Store in SEMANTIC tier
        memory.remember("fact-a", textA, MemoryType.SEMANTIC, MemorySource.OBSERVED, "geography").get(5, TimeUnit.SECONDS);
        memory.remember("fact-b", textB, MemoryType.SEMANTIC, MemorySource.OBSERVED, "geography").get(5, TimeUnit.SECONDS);

        // Run consolidation
        memory.consolidate();

        // Verify that the original memories still exist, but they are flagged as contradicted
        CognitiveRecord recordA = memory.inspect("fact-a");
        CognitiveRecord recordB = memory.inspect("fact-b");

        assertThat(recordA).isNotNull();
        assertThat(recordB).isNotNull();
        assertThat(recordA.isContradicted()).isTrue();
        assertThat(recordB.isContradicted()).isTrue();

        // Standard recall should filter/gate them out
        List<CognitiveResult> standardRecall = memory.recall("capital of France", RecallOptions.builder().includeContradictions(false).build());
        assertThat(standardRecall).noneMatch(r -> "fact-a".equals(r.id()) || "fact-b".equals(r.id()));

        // Recall with includeContradictions(true) should return them
        List<CognitiveResult> recallWithContradictions = memory.recall("capital of France", RecallOptions.builder().includeContradictions(true).build());
        assertThat(recallWithContradictions).anyMatch(r -> "fact-a".equals(r.id()));
        assertThat(recallWithContradictions).anyMatch(r -> "fact-b".equals(r.id()));
    }

    static class TestEmbeddingProvider implements EmbeddingProvider {
        private final int dims;
        private final Map<String, float[]> presetVectors = new HashMap<>();

        TestEmbeddingProvider(int dims) {
            this.dims = dims;
        }

        void register(String text, float[] vec) {
            presetVectors.put(text, vec);
        }

        @Override
        public EmbeddingResult embed(String text) {
            float[] vec = presetVectors.get(text);
            if (vec == null) {
                Random rng = new Random(text.hashCode());
                vec = new float[dims];
                for (int i = 0; i < dims; i++) {
                    vec[i] = (rng.nextFloat() - 0.5f) * 2.0f;
                }
                float norm = 0f;
                for (float v : vec) norm += v * v;
                norm = (float) Math.sqrt(norm);
                if (norm > 0) {
                    for (int i = 0; i < dims; i++) vec[i] /= norm;
                }
            }
            return new EmbeddingResult(vec, text.split("\\s+").length, "test");
        }

        @Override public int dimensions() { return dims; }
        @Override public String modelName() { return "test"; }
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
                return new LlmResponse("User prefers light theme UI.", 10, 10, "mock-llm");
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
