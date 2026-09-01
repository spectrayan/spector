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
package com.spectrayan.spector.memory.pipeline;

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

import com.spectrayan.spector.memory.graph.HyperEntityGraphMemory;
import com.spectrayan.spector.memory.graph.EntityDirectory;

import com.spectrayan.spector.memory.graph.EntityType;

import com.spectrayan.spector.memory.graph.TypeRegistryMemory;
import com.spectrayan.spector.memory.hebbian.HebbianGraphMemory;
import com.spectrayan.spector.memory.sync.MemoryWal;
import com.spectrayan.spector.memory.sync.WalEvent;
import com.spectrayan.spector.memory.sync.WalRecoveryDispatcher;
import com.spectrayan.spector.memory.hebbian.HebbianEdge;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class HypergraphRecallSpikeTest {

    @Test
    @DisplayName("Hypergraph collectMemories recursively gathers memory indices from hyperedges")
    void hypergraph_collectMemories_recursivelyGathersIndices() {
        HyperEntityGraphMemory hyper = new HyperEntityGraphMemory(100, 500);

        // Hyperedge 1: connects entity 0 and 1, related to memory 10
        hyper.addHyperedge(new int[]{0, 1}, new int[]{1, 1}, 1, 1.0f, 10, System.currentTimeMillis());
        // Hyperedge 2: connects entity 1 and 2, related to memory 20
        hyper.addHyperedge(new int[]{1, 2}, new int[]{1, 1}, 1, 1.0f, 20, System.currentTimeMillis());

        // Max hops = 0: should only collect memories directly on entity 0's hyperedges (memory 10)
        Set<Integer> memoriesHop0 = hyper.collectMemories(0, 0);
        assertThat(memoriesHop0).containsExactlyInAnyOrder(10);

        // Max hops = 1: should traverse to entity 1 and collect memory 20 as well
        Set<Integer> memoriesHop1 = hyper.collectMemories(0, 1);
        assertThat(memoriesHop1).containsExactlyInAnyOrder(10, 20);
    }



    @Test
    @DisplayName("GraphExpansionStage reports hypergraph subsystem availability")
    void graphExpansionStage_queriesHyperEntityGraph() {
        HyperEntityGraphMemory hyperGraph = new HyperEntityGraphMemory(100, 500);

        hyperGraph.addHyperedge(new int[]{0, 2}, new int[]{1, 1}, 1, 1.0f, 99, System.currentTimeMillis());

        GraphScoringPolicy policy = new GraphScoringPolicy(
                0.3f, 0.3f, 0.8f, 0.7f, 0.25f, 2, 3, 2, 0.40f,
                GraphExpansionMode.GATED
        );
        GraphExpansionStage stage = new GraphExpansionStage(
                null, null, null, hyperGraph, null, policy, null, null, null, null
        );

        assertThat(stage.hasGraphSubsystems()).isTrue();
    }

    @Test
    @DisplayName("HyperEntityGraph WAL round-trip recovery restores hyperedges (#460 / #417)")
    void hyperedgeWal_roundTripRecovery_restoresHyperedges() throws IOException {
        Path tempDir = Files.createTempDirectory("spector-hyperedge-wal");
        try {
            MemoryWal wal = new MemoryWal(tempDir);
            HyperEntityGraphMemory original = new HyperEntityGraphMemory(100, 500);
            original.bindWal(wal);

            int edge = original.addHyperedge(new int[]{0, 1, 2}, new int[]{1, 3, 3},
                    5, 2.0f, 77, 123456789L);
            assertThat(edge).isGreaterThanOrEqualTo(0);
            assertThat(original.collectMemories(0, 1)).contains(77);

            wal.close();

            // Simulate a crash between checkpoints: reopen the WAL against a fresh (empty) hypergraph.
            MemoryWal recoveryWal = new MemoryWal(tempDir);
            HyperEntityGraphMemory recovered = new HyperEntityGraphMemory(100, 500);
            assertThat(recovered.totalHyperedges()).isZero();

            java.util.Map<com.spectrayan.spector.memory.kernel.MemoryId, com.spectrayan.spector.memory.kernel.Memory<?>> memories = new java.util.HashMap<>();
            memories.put(recovered.id(), recovered);
            WalRecoveryDispatcher.recover(recoveryWal, memories);
            recoveryWal.close();

            assertThat(recovered.totalHyperedges()).isEqualTo(1);
            assertThat(recovered.collectMemories(0, 1)).contains(77);
            var edges = recovered.findHyperedgesForEntity(1);
            assertThat(edges).hasSize(1);
            assertThat(edges.get(0).memoryIdx()).isEqualTo(77);
            assertThat(edges.get(0).type()).isEqualTo(5);
            assertThat(edges.get(0).vertices()).hasSize(3);

            recovered.close();
            original.close();
        } finally {
            try {
                Files.walk(tempDir)
                     .sorted((a, b) -> b.compareTo(a))
                     .forEach(p -> {
                         try {
                             Files.delete(p);
                         } catch (IOException ignored) {}
                     });
            } catch (IOException ignored) {}
        }
    }

    @Test
    @DisplayName("WAL binding round-trip recovery reconstructs EntityDirectory identity and HebbianGraph (#456)")
    void walBinding_roundTripRecovery_reconstructsGraphs() throws IOException {
        Path tempDir = Files.createTempDirectory("spector-wal-roundtrip");
        try {
            MemoryWal wal = new MemoryWal(tempDir);
            TypeRegistryMemory reg = TypeRegistryMemory.seeded(com.spectrayan.spector.memory.kernel.SystemMemoryId.ENTITY_TYPE, EntityType.SEED);
            EntityDirectory originalDir = new EntityDirectory(100, reg);
            HebbianGraphMemory originalHebbianGraph = new HebbianGraphMemory(100);

            // Bind WAL (ADR-0003 #456: the directory is the WAL-recovered identity store)
            originalDir.bindWal(wal);
            originalHebbianGraph.bindWal(wal);

            // Mutate: intern entities + link a single-entity memory adjacency
            int e0 = originalDir.intern("Alpha", "CONCEPT");
            int e1 = originalDir.intern("Beta", "CONCEPT");
            originalDir.linkEntityToMemory(e0, 42);

            originalHebbianGraph.strengthen(10, 20, 2.5f);

            assertThat(originalDir.findEntity("Alpha")).isEqualTo(e0);
            assertThat(originalDir.findEntity("Beta")).isEqualTo(e1);
            assertThat(originalDir.memoriesForEntity(e0)).contains(42);
            assertThat(originalHebbianGraph.neighbors(10))
                    .anyMatch(edge -> edge.neighborIndex() == 20 && edge.weight() == 2.5f);

            // Close WAL to flush to disk
            wal.close();

            // Reopen WAL for recovery
            MemoryWal recoveryWal = new MemoryWal(tempDir);

            // Fresh empty instances (restart state before recovery)
            TypeRegistryMemory recoveryReg = TypeRegistryMemory.seeded(com.spectrayan.spector.memory.kernel.SystemMemoryId.ENTITY_TYPE, EntityType.SEED);
            EntityDirectory recoveredDir = new EntityDirectory(100, recoveryReg);
            HebbianGraphMemory recoveredHebbianGraph = new HebbianGraphMemory(100);

            assertThat(recoveredDir.findEntity("Alpha")).isEqualTo(-1);
            assertThat(recoveredDir.findEntity("Beta")).isEqualTo(-1);
            assertThat(recoveredHebbianGraph.neighbors(10)).isEmpty();

            java.util.Map<com.spectrayan.spector.memory.kernel.MemoryId, com.spectrayan.spector.memory.kernel.Memory<?>> memories = new java.util.HashMap<>();
            memories.put(recoveredDir.id(), recoveredDir);
            memories.put(recoveredHebbianGraph.id(), recoveredHebbianGraph);

            // Dispatch replay
            WalRecoveryDispatcher.recover(recoveryWal, memories);
            recoveryWal.close();

            // Identity + single-entity adjacency reconstructed via GRAPH_ADD_NODE/GRAPH_LINK_MEMORY.
            int recoveredE0 = recoveredDir.findEntity("Alpha");
            int recoveredE1 = recoveredDir.findEntity("Beta");
            assertThat(recoveredE0).isEqualTo(e0);
            assertThat(recoveredE1).isEqualTo(e1);
            assertThat(recoveredDir.memoriesForEntity(recoveredE0)).contains(42);
            assertThat(recoveredDir.entityType(recoveredE0)).isEqualTo("CONCEPT");
            assertThat(recoveredHebbianGraph.neighbors(10))
                    .anyMatch(edge -> edge.neighborIndex() == 20 && edge.weight() == 2.5f);

            recoveredDir.close();
            originalDir.close();
        } finally {
            // Cleanup WAL dir
            try {
                Files.walk(tempDir)
                     .sorted((a, b) -> b.compareTo(a))
                     .forEach(p -> {
                         try {
                             Files.delete(p);
                         } catch (IOException ignored) {}
                     });
            } catch (IOException ignored) {}
        }
    }
}
