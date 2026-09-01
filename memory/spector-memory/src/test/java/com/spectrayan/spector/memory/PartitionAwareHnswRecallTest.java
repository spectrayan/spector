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

import com.spectrayan.spector.core.similarity.SimilarityFunction;
import com.spectrayan.spector.index.HnswIndex;
import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.kernel.StorageLayout;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.MemoryPersistenceMode;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.test.FakeEmbeddingProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit & Integration tests for Partition-Aware Global HNSW Semantic Recall (ADR-0009, #445).
 */
@DisplayName("Issue #445 — Partition-Aware Global HNSW Semantic Recall")
class PartitionAwareHnswRecallTest {

    private SpectorMemory memory;

    private SpectorMemory build(Path dir, int semanticCap, HnswIndex semanticIndex) {
        FakeEmbeddingProvider embed = new FakeEmbeddingProvider();
        return DefaultSpectorMemory.builder()
                .dimensions(embed.dimensions())
                .embeddingProvider(embed)
                .persistenceMode(MemoryPersistenceMode.DISK)
                .persistence(dir)
                .workingCapacity(32)
                .episodicPartitionCapacity(64)
                .semanticCapacity(semanticCap)
                .proceduralCapacity(32)
                .surpriseWarmup(1)
                .semanticIndex(semanticIndex)
                .build();
    }

    @AfterEach
    void tearDown() {
        if (memory != null) {
            memory.close();
            memory = null;
        }
    }

    private static Set<String> ids(List<CognitiveResult> results) {
        return results.stream().map(CognitiveResult::id).collect(Collectors.toSet());
    }

    private static long partitionDirCount(Path base) throws Exception {
        try (var stream = Files.newDirectoryStream(StorageLayout.partitionsDir(base))) {
            long n = 0;
            for (Path p : stream) {
                if (Files.isDirectory(p) && StorageLayout.isPartitionDir(p.getFileName().toString())) n++;
            }
            return n;
        }
    }

    @Test
    @DisplayName("HNSW semantic recall spans multiple rolled partitions seamlessly")
    void hnswRecallSpansMultiplePartitions(@TempDir Path dir) throws Exception {
        FakeEmbeddingProvider embed = new FakeEmbeddingProvider();
        HnswIndex hnsw = new HnswIndex(embed.dimensions(), 100, SimilarityFunction.COSINE);
        memory = build(dir, /*semanticCap*/ 2, hnsw);

        // Ingest 5 semantic memories with semanticCapacity=2 → forces 3 partitions (000, 001, 002)
        memory.remember("sem-0", "semantic knowledge about quantum computing algorithms",
                MemoryType.SEMANTIC, MemorySource.OBSERVED, "quantum");
        memory.remember("sem-1", "entanglement in distributed quantum networks",
                MemoryType.SEMANTIC, MemorySource.OBSERVED, "quantum");
        memory.remember("sem-2", "superconducting qubits and coherence decoherence times",
                MemoryType.SEMANTIC, MemorySource.OBSERVED, "quantum");
        memory.remember("sem-3", "quantum error correction surface codes",
                MemoryType.SEMANTIC, MemorySource.OBSERVED, "quantum");
        memory.remember("sem-4", "topological braid state quantum gates",
                MemoryType.SEMANTIC, MemorySource.OBSERVED, "quantum");

        assertThat(partitionDirCount(dir)).isGreaterThanOrEqualTo(3);

        // Recall with topK=10 covering all records
        List<CognitiveResult> results = memory.recall(
                "quantum algorithms and superconducting coherence",
                RecallOptions.builder().topK(10).memoryTypes(MemoryType.SEMANTIC).build());

        Set<String> resultIds = ids(results);
        assertThat(resultIds).contains("sem-0", "sem-1", "sem-2", "sem-3", "sem-4");

        // Verify cognitive properties are properly scored from the off-heap partition segments
        for (CognitiveResult res : results) {
            assertThat(res.memoryType()).isEqualTo(MemoryType.SEMANTIC);
            assertThat(res.importance()).isGreaterThan(0.0f);
            assertThat(res.score()).isGreaterThan(0.0f);
            assertThat(res.text()).isNotBlank();
        }
    }

    @Test
    @DisplayName("Startup HNSW rebuild indexes semantic records across all frozen and active partitions")
    void startupRebuildRestoresAllPartitions(@TempDir Path dir) throws Exception {
        FakeEmbeddingProvider embed = new FakeEmbeddingProvider();
        HnswIndex hnsw1 = new HnswIndex(embed.dimensions(), 100, SimilarityFunction.COSINE);
        memory = build(dir, /*semanticCap*/ 2, hnsw1);

        memory.remember("sem-p0-1", "neural network transformer attention mechanisms",
                MemoryType.SEMANTIC, MemorySource.OBSERVED, "ml");
        memory.remember("sem-p0-2", "flash attention tiling memory optimization",
                MemoryType.SEMANTIC, MemorySource.OBSERVED, "ml");
        memory.remember("sem-p1-1", "rotary position embeddings in large language models",
                MemoryType.SEMANTIC, MemorySource.OBSERVED, "ml");
        memory.remember("sem-p1-2", "mixture of experts routing algorithms",
                MemoryType.SEMANTIC, MemorySource.OBSERVED, "ml");
        memory.remember("sem-p2-1", "quantized low rank adaptation fine-tuning",
                MemoryType.SEMANTIC, MemorySource.OBSERVED, "ml");

        assertThat(partitionDirCount(dir)).isGreaterThanOrEqualTo(3);
        memory.close();

        // Re-open with a fresh, empty HnswIndex — startup rebuild should populate it from all partitions
        HnswIndex hnsw2 = new HnswIndex(embed.dimensions(), 100, SimilarityFunction.COSINE);
        assertThat(hnsw2.size()).isEqualTo(0);

        memory = build(dir, /*semanticCap*/ 2, hnsw2);

        // Verify HNSW was rebuilt from all partitions
        assertThat(hnsw2.size()).isEqualTo(5);

        List<CognitiveResult> results = memory.recall(
                "transformer attention and rotary embeddings",
                RecallOptions.builder().topK(10).memoryTypes(MemoryType.SEMANTIC).build());

        Set<String> resultIds = ids(results);
        assertThat(resultIds).containsExactlyInAnyOrder(
                "sem-p0-1", "sem-p0-2", "sem-p1-1", "sem-p1-2", "sem-p2-1");
    }

    @Test
    @DisplayName("Tombstoned/forgotten records in frozen partitions are skipped by HNSW recall")
    void tombstonedRecordsInFrozenPartitionsAreSkipped(@TempDir Path dir) throws Exception {
        FakeEmbeddingProvider embed = new FakeEmbeddingProvider();
        HnswIndex hnsw = new HnswIndex(embed.dimensions(), 100, SimilarityFunction.COSINE);
        memory = build(dir, /*semanticCap*/ 2, hnsw);

        // Fill partition 000 with sem-0 and sem-1
        memory.remember("sem-0", "confidential secret encryption key details",
                MemoryType.SEMANTIC, MemorySource.OBSERVED, "security");
        memory.remember("sem-1", "public cryptography elliptic curve parameters",
                MemoryType.SEMANTIC, MemorySource.OBSERVED, "security");
        // Roll to partition 001 with sem-2
        memory.remember("sem-2", "zero knowledge proofs and verifiable computing",
                MemoryType.SEMANTIC, MemorySource.OBSERVED, "security");

        assertThat(partitionDirCount(dir)).isGreaterThanOrEqualTo(2);

        // Forget sem-0 in frozen partition 000
        memory.forget("sem-0");

        List<CognitiveResult> results = memory.recall(
                "encryption key and cryptography parameters",
                RecallOptions.builder().topK(10).memoryTypes(MemoryType.SEMANTIC).build());

        Set<String> resultIds = ids(results);
        assertThat(resultIds).doesNotContain("sem-0");
        assertThat(resultIds).contains("sem-1", "sem-2");
    }

    @Test
    @DisplayName("Synaptic tag gating and hyperfocus filter HNSW results across partitions")
    void synapticTagGatingAcrossPartitions(@TempDir Path dir) throws Exception {
        FakeEmbeddingProvider embed = new FakeEmbeddingProvider();
        HnswIndex hnsw = new HnswIndex(embed.dimensions(), 100, SimilarityFunction.COSINE);
        memory = build(dir, /*semanticCap*/ 2, hnsw);

        // Ingest memories with distinct tags across partition rolls
        memory.remember("sem-tag-arch1", "microservices event driven architecture",
                MemoryType.SEMANTIC, MemorySource.OBSERVED, "architecture");
        memory.remember("sem-tag-arch2", "domain driven design aggregate roots",
                MemoryType.SEMANTIC, MemorySource.OBSERVED, "architecture");
        memory.remember("sem-tag-devops", "kubernetes container pod autoscaling",
                MemoryType.SEMANTIC, MemorySource.OBSERVED, "devops");
        memory.remember("sem-tag-sec", "zero trust mutual tls authentication",
                MemoryType.SEMANTIC, MemorySource.OBSERVED, "security");

        // Hyperfocus on "architecture"
        List<CognitiveResult> archResults = memory.recall(
                "cloud system architecture and pod scaling",
                RecallOptions.builder()
                        .topK(10)
                        .memoryTypes(MemoryType.SEMANTIC)
                        .hyperfocusMask("architecture")
                        .build());

        Set<String> archIds = ids(archResults);
        assertThat(archIds).contains("sem-tag-arch1", "sem-tag-arch2");
        assertThat(archIds).doesNotContain("sem-tag-devops", "sem-tag-sec");
    }
}
