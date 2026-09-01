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
package com.spectrayan.spector.memory.graph.hebbian;

import com.spectrayan.spector.memory.kernel.MemoryShape;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the Cross-Capture Graph traversal functionality (ADR-0009).
 *
 * <p>Validates tag co-occurrence traversal, inverted index management,
 * fan-factor attenuation, and graceful degradation.</p>
 */
class CrossCaptureTraversalTest {

    private CoActivationRecordMemory tracker;

    @BeforeEach
    void setUp() {
        tracker = new CoActivationRecordMemory(1000, 1000);
    }

    // ── Shape Tests ──

    @Test
    void shapeIsHashTable() {
        assertThat(tracker.shape()).isEqualTo(MemoryShape.HASHTABLE);
    }

    // ── Inverted Index Tests ──

    @Test
    void indexMemoryTagCreatesEntry() {
        tracker.indexMemoryTag("java", 42);
        int[] memories = tracker.findMemoriesByTag(CoActivationRecordMemory.hashTag("java"), 10);
        assertThat(memories).containsExactly(42);
    }

    @Test
    void indexMemoryTagMultipleMemories() {
        tracker.indexMemoryTag("java", 1);
        tracker.indexMemoryTag("java", 2);
        tracker.indexMemoryTag("java", 3);
        int[] memories = tracker.findMemoriesByTag(CoActivationRecordMemory.hashTag("java"), 10);
        assertThat(memories).containsExactlyInAnyOrder(1, 2, 3);
    }

    @Test
    void indexMemoryTagDeduplicates() {
        tracker.indexMemoryTag("java", 42);
        tracker.indexMemoryTag("java", 42); // duplicate
        int[] memories = tracker.findMemoriesByTag(CoActivationRecordMemory.hashTag("java"), 10);
        assertThat(memories).containsExactly(42);
    }

    @Test
    void findMemoriesByTagRespectsLimit() {
        for (int i = 0; i < 20; i++) {
            tracker.indexMemoryTag("java", i);
        }
        int[] memories = tracker.findMemoriesByTag(CoActivationRecordMemory.hashTag("java"), 5);
        assertThat(memories).hasSize(5);
    }

    @Test
    void findMemoriesByTagReturnsEmptyForUnknownTag() {
        int[] memories = tracker.findMemoriesByTag(CoActivationRecordMemory.hashTag("unknown"), 10);
        assertThat(memories).isEmpty();
    }

    @Test
    void deindexMemoryRemovesFromAllTags() {
        tracker.indexMemoryTag("java", 42);
        tracker.indexMemoryTag("python", 42);
        tracker.indexMemoryTag("java", 99);

        tracker.deindexMemory(42);

        int[] javaMemories = tracker.findMemoriesByTag(CoActivationRecordMemory.hashTag("java"), 10);
        assertThat(javaMemories).containsExactly(99);

        int[] pythonMemories = tracker.findMemoriesByTag(CoActivationRecordMemory.hashTag("python"), 10);
        assertThat(pythonMemories).isEmpty();
    }

    // ── Inverted Index Metrics ──

    @Test
    void invertedIndexCountsAreCorrect() {
        tracker.indexMemoryTag("java", 1);
        tracker.indexMemoryTag("java", 2);
        tracker.indexMemoryTag("python", 3);

        assertThat(tracker.invertedIndexTagCount()).isEqualTo(2); // java, python
        assertThat(tracker.invertedIndexEntryCount()).isEqualTo(3); // 2 java + 1 python
    }

    // ── Cross-Capture Traversal Tests ──

    @Test
    void crossCaptureTraversalFindsRelatedMemories() {
        // Build co-occurrence data: java <-> performance (5x), java <-> gc (3x)
        for (int i = 0; i < 5; i++) tracker.recordCoActivation("java", "performance");
        for (int i = 0; i < 3; i++) tracker.recordCoActivation("java", "gc");

        // Index memories with related tags
        tracker.indexMemoryTag("performance", 10);
        tracker.indexMemoryTag("performance", 11);
        tracker.indexMemoryTag("gc", 20);
        tracker.indexMemoryTag("java", 30);

        // Traverse from "java" — should find memories tagged "performance" and "gc"
        var candidates = tracker.crossCaptureTraversal(List.of("java"), 5, 10);

        assertThat(candidates).isNotEmpty();

        // Verify discovered memory slot indices include those from related tags
        Set<Integer> discoveredSlots = new java.util.HashSet<>();
        candidates.forEach(c -> discoveredSlots.add(c.memorySlotIndex()));

        assertThat(discoveredSlots).contains(10, 11, 20); // from performance and gc tags
    }

    @Test
    void crossCaptureTraversalScoresUsesFanFactor() {
        // One memory with "rare-tag", many memories with "common-tag"
        tracker.recordCoActivation("query", "rare-tag");
        tracker.recordCoActivation("query", "common-tag");

        tracker.indexMemoryTag("rare-tag", 1);
        for (int i = 10; i < 20; i++) tracker.indexMemoryTag("common-tag", i);

        var candidates = tracker.crossCaptureTraversal(List.of("query"), 5, 20);

        assertThat(candidates).isNotEmpty();

        // Find the candidate from rare-tag (memory 1) and a common-tag candidate
        var rareCandidate = candidates.stream()
                .filter(c -> c.memorySlotIndex() == 1)
                .findFirst();
        var commonCandidate = candidates.stream()
                .filter(c -> c.memorySlotIndex() == 10)
                .findFirst();

        assertThat(rareCandidate).isPresent();
        assertThat(commonCandidate).isPresent();

        // Rare tag should have higher score due to lower fan-factor (1/√1 vs 1/√10)
        assertThat(rareCandidate.get().score()).isGreaterThan(commonCandidate.get().score());
    }

    @Test
    void crossCaptureTraversalDeduplicatesAcrossQueryTags() {
        tracker.recordCoActivation("tag-a", "shared");
        tracker.recordCoActivation("tag-b", "shared");

        tracker.indexMemoryTag("shared", 42);

        // Both tag-a and tag-b point to "shared" which points to memory 42
        var candidates = tracker.crossCaptureTraversal(List.of("tag-a", "tag-b"), 5, 10);

        // Memory 42 should appear only once despite being found via two query tags
        long count = candidates.stream().filter(c -> c.memorySlotIndex() == 42).count();
        assertThat(count).isEqualTo(1);
    }

    @Test
    void crossCaptureTraversalGracefulDegradationOnEmptyGraph() {
        // No co-occurrence data recorded, no inverted index entries
        var candidates = tracker.crossCaptureTraversal(List.of("java"), 5, 10);
        assertThat(candidates).isEmpty();
    }

    @Test
    void crossCaptureTraversalWithNullOrEmptyQueryTags() {
        assertThat(tracker.crossCaptureTraversal(null, 5, 10)).isEmpty();
        assertThat(tracker.crossCaptureTraversal(List.of(), 5, 10)).isEmpty();
    }

    // ── Rebuild Inverted Index Test ──

    @Test
    void rebuildInvertedIndexClearsAndRepopulates() {
        // Add some initial entries
        tracker.indexMemoryTag("java", 1);
        tracker.indexMemoryTag("python", 2);
        assertThat(tracker.invertedIndexTagCount()).isEqualTo(2);

        // Rebuild with new data
        Map<Integer, Collection<String>> tagSets = Map.of(
                10, List.of("go", "performance"),
                20, List.of("go", "concurrency")
        );
        tracker.rebuildInvertedIndex(tagSets);

        // Old entries should be gone
        assertThat(tracker.findMemoriesByTag(CoActivationRecordMemory.hashTag("java"), 10)).isEmpty();
        assertThat(tracker.findMemoriesByTag(CoActivationRecordMemory.hashTag("python"), 10)).isEmpty();

        // New entries should exist
        assertThat(tracker.findMemoriesByTag(CoActivationRecordMemory.hashTag("go"), 10))
                .containsExactlyInAnyOrder(10, 20);
        assertThat(tracker.findMemoriesByTag(CoActivationRecordMemory.hashTag("performance"), 10))
                .containsExactly(10);
    }

    // ── Related Tags Traversal Tests ──

    @Test
    void traverseRelatedTagsReturnsSortedByCoOccurrence() {
        for (int i = 0; i < 10; i++) tracker.recordCoActivation("java", "performance");
        for (int i = 0; i < 5; i++) tracker.recordCoActivation("java", "gc");
        for (int i = 0; i < 2; i++) tracker.recordCoActivation("java", "threads");

        var neighbors = tracker.traverseRelatedTags(CoActivationRecordMemory.hashTag("java"), 3);

        assertThat(neighbors).hasSize(3);
        assertThat(neighbors.get(0).tagName()).isEqualTo("performance");
        assertThat(neighbors.get(0).coOccurrenceCount()).isEqualTo(10);
        assertThat(neighbors.get(1).tagName()).isEqualTo("gc");
        assertThat(neighbors.get(2).tagName()).isEqualTo("threads");
    }

    @Test
    void traverseRelatedTagsRespectsMaxNeighbors() {
        for (int i = 0; i < 10; i++) {
            tracker.recordCoActivation("java", "tag-" + i);
        }

        var neighbors = tracker.traverseRelatedTags(CoActivationRecordMemory.hashTag("java"), 3);
        assertThat(neighbors).hasSize(3);
    }
}
