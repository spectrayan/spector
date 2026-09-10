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
package com.spectrayan.spector.memory.bootstrap;

import com.spectrayan.spector.config.properties.MemoryProperties;
import com.spectrayan.spector.memory.DefaultSpectorMemory;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.model.MemoryPersistenceMode;
import com.spectrayan.spector.memory.model.TopologyStats;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.embedding.EmbeddingResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Regression coverage for {@link CognitiveGraphBuilder} to guarantee that the
 * entity directory, hypergraph, temporal knowledge graph, and topology stats are
 * always initialized and queryable even when entity extraction mode is NONE.
 */
class CognitiveGraphBuilderTest {

    @Test
    @DisplayName("In-memory mode initializes entity directory and hypergraph even when extraction mode is NONE")
    void build_withExtractionModeNone_initializesEntityDirectoryAndHypergraph() {
        MemoryProperties props = new MemoryProperties();
        props.getGraph().getEntity().setExtractionMode("NONE");

        try (SpectorMemory memory = DefaultSpectorMemory.builder(props)
                .embeddingProvider(new MockEmbeddingProvider(32))
                .persistenceMode(MemoryPersistenceMode.IN_MEMORY)
                .build()) {

            var admin = memory.admin();
            assertThat(admin).isNotNull();
            assertThat(admin.entityDirectory())
                    .as("EntityDirectory must not be null when extraction mode is NONE")
                    .isNotNull();
            assertThat(admin.hyperEntityGraph())
                    .as("HyperEntityGraph must not be null when extraction mode is NONE")
                    .isNotNull();
            assertThat(admin.temporalKnowledgeGraph())
                    .as("TemporalKnowledgeGraph must not be null when extraction mode is NONE")
                    .isNotNull();
            assertThat(admin.graph())
                    .as("CognitiveGraphFacade must not be null")
                    .isNotNull();

            TopologyStats stats = admin.graph().topologyStats();
            assertThat(stats).isNotNull();
            assertThat(stats.entityTypes()).isEmpty();
            assertThat(stats.relationTypes()).isEmpty();
        }
    }

    @Test
    @DisplayName("Bundle mode persists entities and topology across restart even when extraction mode is NONE")
    void build_bundleMode_preservesEntitiesAndTopologyAcrossReopenWithModeNone(@TempDir Path tmp) {
        MemoryProperties props = new MemoryProperties();
        props.setDimensions(32);
        props.getGraph().getEntity().setExtractionMode("NONE");

        int aliceId;
        int acmeId;

        // Phase 1: Initialize bundle, register entities, facts, and hyperedges
        try (SpectorMemory memory = SpectorMemory.builder(props)
                .embeddingProvider(new MockEmbeddingProvider(32))
                .persistence(tmp)
                .persistenceMode(MemoryPersistenceMode.DISK)
                .bundleMode(true)
                .build()) {

            var admin = memory.admin();
            assertThat(admin.entityDirectory()).isNotNull();
            assertThat(admin.hyperEntityGraph()).isNotNull();
            assertThat(admin.temporalKnowledgeGraph()).isNotNull();

            var entityDir = admin.entityDirectory();
            aliceId = entityDir.intern("Alice", "PERSON");
            acmeId = entityDir.intern("Acme Corp", "ORGANIZATION");
            entityDir.linkEntityToMemory(aliceId, 0);
            entityDir.linkEntityToMemory(acmeId, 0);

            admin.hyperEntityGraph().addHyperedge(
                    new int[]{aliceId, acmeId}, new int[]{0, 0}, 1, 1.0f, 0, System.currentTimeMillis());

            admin.temporalKnowledgeGraph().assertFact(
                    aliceId, "works_at", acmeId, -1L, (short) 0,
                    1000L, Long.MAX_VALUE, 1.0f, false);

            assertThat(entityDir.entityCount()).isEqualTo(2);

            TopologyStats stats = admin.graph().topologyStats();
            assertThat(stats.entityTypes()).extracting(TopologyStats.EntityTypeStats::type)
                    .contains("PERSON", "ORGANIZATION");
            assertThat(stats.relationTypes()).extracting(TopologyStats.RelationTypeStats::type)
                    .contains("WORKS_AT");
        }

        // Phase 2: Reopen bundle with default properties (extraction mode NONE, as in Docker/Synapse)
        MemoryProperties reopenProps = new MemoryProperties();
        reopenProps.setDimensions(32);
        reopenProps.getGraph().getEntity().setExtractionMode("NONE");

        try (SpectorMemory reopened = SpectorMemory.builder(reopenProps)
                .embeddingProvider(new MockEmbeddingProvider(32))
                .persistence(tmp)
                .persistenceMode(MemoryPersistenceMode.DISK)
                .bundleMode(true)
                .build()) {

            var admin = reopened.admin();
            assertThat(admin.entityDirectory())
                    .as("EntityDirectory must be loaded from bundle on restart")
                    .isNotNull();
            assertThat(admin.hyperEntityGraph())
                    .as("HyperEntityGraph must be loaded from bundle on restart")
                    .isNotNull();
            assertThat(admin.temporalKnowledgeGraph())
                    .as("TemporalKnowledgeGraph must be loaded from bundle on restart")
                    .isNotNull();

            assertThat(admin.entityDirectory().entityCount()).isEqualTo(2);
            assertThat(admin.entityDirectory().findEntity("Alice")).isEqualTo(aliceId);
            assertThat(admin.entityDirectory().findEntity("Acme Corp")).isEqualTo(acmeId);

            assertThat(admin.temporalKnowledgeGraph().factCount()).isEqualTo(1);

            TopologyStats stats = admin.graph().topologyStats();
            assertThat(stats).isNotNull();
            assertThat(stats.entityTypes()).extracting(TopologyStats.EntityTypeStats::type)
                    .contains("PERSON", "ORGANIZATION");
            assertThat(stats.relationTypes()).extracting(TopologyStats.RelationTypeStats::type)
                    .contains("WORKS_AT");
        }
    }

    @Test
    @DisplayName("Canonical MindSpan v2-memory dataset loads all 6,184 entities and 74,397 facts if present")
    void build_loadsCanonicalMindSpanDatasetIfPresent() {
        Path v2Path = Path.of("d:/git/spector-datasets/mindspan/results/v2-memory");
        assumeTrue(Files.exists(v2Path.resolve("runtime").resolve("runtime.bundle")),
                "Canonical MindSpan v2-memory dataset not found at " + v2Path);

        MemoryProperties props = new MemoryProperties();
        props.setDimensions(768);
        props.setEpisodicPartitionCapacity(35_000);
        props.setSemanticCapacity(30_000);
        props.setMaxNamespaces(1);
        // Default extraction mode is NONE, reproducing the Docker instance scenario
        props.getGraph().getEntity().setExtractionMode("NONE");

        try (SpectorMemory memory = SpectorMemory.builder(props)
                .embeddingProvider(new MockEmbeddingProvider(768))
                .persistence(v2Path)
                .persistenceMode(MemoryPersistenceMode.DISK)
                .bundleMode(true)
                .build()) {

            var admin = memory.admin();
            assertThat(admin.entityDirectory())
                    .as("EntityDirectory must be populated from v2-memory")
                    .isNotNull();
            assertThat(admin.entityDirectory().entityCount()).isEqualTo(6184);

            assertThat(admin.temporalKnowledgeGraph())
                    .as("TemporalKnowledgeGraph must be populated from v2-memory")
                    .isNotNull();
            assertThat(admin.temporalKnowledgeGraph().factCount()).isEqualTo(74397);

            TopologyStats stats = admin.graph().topologyStats();
            assertThat(stats).isNotNull();
            int totalEntitiesInTopology = stats.entityTypes().stream()
                    .mapToInt(TopologyStats.EntityTypeStats::nodeCount).sum();
            assertThat(stats.entityTypes().size()).isEqualTo(79);
            assertThat(stats.relationTypes().size()).isEqualTo(2515);
        }
    }

    private static class MockEmbeddingProvider implements EmbeddingProvider {
        private final int dims;

        MockEmbeddingProvider(int dims) {
            this.dims = dims;
        }

        @Override
        public EmbeddingResult embed(String text) {
            Random rng = new Random(text.hashCode());
            float[] vector = new float[dims];
            for (int i = 0; i < dims; i++) {
                vector[i] = (rng.nextFloat() - 0.5f) * 2.0f;
            }
            float norm = 0f;
            for (float v : vector) norm += v * v;
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
