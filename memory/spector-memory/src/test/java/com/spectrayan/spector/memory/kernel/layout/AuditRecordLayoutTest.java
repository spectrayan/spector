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
package com.spectrayan.spector.memory.kernel.layout;

import com.spectrayan.spector.memory.model.MemoryType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DisplayName("AuditRecordLayout Unit Tests")
class AuditRecordLayoutTest {

    private final AuditRecordLayout layout = AuditRecordLayout.INSTANCE;

    @Test
    @DisplayName("Verify AuditRecordLayout constants, 96-byte stride, and schema version (1)")
    void testMetadataAndDimensions() {
        assertThat(layout.layoutId()).isEqualTo(0x41554454); // 'AUDT'
        assertThat(layout.schemaVersion()).isEqualTo(1);
        assertThat(layout.recordStride()).isEqualTo(96);
        assertThat(layout.name()).isEqualTo("AuditRecordLayout");
    }

    @Test
    @DisplayName("Verify AuditRecord round-trip and MemoryType ordinal embedding")
    void testAuditRecordRoundTrip() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(192, 32);
            long offset = 0;

            AuditRecordLayout.AuditRecord record = new AuditRecordLayout.AuditRecord(
                    (byte) MemoryType.SEMANTIC.ordinal(),
                    (byte) 2,
                    (byte) 10,
                    15,
                    3,
                    4.5f,
                    1.2f,
                    12345,
                    1716900001000L,
                    1716900005000L,
                    new int[]{10, 20, 30, 40, 50, 60, 70, 80},
                    55L
            );

            layout.writeRecord(segment, offset, record);

            AuditRecordLayout.AuditRecord read = layout.readRecord(segment, offset);
            assertThat(read.memoryType()).isEqualTo(MemoryType.SEMANTIC);
            assertThat(read.agentRecallCount()).isEqualTo(15);
            assertThat(read.spectorRecallCount()).isEqualTo(3);
            assertThat(read.lastRecallTimestamp()).isEqualTo(1716900005000L);
            assertThat(read.effectiveImportance()).isCloseTo(4.5f, within(1e-5f));
            assertThat(read.storageStrength()).isCloseTo(1.2f, within(1e-5f));
            assertThat(read.lastAgentHash()).isEqualTo(12345);
            assertThat(read.lastRecallProfile()).isEqualTo((byte) 2);
            assertThat(read.lastAutoLtp()).isEqualTo(1716900001000L);
            assertThat(read.actRTimestamps()).containsExactly(10, 20, 30, 40, 50, 60, 70, 80);
            assertThat(read.reconsolidationDelta()).isEqualTo(55L);
        }
    }

    @Test
    @DisplayName("Verify atomic VarHandle increments and CAS operations on AuditRecord")
    void testAtomicOperations() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(192, 32);
            long offset = 0;

            layout.initializeDefaultRecord(segment, offset, MemoryType.EPISODIC, 3.5f);

            assertThat(layout.readAgentRecallCount(segment, offset)).isEqualTo(0);
            assertThat(layout.readSpectorRecallCount(segment, offset)).isEqualTo(0);
            assertThat(layout.readEffectiveImportance(segment, offset)).isCloseTo(3.5f, within(1e-5f));
            assertThat(layout.readStorageStrength(segment, offset)).isCloseTo(1.0f, within(1e-5f));

            // Atomic increments (getAndAdd returns prior value)
            int prevAgentCount = layout.incrementAgentRecallCount(segment, offset);
            assertThat(prevAgentCount).isEqualTo(0);
            assertThat(layout.readAgentRecallCount(segment, offset)).isEqualTo(1);

            int prevSpectorCount = layout.incrementSpectorRecallCount(segment, offset);
            assertThat(prevSpectorCount).isEqualTo(0);
            assertThat(layout.readSpectorRecallCount(segment, offset)).isEqualTo(1);

            // CAS operations
            float updatedImp = layout.casEffectiveImportance(segment, offset, imp -> imp + 1.25f);
            assertThat(updatedImp).isCloseTo(4.75f, within(1e-5f));
            assertThat(layout.readEffectiveImportance(segment, offset)).isCloseTo(4.75f, within(1e-5f));

            float updatedStorage = layout.casStorageStrength(segment, offset, str -> str + 0.5f);
            assertThat(updatedStorage).isCloseTo(1.5f, within(1e-5f));
            assertThat(layout.readStorageStrength(segment, offset)).isCloseTo(1.5f, within(1e-5f));
        }
    }

    @Test
    @DisplayName("Verify 8-slot ACT-R circular ring buffer and base-level activation calculation")
    void testActRRingBufferAndActivation() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(192, 32);
            long offset = 0;
            long creationMs = 1716900000000L;

            layout.initializeDefaultRecord(segment, offset, MemoryType.SEMANTIC, 5.0f);

            // Fill 8 recall slots
            for (int i = 1; i <= 8; i++) {
                layout.recordActRRecall(segment, offset, creationMs, creationMs + (i * 1000L));
            }

            int[] timestamps = layout.readActRTimestamps(segment, offset);
            assertThat(timestamps).containsExactly(1, 2, 3, 4, 5, 6, 7, 8);

            // Overwrite oldest slot with 9th recall
            layout.recordActRRecall(segment, offset, creationMs, creationMs + 9000L);
            int[] updatedTimestamps = layout.readActRTimestamps(segment, offset);
            // Slot 0 (which had 1) should now have 9
            assertThat(updatedTimestamps[0]).isEqualTo(9);

            // Base level activation computation
            long nowMs = creationMs + 10_000L;
            float activation = layout.computeActRActivation(segment, offset, creationMs, nowMs);
            assertThat(activation).isGreaterThan(0.0f).isLessThanOrEqualTo(1.0f);
        }
    }
}
