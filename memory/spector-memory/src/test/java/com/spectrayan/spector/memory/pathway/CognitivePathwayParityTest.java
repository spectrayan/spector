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
package com.spectrayan.spector.memory.pathway;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

import com.spectrayan.spector.memory.DefaultSpectorMemory;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.model.CognitiveProfile;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.MemoryPersistenceMode;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.RecallMode;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.embedding.EmbeddingResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * End-to-end parity test comparing legacy {@link com.spectrayan.spector.memory.pipeline.RecallPipeline}
 * and {@link com.spectrayan.spector.memory.pipeline.CognitiveIngestionTarget} against the new
 * {@link com.spectrayan.spector.memory.pathway.RecallPathway} and {@link com.spectrayan.spector.memory.pathway.RememberPathway}.
 */
@DisplayName("CognitivePathwayParityTest")
class CognitivePathwayParityTest {

    private static final int DIMENSIONS = 32;

    private SpectorMemory legacyMemory;
    private SpectorMemory pathwayMemory;

    @BeforeEach
    void setUp() {
        final EmbeddingProvider provider = new MockEmbeddingProvider(DIMENSIONS);

        legacyMemory = DefaultSpectorMemory.builder()
                .dimensions(DIMENSIONS)
                .embeddingProvider(provider)
                .persistenceMode(MemoryPersistenceMode.IN_MEMORY)
                .workingCapacity(50)
                .episodicPartitionCapacity(100)
                .semanticCapacity(100)
                .proceduralCapacity(100)
                .usePathwayEngine(false)
                .build();

        pathwayMemory = DefaultSpectorMemory.builder()
                .dimensions(DIMENSIONS)
                .embeddingProvider(provider)
                .persistenceMode(MemoryPersistenceMode.IN_MEMORY)
                .workingCapacity(50)
                .episodicPartitionCapacity(100)
                .semanticCapacity(100)
                .proceduralCapacity(100)
                .usePathwayEngine(true)
                .build();
    }

    @AfterEach
    void tearDown() {
        if (legacyMemory != null) legacyMemory.close();
        if (pathwayMemory != null) pathwayMemory.close();
    }

    @Test
    @DisplayName("Core Ingest & Recall: produces identical results across all tiers and options")
    void testCoreIngestAndRecallParity() {
        populateStandardDataset(legacyMemory);
        populateStandardDataset(pathwayMemory);

        // Assert memory counts match exactly across all tiers
        assertThat(pathwayMemory.totalMemories()).isEqualTo(legacyMemory.totalMemories());
        for (final MemoryType type : MemoryType.values()) {
            assertThat(pathwayMemory.memoryCount(type))
                    .as("Tier count for " + type)
                    .isEqualTo(legacyMemory.memoryCount(type));
        }

        // Standard text recall
        assertRecallParity("database lock timeout", RecallOptions.builder().topK(10).build());

        // Recall with importance filter
        assertRecallParity("user settings UI", RecallOptions.builder().topK(5).minImportance(0.2f).build());

        // Recall restricted to specific memory tiers
        assertRecallParity("programming guidelines", RecallOptions.builder().topK(5).memoryTypes(MemoryType.PROCEDURAL, MemoryType.SEMANTIC).build());
    }

    @Test
    @DisplayName("Cognitive Profiles: all 6 profiles produce identical candidate ranking and scores")
    void testCognitiveProfileParity() {
        populateStandardDataset(legacyMemory);
        populateStandardDataset(pathwayMemory);

        for (final CognitiveProfile profile : CognitiveProfile.values()) {
            final RecallOptions options = RecallOptions.builder()
                    .topK(5)
                    .profile(profile)
                    .build();
            assertRecallParity("concurrent database transactions", options);
        }
    }

    @Test
    @DisplayName("Synaptic Tag Filtering: Bloom filter bitmasking matches legacy pipeline exactly")
    void testSynapticTagFilterParity() {
        populateStandardDataset(legacyMemory);
        populateStandardDataset(pathwayMemory);

        final RecallOptions options = RecallOptions.builder()
                .topK(10)
                .synapticFilter("database", "error")
                .build();

        assertRecallParity("query failure analysis", options);
    }

    @Test
    @DisplayName("MMR Diversity: maximal marginal relevance reranking yields identical diverse subsets")
    void testMmrDiversityParity() {
        populateStandardDataset(legacyMemory);
        populateStandardDataset(pathwayMemory);

        final RecallOptions options = RecallOptions.builder()
                .topK(5)
                .enableMmr(true)
                .mmrLambda(0.6f)
                .build();

        assertRecallParity("general preferences and rules", options);
    }

    @Test
    @DisplayName("Hybrid Search: BM25 lexical fusion produces identical reciprocal rank fusion scores")
    void testHybridSearchParity() {
        populateStandardDataset(legacyMemory);
        populateStandardDataset(pathwayMemory);

        final RecallOptions options = RecallOptions.builder()
                .topK(5)
                .enableTextSearch(true)
                .gamma(0.5f)
                .build();

        assertRecallParity("database lock", options);
    }

    @Test
    @DisplayName("Habituation & Satiation: repetition suppression degrades identically under RecallMode.LEARN")
    void testHabituationParity() {
        populateStandardDataset(legacyMemory);
        populateStandardDataset(pathwayMemory);

        final RecallOptions options = RecallOptions.builder()
                .topK(5)
                .recallMode(RecallMode.LEARN)
                .build();

        // 3 consecutive identical queries
        for (int i = 0; i < 3; i++) {
            assertRecallParity("frequently accessed database query", options);
        }
    }

    @Test
    @DisplayName("Deduplication & Idempotency: duplicate ID updates match between engines")
    void testDeduplicationParity() {
        legacyMemory.remember("dedup-1", "Initial text version.", MemoryType.SEMANTIC, MemorySource.USER_STATED, "tag1");
        pathwayMemory.remember("dedup-1", "Initial text version.", MemoryType.SEMANTIC, MemorySource.USER_STATED, "tag1");

        // Re-ingest with same ID
        legacyMemory.remember("dedup-1", "Updated text version.", MemoryType.SEMANTIC, MemorySource.USER_STATED, "tag1");
        pathwayMemory.remember("dedup-1", "Updated text version.", MemoryType.SEMANTIC, MemorySource.USER_STATED, "tag1");

        assertThat(pathwayMemory.totalMemories()).isEqualTo(legacyMemory.totalMemories());
        assertRecallParity("Updated text", RecallOptions.builder().topK(5).build());
    }

    private void populateStandardDataset(final SpectorMemory memory) {
        memory.remember("mem-1", "User prefers dark mode for UI themes.", MemoryType.EPISODIC, MemorySource.USER_STATED, "ui", "preferences");
        memory.remember("mem-2", "User prefers Java over Python for high performance.", MemoryType.EPISODIC, MemorySource.USER_STATED, "language", "preferences");
        memory.remember("mem-3", "Database lock timeout on table users during batch migration.", MemoryType.EPISODIC, MemorySource.OBSERVED, "error", "database");
        memory.remember("mem-4", "PostgreSQL connection pool exhausted under heavy load.", MemoryType.EPISODIC, MemorySource.OBSERVED, "error", "database", "sql");
        memory.remember("mem-5", "Java is a statically typed object-oriented language.", MemoryType.SEMANTIC, MemorySource.OBSERVED, "java", "programming");
        memory.remember("mem-6", "Relational databases use ACID transactions for consistency.", MemoryType.SEMANTIC, MemorySource.OBSERVED, "database", "acid");
        memory.remember("mem-7", "Always check null bounds before pointer dereferencing.", MemoryType.PROCEDURAL, MemorySource.PROCEDURAL, "rule", "safety");
        memory.remember("mem-8", "Run unit tests before submitting pull requests.", MemoryType.PROCEDURAL, MemorySource.PROCEDURAL, "rule", "git");
        memory.remember("mem-9", "Temporary buffer state for ongoing reasoning task.", MemoryType.WORKING, MemorySource.INFERRED, "scratch");
    }

    private void assertRecallParity(final String query, final RecallOptions options) {
        final List<CognitiveResult> legacyResults = legacyMemory.recall(query, options);
        final List<CognitiveResult> pathwayResults = pathwayMemory.recall(query, options);

        assertThat(pathwayResults)
                .as("Result size parity for query '" + query + "'")
                .hasSameSizeAs(legacyResults);

        for (int i = 0; i < legacyResults.size(); i++) {
            final CognitiveResult legacy = legacyResults.get(i);
            final CognitiveResult pathway = pathwayResults.get(i);

            assertThat(pathway.id())
                    .as("Rank " + i + " ID for query '" + query + "'")
                    .isEqualTo(legacy.id());

            assertThat(pathway.memoryType())
                    .as("Rank " + i + " MemoryType for query '" + query + "'")
                    .isEqualTo(legacy.memoryType());

            assertThat(pathway.score())
                    .as("Rank " + i + " Score for query '" + query + "'")
                    .isCloseTo(legacy.score(), within(0.001f));
        }
    }

    private static class MockEmbeddingProvider implements EmbeddingProvider {
        private final int dims;

        MockEmbeddingProvider(final int dims) {
            this.dims = dims;
        }

        @Override
        public EmbeddingResult embed(final String text) {
            final Random rng = new Random(text.hashCode());
            final float[] vector = new float[dims];
            for (int i = 0; i < dims; i++) {
                vector[i] = (rng.nextFloat() - 0.5f) * 2.0f;
            }
            float norm = 0f;
            for (final float v : vector) norm += v * v;
            norm = (float) Math.sqrt(norm);
            if (norm > 0) {
                for (int i = 0; i < dims; i++) vector[i] /= norm;
            }
            return new EmbeddingResult(vector, text.split("\\s+").length, "mock-" + dims + "d");
        }

        @Override
        public int dimensions() { return dims; }

        @Override
        public String modelName() { return "mock-" + dims + "d"; }
    }
}
