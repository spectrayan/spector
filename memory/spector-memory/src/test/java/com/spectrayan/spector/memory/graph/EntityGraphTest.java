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
package com.spectrayan.spector.memory.graph;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for EntityGraph: entity management, relations, traversal, and persistence.
 */
class EntityGraphTest {

    @TempDir
    Path tempDir;

    private EntityGraph graph;

    @BeforeEach
    void setUp() {
        graph = new EntityGraph(100, 500);
    }

    @AfterEach
    void tearDown() {
        graph.close();
    }

    @Test
    void addEntityReturnsId() {
        int id = graph.addEntity("Alice", "PERSON");
        assertThat(id).isEqualTo(0);
        assertThat(graph.entityCount()).isEqualTo(1);
    }

    @Test
    void addDuplicateEntityReturnsExistingId() {
        int id1 = graph.addEntity("Alice", "PERSON");
        int id2 = graph.addEntity("alice", "PERSON"); // case-insensitive
        int id3 = graph.addEntity("ALICE", "PERSON");

        assertThat(id1).isEqualTo(id2).isEqualTo(id3);
        assertThat(graph.entityCount()).isEqualTo(1);
    }

    @Test
    void findEntityCaseInsensitive() {
        graph.addEntity("Project Alpha", "PROJECT");

        assertThat(graph.findEntity("project alpha")).isEqualTo(0);
        assertThat(graph.findEntity("PROJECT ALPHA")).isEqualTo(0);
        assertThat(graph.findEntity("nonexistent")).isEqualTo(-1);
    }

    @Test
    void entityTypePreserved() {
        graph.addEntity("Alice", "PERSON");
        graph.addEntity("Acme", "ORGANIZATION");

        assertThat(graph.entityType(0)).isEqualTo("PERSON");
        assertThat(graph.entityType(1)).isEqualTo("ORGANIZATION");
    }

    @Test
    void customEntityTypeAccepted() {
        graph.addEntity("Tesla", "VEHICLE_MANUFACTURER");
        graph.addEntity("ChatGPT", "SOFTWARE");

        assertThat(graph.entityType(0)).isEqualTo("VEHICLE_MANUFACTURER");
        assertThat(graph.entityType(1)).isEqualTo("SOFTWARE");
    }

    @Test
    void addRelation() {
        int alice = graph.addEntity("Alice", "PERSON");
        int project = graph.addEntity("Project Alpha", "PROJECT");

        graph.addRelation(alice, project, "MANAGES");

        List<EntityGraph.EntityEdge> edges = graph.edges(alice);
        assertThat(edges).hasSize(1);
        assertThat(edges.get(0).targetEntityId()).isEqualTo(project);
        assertThat(edges.get(0).relationType()).isEqualTo("MANAGES");
        assertThat(edges.get(0).weight()).isEqualTo(1.0f);
    }

    @Test
    void duplicateRelationStrengthensWeight() {
        int alice = graph.addEntity("Alice", "PERSON");
        int project = graph.addEntity("Project Alpha", "PROJECT");

        graph.addRelation(alice, project, "MANAGES");
        graph.addRelation(alice, project, "MANAGES");

        List<EntityGraph.EntityEdge> edges = graph.edges(alice);
        assertThat(edges).hasSize(1);
        assertThat(edges.get(0).weight()).isEqualTo(2.0f);
    }

    @Test
    void linkEntityToMemory() {
        int alice = graph.addEntity("Alice", "PERSON");

        graph.linkEntityToMemory(alice, 42);
        graph.linkEntityToMemory(alice, 99);
        graph.linkEntityToMemory(alice, 42); // duplicate: ignored

        int[] memories = graph.memoriesForEntity(alice);
        assertThat(memories).containsExactly(42, 99);
    }

    @Test
    void unlimitedMemoryRefsAllowed() {
        int alice = graph.addEntity("Alice", "PERSON");

        // Link 20 memories — far beyond the old MAX_MEMORY_REFS=4 limit
        for (int i = 0; i < 20; i++) {
            graph.linkEntityToMemory(alice, i);
        }

        int[] memories = graph.memoriesForEntity(alice);
        assertThat(memories).hasSize(20);
        for (int i = 0; i < 20; i++) {
            assertThat(memories[i]).isEqualTo(i);
        }
    }

    @Test
    void duplicateLinkReinforcesWeight() {
        int alice = graph.addEntity("Alice", "PERSON");

        graph.linkEntityToMemory(alice, 42);
        assertThat(graph.memoryRefWeight(alice, 0)).isEqualTo(1.0f);

        // Re-mention: should reinforce, not add duplicate
        graph.linkEntityToMemory(alice, 42);
        int[] memories = graph.memoriesForEntity(alice);
        assertThat(memories).hasSize(1); // still just one entry
        assertThat(graph.memoryRefWeight(alice, 0)).isEqualTo(1.2f); // +0.2 LTP
    }

    @Test
    void fanFactorScalesWithRefCount() {
        int alice = graph.addEntity("Alice", "PERSON");

        // 0 refs → factor 1.0 (clamped from NaN)
        assertThat(graph.fanFactor(alice)).isEqualTo(1.0f);

        // 1 ref → factor 1.0
        graph.linkEntityToMemory(alice, 0);
        assertThat(graph.fanFactor(alice)).isEqualTo(1.0f);

        // 4 refs → factor 0.5 (1/√4)
        for (int i = 1; i < 4; i++) graph.linkEntityToMemory(alice, i);
        assertThat(graph.fanFactor(alice)).isCloseTo(0.5f, org.assertj.core.data.Offset.offset(0.01f));

        // 16 refs → factor 0.25 (1/√16)
        for (int i = 4; i < 16; i++) graph.linkEntityToMemory(alice, i);
        assertThat(graph.fanFactor(alice)).isCloseTo(0.25f, org.assertj.core.data.Offset.offset(0.01f));
    }

    @Test
    void adjacencyDecayPrunesWeakLinks() {
        int alice = graph.addEntity("Alice", "PERSON");
        graph.linkEntityToMemory(alice, 10);
        graph.linkEntityToMemory(alice, 20);

        // Repeatedly decay until weight drops below threshold
        for (int i = 0; i < 100; i++) {
            graph.decayAdjacencyWeights(0.95f, 0.2f);
        }

        // After ~100 cycles of 5% decay, weight ≈ 1.0 × 0.95^100 ≈ 0.006 → pruned
        assertThat(graph.memoriesForEntity(alice)).isEmpty();
    }

    @Test
    void adjacencyCompaction() {
        int alice = graph.addEntity("Alice", "PERSON");
        int bob = graph.addEntity("Bob", "PERSON");

        // Link enough to trigger block growth for alice
        for (int i = 0; i < 20; i++) graph.linkEntityToMemory(alice, i);
        for (int i = 100; i < 105; i++) graph.linkEntityToMemory(bob, i);

        int hwmBefore = graph.adjHighWaterMark();
        long reclaimed = graph.compactAdjacency();

        // Compaction should reclaim fragmented space from alice's old block
        assertThat(graph.adjHighWaterMark()).isLessThanOrEqualTo(hwmBefore);

        // Data integrity preserved
        assertThat(graph.memoriesForEntity(alice)).hasSize(20);
        assertThat(graph.memoriesForEntity(bob)).hasSize(5);
    }

    @Test
    void bfsTraversal() {
        int alice = graph.addEntity("Alice", "PERSON");
        int project = graph.addEntity("Project Alpha", "PROJECT");
        int bob = graph.addEntity("Bob", "PERSON");

        graph.addRelation(alice, project, "MANAGES");
        graph.addRelation(project, bob, "PART_OF");

        // Traverse from alice: should reach project (hop 1) and bob (hop 2)
        var results = graph.traverse(alice, null, 2);
        assertThat(results).hasSize(2);
        assertThat(results.get(0).entityId()).isEqualTo(project);
        assertThat(results.get(0).hopDistance()).isEqualTo(1);
        assertThat(results.get(1).entityId()).isEqualTo(bob);
        assertThat(results.get(1).hopDistance()).isEqualTo(2);
    }

    @Test
    void bfsTraversalWithFilter() {
        int alice = graph.addEntity("Alice", "PERSON");
        int project = graph.addEntity("Project Alpha", "PROJECT");
        int bob = graph.addEntity("Bob", "PERSON");

        graph.addRelation(alice, project, "MANAGES");
        graph.addRelation(alice, bob, "RELATED_TO");

        // Filter: only MANAGES edges
        var results = graph.traverse(alice, "MANAGES", 2);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).entityId()).isEqualTo(project);
    }

    @Test
    void collectMemories() {
        int alice = graph.addEntity("Alice", "PERSON");
        int project = graph.addEntity("Project Alpha", "PROJECT");

        graph.linkEntityToMemory(alice, 10);
        graph.linkEntityToMemory(project, 20);
        graph.addRelation(alice, project, "MANAGES");

        Set<Integer> memories = graph.collectMemories(alice, null, 2);
        assertThat(memories).containsExactlyInAnyOrder(10, 20);
    }

    @Test
    void saveAndLoadPreservesGraph() {
        int alice = graph.addEntity("Alice", "PERSON");
        int project = graph.addEntity("Project Alpha", "PROJECT");
        graph.addRelation(alice, project, "MANAGES");
        graph.linkEntityToMemory(alice, 42);

        Path file = tempDir.resolve("test.entity");
        graph.save(file);
        graph.close();

        graph = EntityGraph.load(file, 100, 500);
        assertThat(graph.entityCount()).isEqualTo(2);
        assertThat(graph.findEntity("alice")).isEqualTo(0);
        assertThat(graph.findEntity("project alpha")).isEqualTo(1);

        // Relations preserved
        var edges = graph.edges(0);
        assertThat(edges).hasSize(1);
        assertThat(edges.get(0).relationType()).isEqualTo("MANAGES");

        // Memory refs preserved
        int[] memories = graph.memoriesForEntity(0);
        assertThat(memories).containsExactly(42);
    }

    @Test
    void loadNonExistentFileCreatesNew() {
        Path file = tempDir.resolve("nonexistent.entity");
        graph.close();
        graph = EntityGraph.load(file, 50, 200);

        assertThat(graph.entityCount()).isZero();
    }

    @Test
    void boundsCheckDoesNotCrash() {
        graph.addRelation(-1, 0, "OTHER"); // ignored
        graph.addRelation(0, 500, "OTHER"); // ignored
        graph.linkEntityToMemory(-1, 0); // ignored
        assertThat(graph.edges(-1)).isEmpty();
        assertThat(graph.memoriesForEntity(-1)).isEmpty();
        assertThat(graph.entityType(-1)).isEqualTo("OTHER");
    }

    @Test
    void nameIndexSnapshot() {
        graph.addEntity("Alice", "PERSON");
        graph.addEntity("Bob", "PERSON");

        var snapshot = graph.nameIndex();
        assertThat(snapshot).containsKeys("alice", "bob");
    }

    @Test
    void nonContiguousRelationAddingDoesNotCorruptEdges() {
        int alice = graph.addEntity("Alice", "PERSON");
        int bob = graph.addEntity("Bob", "PERSON");
        int charlie = graph.addEntity("Charlie", "PERSON");

        // Alice -> Bob
        graph.addRelation(alice, bob, "WORKS_WITH");
        // Bob -> Charlie
        graph.addRelation(bob, charlie, "MANAGES");
        // Alice -> Charlie (Interleaved addition: Alice already has an edge, Bob was added next)
        graph.addRelation(alice, charlie, "KNOWS");

        // Verify Alice's edges (should be relocated and contiguous)
        var aliceEdges = graph.edges(alice);
        assertThat(aliceEdges).hasSize(2);
        assertThat(aliceEdges.get(0).targetEntityId()).isEqualTo(bob);
        assertThat(aliceEdges.get(0).relationType()).isEqualTo("WORKS_WITH");
        assertThat(aliceEdges.get(1).targetEntityId()).isEqualTo(charlie);
        assertThat(aliceEdges.get(1).relationType()).isEqualTo("KNOWS");

        // Verify Bob's edges (should remain intact)
        var bobEdges = graph.edges(bob);
        assertThat(bobEdges).hasSize(1);
        assertThat(bobEdges.get(0).targetEntityId()).isEqualTo(charlie);
        assertThat(bobEdges.get(0).relationType()).isEqualTo("MANAGES");
    }
}
