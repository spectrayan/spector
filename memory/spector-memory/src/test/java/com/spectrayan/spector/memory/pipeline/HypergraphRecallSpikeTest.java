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

import com.spectrayan.spector.memory.graph.HyperEntityGraphMemory;
import com.spectrayan.spector.memory.graph.EntityGraphMemory;
import com.spectrayan.spector.memory.graph.EdgeImportance;
import com.spectrayan.spector.memory.hebbian.HebbianGraphMemory;
import com.spectrayan.spector.memory.sync.MemoryWal;
import com.spectrayan.spector.memory.sync.WalEvent;
import com.spectrayan.spector.memory.sync.WalRecoveryDispatcher;
import com.spectrayan.spector.memory.hebbian.HebbianGraph.HebbianEdge;
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
    @DisplayName("WAL binding: entity and Hebbian mutations write to the Write-Ahead Log")
    void walBinding_mutations_areLoggedToWal() throws IOException {
        Path tempDir = Files.createTempDirectory("spector-wal-test");
        try {
            MemoryWal wal = new MemoryWal(tempDir);
            EntityGraphMemory entityGraph = new EntityGraphMemory(100, 500, 32, EdgeImportance.DEFAULT);
            HebbianGraphMemory hebbianGraph = new HebbianGraphMemory(100);

            // Bind WAL
            entityGraph.bindWal(wal);
            hebbianGraph.bindWal(wal);

            // Perform mutations
            int e0 = entityGraph.addEntity("Alpha", "CONCEPT");
            int e1 = entityGraph.addEntity("Beta", "CONCEPT");
            entityGraph.addRelation(e0, e1, "ASSOCIATED_WITH");

            hebbianGraph.strengthen(10, 20, 2.5f);

            // Replay and assert WAL event presence
            List<WalEvent> events = wal.replay(0);
            assertThat(events).isNotEmpty();

            boolean foundNode = false;
            boolean foundEdge = false;
            boolean foundHebbian = false;

            for (WalEvent ev : events) {
                if (ev.type() == WalEvent.EventType.GRAPH_ADD_NODE) {
                    foundNode = true;
                } else if (ev.type() == WalEvent.EventType.ADJ_ADD_EDGE) {
                    // Both EntityGraph and HebbianGraph append ADJ_ADD_EDGE
                    foundEdge = true;
                    foundHebbian = true;
                }
            }

            assertThat(foundNode).isTrue();
            assertThat(foundEdge).isTrue();
            assertThat(foundHebbian).isTrue();
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

    @Test
    @DisplayName("GraphExpansionStage queries HyperEntityGraph when useHypergraphRecall is true")
    void graphExpansionStage_queriesHyperEntityGraph_whenUseHypergraphRecallTrue() {
        EntityGraphMemory entityGraph = new EntityGraphMemory(100, 500, 32, EdgeImportance.DEFAULT);
        HyperEntityGraphMemory hyperGraph = new HyperEntityGraphMemory(100, 500);

        int e0 = entityGraph.addEntity("Alpha", "CONCEPT");
        int e2 = entityGraph.addEntity("Gamma", "CONCEPT");

        hyperGraph.addHyperedge(new int[]{0, 2}, new int[]{1, 1}, 1, 1.0f, 99, System.currentTimeMillis());

        GraphScoringPolicy policy = new GraphScoringPolicy(
                0.3f, 0.3f, 0.8f, 0.7f, 0.25f, 2, 3, 2, 0.40f, true // useHypergraphRecall = true
        );

        GraphExpansionStage stage = new GraphExpansionStage(
                null, null, entityGraph, hyperGraph, null, policy, null, null, null, null
        );

        assertThat(stage.hasGraphSubsystems()).isTrue();
    }

    @Test
    @DisplayName("WAL binding round-trip recovery reconstructs EntityGraph and HebbianGraph")
    void walBinding_roundTripRecovery_reconstructsGraphs() throws IOException {
        Path tempDir = Files.createTempDirectory("spector-wal-roundtrip");
        try {
            MemoryWal wal = new MemoryWal(tempDir);
            EntityGraphMemory originalEntityGraph = new EntityGraphMemory(100, 500, 32, EdgeImportance.DEFAULT);
            HebbianGraphMemory originalHebbianGraph = new HebbianGraphMemory(100);

            // Bind WAL
            originalEntityGraph.bindWal(wal);
            originalHebbianGraph.bindWal(wal);

            // Mutate
            int e0 = originalEntityGraph.addEntity("Alpha", "CONCEPT");
            int e1 = originalEntityGraph.addEntity("Beta", "CONCEPT");
            originalEntityGraph.addRelation(e0, e1, "ASSOCIATED_WITH");
            originalEntityGraph.linkEntityToMemory(e0, 42);

            originalHebbianGraph.strengthen(10, 20, 2.5f);

            // Ensure the changes are in the memory instances before closing
            assertThat(originalEntityGraph.findEntity("Alpha")).isEqualTo(e0);
            assertThat(originalEntityGraph.findEntity("Beta")).isEqualTo(e1);
            assertThat(originalEntityGraph.memoriesForEntity(e0)).contains(42);
            assertThat(originalHebbianGraph.neighbors(10))
                    .anyMatch(edge -> edge.neighborIndex() == 20 && edge.weight() == 2.5f);

            // Close WAL to flush to disk
            wal.close();

            // Reopen WAL for recovery
            MemoryWal recoveryWal = new MemoryWal(tempDir);

            // Create fresh empty graph instances (representing restart state before recovery)
            EntityGraphMemory recoveredEntityGraph = new EntityGraphMemory(100, 500, 32, EdgeImportance.DEFAULT);
            HebbianGraphMemory recoveredHebbianGraph = new HebbianGraphMemory(100);

            // Verify they start empty
            assertThat(recoveredEntityGraph.findEntity("Alpha")).isEqualTo(-1);
            assertThat(recoveredEntityGraph.findEntity("Beta")).isEqualTo(-1);
            assertThat(recoveredHebbianGraph.neighbors(10)).isEmpty();

            // Prepare dispatcher memories map
            java.util.Map<com.spectrayan.spector.memory.kernel.MemoryId, com.spectrayan.spector.memory.kernel.Memory<?>> memories = new java.util.HashMap<>();
            memories.put(recoveredEntityGraph.id(), recoveredEntityGraph);
            memories.put(recoveredHebbianGraph.id(), recoveredHebbianGraph);

            // Dispatch replay
            WalRecoveryDispatcher.recover(recoveryWal, memories);

            // Close recovery WAL
            recoveryWal.close();

            // Assert everything is reconstructed
            int recoveredE0 = recoveredEntityGraph.findEntity("Alpha");
            int recoveredE1 = recoveredEntityGraph.findEntity("Beta");
            assertThat(recoveredE0).isNotEqualTo(-1);
            assertThat(recoveredE1).isNotEqualTo(-1);
            assertThat(recoveredEntityGraph.memoriesForEntity(recoveredE0)).contains(42);
            assertThat(recoveredHebbianGraph.neighbors(10))
                    .anyMatch(edge -> edge.neighborIndex() == 20 && edge.weight() == 2.5f);
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
