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

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.api.*;

import com.spectrayan.spector.memory.pathway.*;
import com.spectrayan.spector.memory.persist.*;
import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.cortex.CognitiveMemoryRouter;
import com.spectrayan.spector.memory.cortex.PartitionHandle;
import com.spectrayan.spector.memory.cortex.PartitionSummary;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.pipeline.pruning.DefaultPartitionPruner;
import com.spectrayan.spector.memory.pipeline.pruning.PartitionPruner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for query-time partition pruning and partition summaries (#447).
 */
class PartitionPrunerTest {

    private final DefaultPartitionPruner pruner = new DefaultPartitionPruner();

    private PartitionHandle createHandle(int seq, long minTs, long maxTs, long tagMask,
                                          int semCount, int epiCount, int procCount, boolean writable) {
        PartitionSummary summary = new PartitionSummary(
                seq, minTs, maxTs, tagMask, semCount, epiCount, procCount, writable);
        CognitiveMemoryRouter router = mock(CognitiveMemoryRouter.class);
        return new PartitionHandle(seq, null, router, null, writable, null, summary);
    }

    @Nested
    @DisplayName("Temporal Gating")
    class TemporalGatingTests {

        @Test
        @DisplayName("Should prune partition entirely before minTimestamp")
        void shouldPrunePartitionBeforeMinTimestamp() {
            // Partition with records between 1,000ms and 2,000ms
            PartitionHandle handle = createHandle(0, 1000L, 2000L, 0L, 10, 0, 0, false);
            RecallOptions options = RecallOptions.builder()
                    .minTimestamp(2500L)
                    .build();

            assertThat(pruner.shouldPrune(handle, options, null)).isTrue();
        }

        @Test
        @DisplayName("Should prune partition entirely after maxTimestamp")
        void shouldPrunePartitionAfterMaxTimestamp() {
            // Partition with records between 5,000ms and 6,000ms
            PartitionHandle handle = createHandle(1, 5000L, 6000L, 0L, 10, 0, 0, false);
            RecallOptions options = RecallOptions.builder()
                    .maxTimestamp(4000L)
                    .build();

            assertThat(pruner.shouldPrune(handle, options, null)).isTrue();
        }

        @Test
        @DisplayName("Should include partition overlapping temporal window")
        void shouldIncludePartitionOverlappingWindow() {
            // Partition with records between 2,000ms and 5,000ms
            PartitionHandle handle = createHandle(0, 2000L, 5000L, 0L, 10, 0, 0, false);
            RecallOptions options = RecallOptions.builder()
                    .minTimestamp(3000L)
                    .maxTimestamp(4000L)
                    .build();

            assertThat(pruner.shouldPrune(handle, options, null)).isFalse();
        }

        @Test
        @DisplayName("Should include active partition with unbounded future timestamp")
        void shouldIncludeActivePartition() {
            PartitionHandle active = createHandle(2, 6000L, Long.MAX_VALUE, 0L, 5, 0, 0, true);
            RecallOptions options = RecallOptions.builder()
                    .minTimestamp(7000L)
                    .build();

            assertThat(pruner.shouldPrune(active, options, null)).isFalse();
        }
    }

    @Nested
    @DisplayName("Synaptic Tag Bloom Filter Gating")
    class TagGatingTests {

        @Test
        @DisplayName("Should prune partition with zero tag mask overlap in standard recall")
        void shouldPruneZeroTagOverlap() {
            long partitionTags = 0b0000_1010L; // tags at bit 1 and bit 3
            PartitionHandle handle = createHandle(0, 0L, 1000L, partitionTags, 10, 0, 0, false);

            RecallOptions options = RecallOptions.builder()
                    .synapticTagMask(0b0001_0100L) // query for bit 2 and bit 4
                    .build();

            assertThat(pruner.shouldPrune(handle, options, null)).isTrue();
        }

        @Test
        @DisplayName("Should include partition with matching tag overlap")
        void shouldIncludeMatchingTagOverlap() {
            long partitionTags = 0b0000_1010L; // tags at bit 1 and bit 3
            PartitionHandle handle = createHandle(0, 0L, 1000L, partitionTags, 10, 0, 0, false);

            RecallOptions options = RecallOptions.builder()
                    .synapticTagMask(0b0000_0010L) // query for bit 1
                    .build();

            assertThat(pruner.shouldPrune(handle, options, null)).isFalse();
        }

        @Test
        @DisplayName("Should prune partition missing any hyperfocus tag bits")
        void shouldPruneHyperfocusSubsetMissing() {
            long partitionTags = 0b0000_1010L; // has bit 1 and 3, missing bit 0
            PartitionHandle handle = createHandle(0, 0L, 1000L, partitionTags, 10, 0, 0, false);

            RecallOptions options = RecallOptions.builder()
                    .hyperfocusMask(0b0000_1011L) // requires bit 0, 1, 3
                    .build();

            assertThat(pruner.shouldPrune(handle, options, null)).isTrue();
        }

        @Test
        @DisplayName("Should include partition containing all hyperfocus tag bits")
        void shouldIncludeHyperfocusExactSubset() {
            long partitionTags = 0b0000_1111L; // has bits 0, 1, 2, 3
            PartitionHandle handle = createHandle(0, 0L, 1000L, partitionTags, 10, 0, 0, false);

            RecallOptions options = RecallOptions.builder()
                    .hyperfocusMask(0b0000_1010L) // requires bits 1 and 3
                    .build();

            assertThat(pruner.shouldPrune(handle, options, null)).isFalse();
        }
    }

    @Nested
    @DisplayName("Empty & Tier Gating")
    class TierGatingTests {

        @Test
        @DisplayName("Should prune partition with 0 visible records across all tiers")
        void shouldPruneEmptyPartition() {
            PartitionHandle empty = createHandle(0, 0L, 1000L, 0L, 0, 0, 0, false);
            assertThat(pruner.shouldPrune(empty, RecallOptions.DEFAULT, null)).isTrue();
        }

        @Test
        @DisplayName("Should prune partition when requested tier has 0 records")
        void shouldPruneMismatchedTier() {
            // Partition with only semantic records
            PartitionHandle semanticOnly = createHandle(0, 0L, 1000L, 0L, 5, 0, 0, false);

            // Query targeting only EPISODIC memory
            assertThat(pruner.shouldPrune(semanticOnly, RecallOptions.DEFAULT,
                    new MemoryType[]{MemoryType.EPISODIC})).isTrue();

            // Query targeting SEMANTIC memory
            assertThat(pruner.shouldPrune(semanticOnly, RecallOptions.DEFAULT,
                    new MemoryType[]{MemoryType.SEMANTIC})).isFalse();
        }
    }

    @Nested
    @DisplayName("Multi-Partition Fan-Out Pruning")
    class MultiPartitionPruneListTests {

        @Test
        @DisplayName("Should filter multiple partitions down to only relevant ones")
        void shouldFilterPartitionList() {
            PartitionHandle p0 = createHandle(0, 1000L, 2000L, 0b0001L, 10, 0, 0, false);
            PartitionHandle p1 = createHandle(1, 2000L, 3000L, 0b0010L, 10, 0, 0, false);
            PartitionHandle p2 = createHandle(2, 3000L, 4000L, 0b0100L, 10, 0, 0, false);
            PartitionHandle p3 = createHandle(3, 4000L, 5000L, 0b1000L, 10, 0, 0, false);

            List<PartitionHandle> all = List.of(p0, p1, p2, p3);

            // 1. Time query: [2500, 3500] -> should keep p1 and p2 only
            RecallOptions timeQuery = RecallOptions.builder()
                    .minTimestamp(2500L)
                    .maxTimestamp(3500L)
                    .build();
            List<PartitionHandle> timePruned = pruner.prune(all, timeQuery, null, System.currentTimeMillis());
            assertThat(timePruned).containsExactly(p1, p2);

            // 2. Tag query: 0b0010 -> should keep p1 only
            RecallOptions tagQuery = RecallOptions.builder()
                    .synapticTagMask(0b0010L)
                    .build();
            List<PartitionHandle> tagPruned = pruner.prune(all, tagQuery, null, System.currentTimeMillis());
            assertThat(tagPruned).containsExactly(p1);

            // 3. Unfiltered query -> should keep all non-empty partitions
            List<PartitionHandle> unpruned = pruner.prune(all, RecallOptions.DEFAULT, null, System.currentTimeMillis());
            assertThat(unpruned).containsExactly(p0, p1, p2, p3);
        }
    }
}
