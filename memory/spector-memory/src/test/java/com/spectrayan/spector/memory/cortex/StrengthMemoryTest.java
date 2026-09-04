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
package com.spectrayan.spector.memory.cortex;

import com.spectrayan.spector.memory.kernel.MemoryId;
import com.spectrayan.spector.memory.kernel.layout.StrengthLayout;
import com.spectrayan.spector.memory.model.MemoryType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DisplayName("StrengthMemory Unit Tests")
class StrengthMemoryTest {

    @Test
    @DisplayName("Verify cumulative slot offset indexing and tier capacity bounds")
    void testCumulativeTierSlotOffsetIndexing() {
        int semCap = 100;
        int epiCap = 200;
        int procCap = 50;
        int totalCap = semCap + epiCap + procCap; // 350

        try (Arena arena = Arena.ofConfined()) {
            long totalBytes = (long) totalCap * StrengthLayout.INSTANCE.recordStride();
            MemorySegment segment = arena.allocate(totalBytes, 32);

            StrengthMemory store = new StrengthMemory(
                    MemoryId.of("default", "test-strength"),
                    StrengthLayout.INSTANCE,
                    semCap, epiCap, procCap,
                    arena, segment, 0,
                    false, null, null, false);

            assertThat(store.semanticCapacity()).isEqualTo(100);
            assertThat(store.episodicCapacity()).isEqualTo(200);
            assertThat(store.proceduralCapacity()).isEqualTo(50);
            assertThat(store.capacity()).isEqualTo(350);

            // Verify offsets
            assertThat(store.strengthOffset(MemoryType.SEMANTIC, 0)).isEqualTo(0);
            assertThat(store.strengthOffset(MemoryType.SEMANTIC, 99)).isEqualTo(99 * 96);
            assertThat(store.strengthOffset(MemoryType.EPISODIC, 0)).isEqualTo(100 * 96);
            assertThat(store.strengthOffset(MemoryType.EPISODIC, 199)).isEqualTo(299 * 96);
            assertThat(store.strengthOffset(MemoryType.PROCEDURAL, 0)).isEqualTo(300 * 96);
            assertThat(store.strengthOffset(MemoryType.PROCEDURAL, 49)).isEqualTo(349 * 96);
        }
    }

    @Test
    @DisplayName("Verify multi-tier record recall, CAS mutations, and isolation")
    void testMultiTierRecordRecallAndMutations() {
        int semCap = 10;
        int epiCap = 10;
        int procCap = 10;
        int totalCap = semCap + epiCap + procCap;

        try (Arena arena = Arena.ofConfined()) {
            long totalBytes = (long) totalCap * StrengthLayout.INSTANCE.recordStride();
            MemorySegment segment = arena.allocate(totalBytes, 32);

            StrengthMemory store = new StrengthMemory(
                    MemoryId.of("default", "test-strength"),
                    StrengthLayout.INSTANCE,
                    semCap, epiCap, procCap,
                    arena, segment, 0,
                    false, null, null, false);

            long creationMs = 1716900000000L;
            long recallMs = creationMs + 5000L;

            // Initialize records across tiers
            store.initializeDefault(MemoryType.SEMANTIC, 2, 4.0f);
            store.initializeDefault(MemoryType.EPISODIC, 5, 2.5f);

            // Record recall on episodic slot 5
            store.incrementAgentRecallCount(MemoryType.EPISODIC, 5);
            store.recordRecall(MemoryType.EPISODIC, 5, creationMs, recallMs, (byte) 1, 0);

            var epiAudit = store.readAuditRecord(MemoryType.EPISODIC, 5);
            assertThat(epiAudit.memoryType()).isEqualTo(MemoryType.EPISODIC);
            assertThat(epiAudit.agentRecallCount()).isEqualTo(1);
            assertThat(epiAudit.lastRecallProfile()).isEqualTo((byte) 1);
            assertThat(epiAudit.lastRecallTimestamp()).isEqualTo(recallMs);

            // Ensure semantic slot 2 remained unaffected
            var semAudit = store.readAuditRecord(MemoryType.SEMANTIC, 2);
            assertThat(semAudit.memoryType()).isEqualTo(MemoryType.SEMANTIC);
            assertThat(semAudit.agentRecallCount()).isEqualTo(0);
            assertThat(semAudit.effectiveImportance()).isCloseTo(4.0f, within(1e-5f));
        }
    }
}
