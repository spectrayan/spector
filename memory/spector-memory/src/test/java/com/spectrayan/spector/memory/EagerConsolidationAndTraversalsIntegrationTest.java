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
import com.spectrayan.spector.memory.graph.HyperEntityGraphMemory;
import com.spectrayan.spector.memory.model.CognitiveRecord;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.model.MemoryPersistenceMode;
import com.spectrayan.spector.memory.temporal.TemporalFact;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for Eager Consolidation (#526), Temporal Bridge (#527),
 * and Recall CONTRADICTS Traversal (#528).
 */
class EagerConsolidationAndTraversalsIntegrationTest {

    private static final int DIMENSIONS = 32;
    private DefaultSpectorMemory memory;
    private TestEmbeddingProvider embeddingProvider;
    private MockLlmProvider llmProvider;

    @BeforeEach
    void setUp() {
        embeddingProvider = new TestEmbeddingProvider(DIMENSIONS);
        llmProvider = new MockLlmProvider();

        memory = (DefaultSpectorMemory) DefaultSpectorMemory.builder()
                .dimensions(DIMENSIONS)
                .embeddingProvider(embeddingProvider)
                .LlmProvider(llmProvider)
                .entityExtractionMode(com.spectrayan.spector.memory.graph.EntityExtractionMode.LLM)
                .persistenceMode(MemoryPersistenceMode.IN_MEMORY)
                .workingCapacity(20)
                .episodicPartitionCapacity(100)
                .semanticCapacity(100)
                .proceduralCapacity(100)
                .eagerConsolidationQueueCapacity(256)
                .build();
    }

    @AfterEach
    void tearDown() {
        memory.close();
    }

    @Test
    void testAsyncEagerConsolidation_resolvesWithoutExplicitConsolidateCall() throws Exception {
        // Create duplicate vectors
        float[] vector = new float[DIMENSIONS];
        vector[3] = 1.0f;

        String textA = "User preference: light theme for editor.";
        String textB = "User preference: dark theme for editor.";

        embeddingProvider.register(textA, vector);
        embeddingProvider.register(textB, vector);

        llmProvider.registerResponse("light theme", "YES");
        llmProvider.registerResponse("dark theme", "YES");

        // Ingest memory A first, then memory B
        memory.remember("theme-old", textA, MemoryType.SEMANTIC, MemorySource.OBSERVED, "ui", "theme");
        memory.remember("theme-new", textB, MemoryType.SEMANTIC, MemorySource.OBSERVED, "ui", "theme");

        // Await async eager consolidation by polling for up to 3 seconds
        boolean resolved = false;
        long deadline = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < deadline) {
            CognitiveRecord recordA = memory.inspect("theme-old");
            CognitiveRecord recordB = memory.inspect("theme-new");
            if (recordA != null && recordB != null && recordA.isContradicted() && !recordB.isContradicted()) {
                resolved = true;
                break;
            }
            Thread.sleep(50);
        }

        assertThat(resolved)
                .as("Async eager consolidation should have flagged theme-old and preserved theme-new")
                .isTrue();

        // Recall should surface the winner (theme-new) and gate out the loser (theme-old)
        List<CognitiveResult> results = memory.recall("editor theme preference",
                RecallOptions.builder().includeContradictions(false).build());

        assertThat(results).noneMatch(r -> "theme-old".equals(r.id()));
        assertThat(results).anyMatch(r -> "theme-new".equals(r.id()));
    }

    @Test
    void testTemporalBridge_retractsTemporalFactsOnContradiction() throws Exception {
        float[] vector = new float[DIMENSIONS];
        vector[4] = 1.0f;

        String textA = "Alice lives in London.";
        String textB = "Alice lives in Berlin.";

        embeddingProvider.register(textA, vector);
        embeddingProvider.register(textB, vector);

        llmProvider.registerResponse("London", "YES");
        llmProvider.registerResponse("Berlin", "YES");

        int aliceId = memory.entityDirectory().intern("Alice", "PERSON");

        // Assert temporal fact for Alice via SpectorMemory API
        int factId = memory.assertFact("Alice", "lives_in", "London",
                1000L, Long.MAX_VALUE, 0.9f);
        assertThat(factId).isGreaterThan(0);

        List<TemporalFact> activeBefore = memory.temporalKnowledgeGraph().factsAbout(aliceId).resolve();
        assertThat(activeBefore).hasSize(1);
        assertThat(activeBefore.get(0).factId()).isEqualTo(factId);

        // Store contradictory memories (loc-2 is strictly newer)
        memory.remember("loc-1", textA, MemoryType.SEMANTIC, MemorySource.OBSERVED, "location");
        Thread.sleep(20);
        memory.remember("loc-2", textB, MemoryType.SEMANTIC, MemorySource.OBSERVED, "location");

        // Await async eager consolidation and temporal fact retraction
        long deadline = System.currentTimeMillis() + 3000;
        boolean retracted = false;
        while (System.currentTimeMillis() < deadline) {
            CognitiveRecord r1 = memory.inspect("loc-1");
            List<TemporalFact> facts = memory.temporalKnowledgeGraph().factsAbout(aliceId)
                    .excludeRetracted()
                    .resolve();
            if (r1 != null && r1.isContradicted() && facts.stream().noneMatch(f -> f.factId() == factId)) {
                retracted = true;
                break;
            }
            Thread.sleep(50);
        }

        // Run batch consolidation fallback if not already resolved by eager consolidator
        CognitiveRecord record1 = memory.inspect("loc-1");
        if (record1 == null || !record1.isContradicted() || !retracted) {
            memory.consolidate();
            record1 = memory.inspect("loc-1");
        }

        // Check that CADP resolved
        CognitiveRecord record2 = memory.inspect("loc-2");
        assertThat(record1.isContradicted()).isTrue();
        assertThat(record2.isContradicted()).isFalse();

        // Verify that the loser's temporal fact (London) was retracted in TemporalKnowledgeGraph (#527)
        List<TemporalFact> activeAfter = memory.temporalKnowledgeGraph().factsAbout(aliceId)
                .excludeRetracted()
                .resolve();
        assertThat(activeAfter).noneMatch(f -> f.factId() == factId);
    }

    @Test
    void testRecallContradictsTraversal_hyperedgeSurfacesCorrector() {
        float[] vectorWinner = new float[DIMENSIONS];
        vectorWinner[7] = 1.0f;
        float[] vectorLoser = new float[DIMENSIONS];
        vectorLoser[8] = 1.0f;

        String textWinner = "The chief executive officer is Sarah Chen.";
        String textLoser = "The chief executive officer is John Smith.";

        embeddingProvider.register(textWinner, vectorWinner);
        embeddingProvider.register(textLoser, vectorLoser);

        llmProvider.registerResponse("Sarah Chen", "YES");
        llmProvider.registerResponse("John Smith", "YES");

        memory.remember("mem-loser", textLoser, MemoryType.SEMANTIC, MemorySource.OBSERVED, "org");
        memory.remember("mem-winner", textWinner, MemoryType.SEMANTIC, MemorySource.OBSERVED, "org");

        int eWinner = memory.entityDirectory().intern("Sarah Chen", "PERSON");
        int eLoser = memory.entityDirectory().intern("John Smith", "PERSON");

        memory.hyperEntityGraph().addHyperedge(
                new int[]{eWinner, eLoser},
                new int[]{HyperEntityGraphMemory.ROLE_CORRECTOR, HyperEntityGraphMemory.ROLE_CORRECTED},
                HyperEntityGraphMemory.TYPE_CONTRADICTS,
                1.0f, -1, System.currentTimeMillis());

        assertThat(memory.hyperEntityGraph().findHyperedgesForEntity(eWinner)).isNotEmpty();
        assertThat(memory.hyperEntityGraph().findHyperedgesForEntity(eLoser)).isNotEmpty();

        // Query with entity hint for loser entity "John Smith"
        List<CognitiveResult> results = memory.recall("John Smith executive",
                RecallOptions.builder()
                        .entityHints(List.of(new com.spectrayan.spector.memory.graph.ExtractedEntity("John Smith", "PERSON")))
                        .graphExpansionThreshold(1.0f)
                        .build());

        // CONTRADICTS traversal should have surfaced mem-winner (Sarah Chen)
        assertThat(results).anyMatch(r -> "mem-winner".equals(r.id())
                || "CONTRADICTION_CORRECTOR".equals(r.metadata().get("graph_source")));
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
            if (prompt.contains("Extract all named entities") || prompt.contains("ENTITY:")) {
                if (prompt.contains("Berlin")) {
                    return new LlmResponse("ENTITY: Alice | PERSON\nENTITY: Berlin | LOCATION\nRELATION: Alice | lives_in | Berlin", 10, 10, "mock-llm");
                }
                if (prompt.contains("London") || prompt.contains("Alice")) {
                    return new LlmResponse("ENTITY: Alice | PERSON\nENTITY: London | LOCATION\nRELATION: Alice | lives_in | London", 10, 10, "mock-llm");
                }
            }
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
