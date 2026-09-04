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

import com.spectrayan.spector.memory.model.ConversationRole;
import com.spectrayan.spector.memory.model.EngramSource;
import com.spectrayan.spector.memory.model.SourceModality;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DisplayName("Per-Tier Header Layouts & Fields Unit Tests (ADR-0030)")
class PerTierHeaderLayoutTest {

    @Test
    @DisplayName("Verify SemanticHeaderLayout and ProceduralHeaderLayout inherit and operate correctly")
    void testSemanticAndProceduralHeaderLayout() {
        SemanticHeaderLayout semantic = SemanticHeaderLayout.defaultLayout();
        ProceduralHeaderLayout procedural = ProceduralHeaderLayout.defaultLayout();

        assertThat(semantic).isInstanceOf(SemanticProceduralHeaderLayout.class);
        assertThat(procedural).isInstanceOf(SemanticProceduralHeaderLayout.class);
        assertThat(semantic.headerBytes()).isEqualTo(64);
        assertThat(procedural.headerBytes()).isEqualTo(64);

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg = arena.allocate(128, 64);
            long off = 0L;

            semantic.writeSynapticTags(seg, off, 0x1111222233334444L, 0x5555666677778888L);
            assertThat(semantic.readSynapticTagsLo(seg, off)).isEqualTo(0x1111222233334444L);
            assertThat(semantic.readSynapticTagsHi(seg, off)).isEqualTo(0x5555666677778888L);
            assertThat(semantic.readSynapticTags(seg, off)).isEqualTo(0x1111222233334444L);

            semantic.mergeSynapticTags128(seg, off, 0x0000000000000001L, 0x0000000000000002L);
            assertThat(semantic.readSynapticTagsLo(seg, off)).isEqualTo(0x1111222233334445L);
            assertThat(semantic.readSynapticTagsHi(seg, off)).isEqualTo(0x555566667777888AL);
        }
    }

    @Test
    @DisplayName("Verify EpisodicHeaderLayout honest fields and record-offset translation")
    void testEpisodicHeaderLayout() {
        EpisodicHeaderLayout episodic = EpisodicHeaderLayout.defaultLayout();
        assertThat(episodic).isInstanceOf(EncodingHeaderLayout.class);
        assertThat(episodic.headerBytes()).isEqualTo(64);

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg = arena.allocate(256, 64);
            long recordOff = 0L;
            long headerOff = recordOff + EpisodeLayout.PREFIX_BYTES;

            // Prefix fields
            seg.set(java.lang.foreign.ValueLayout.JAVA_INT_UNALIGNED, recordOff, 120);
            seg.set(java.lang.foreign.ValueLayout.JAVA_INT_UNALIGNED, recordOff + 4, 7);
            seg.set(java.lang.foreign.ValueLayout.JAVA_INT_UNALIGNED, recordOff + 8, 0xCAFEBABE);
            seg.set(java.lang.foreign.ValueLayout.JAVA_INT_UNALIGNED, recordOff + 12, EpisodeLayout.MAGIC);

            assertThat(episodic.readPayloadBytes(seg, recordOff)).isEqualTo(120);
            assertThat(episodic.readSequenceId(seg, recordOff)).isEqualTo(7);
            assertThat(episodic.readChecksum(seg, recordOff)).isEqualTo(0xCAFEBABE);
            assertThat(episodic.readMagic(seg, recordOff)).isEqualTo(EpisodeLayout.MAGIC);
            assertThat(episodic.isOptionBRecord(seg, recordOff)).isTrue();

            // Honest episodic fields (at header offset)
            long sessionId = 123456789012345L;
            short modelId = 42;
            byte role = (byte) ConversationRole.USER.ordinal();
            episodic.writeSessionId(seg, headerOff, sessionId);
            episodic.writeModelId(seg, headerOff, modelId);
            episodic.writeRole(seg, headerOff, role);
            episodic.writeEpisodicTags(seg, headerOff, 0xAABBCCDDEEFF0011L, 0x2233445566778899L);

            assertThat(episodic.readSessionId(seg, headerOff)).isEqualTo(sessionId);
            assertThat(episodic.readModelId(seg, headerOff)).isEqualTo(modelId);
            assertThat(episodic.readRole(seg, headerOff)).isEqualTo(role);
            assertThat(episodic.readEpisodicTagsLo(seg, headerOff)).isEqualTo(0xAABBCCDDEEFF0011L);
            assertThat(episodic.readEpisodicTagsHi(seg, headerOff)).isEqualTo(0x2233445566778899L);

            // Record-level honest episodic operations
            episodic.writeSessionIdRecord(seg, recordOff, sessionId + 1);
            assertThat(episodic.readSessionIdRecord(seg, recordOff)).isEqualTo(sessionId + 1);
            episodic.writeModelIdRecord(seg, recordOff, (short) 99);
            assertThat(episodic.readModelIdRecord(seg, recordOff)).isEqualTo((short) 99);
            episodic.writeRoleRecord(seg, recordOff, (byte) ConversationRole.ASSISTANT.ordinal());
            assertThat(episodic.readRoleRecord(seg, recordOff)).isEqualTo((byte) ConversationRole.ASSISTANT.ordinal());
            episodic.writeEpisodicTagsRecord(seg, recordOff, 0x1111L, 0x2222L);
            assertThat(episodic.readEpisodicTagsLoRecord(seg, recordOff)).isEqualTo(0x1111L);
            assertThat(episodic.readEpisodicTagsHiRecord(seg, recordOff)).isEqualTo(0x2222L);

            // Record-level operations (translates recordOffset to headerOffset automatically)
            episodic.writeImportanceRecord(seg, recordOff, 0.95f);
            assertThat(episodic.readImportanceRecord(seg, recordOff)).isCloseTo(0.95f, within(1e-5f));

            episodic.writeValenceRecord(seg, recordOff, (byte) 25);
            assertThat(episodic.readValenceRecord(seg, recordOff)).isEqualTo((byte) 25);

            episodic.writeArousalRecord(seg, recordOff, (byte) 100);
            assertThat(episodic.readArousalRecord(seg, recordOff)).isEqualTo((byte) 100);

            assertThat(episodic.isTombstonedRecord(seg, recordOff)).isFalse();
            episodic.tombstoneRecord(seg, recordOff);
            assertThat(episodic.isTombstonedRecord(seg, recordOff)).isTrue();

            assertThat(episodic.isConsolidatedRecord(seg, recordOff)).isFalse();
            episodic.markConsolidatedRecord(seg, recordOff);
            assertThat(episodic.isConsolidatedRecord(seg, recordOff)).isTrue();

            episodic.markResolvedRecord(seg, recordOff);
            assertThat(EncodingHeaderFields.isResolved(episodic.readFlagsRecord(seg, recordOff))).isTrue();
            episodic.markUnresolvedRecord(seg, recordOff);
            assertThat(EncodingHeaderFields.isResolved(episodic.readFlagsRecord(seg, recordOff))).isFalse();
        }
    }

    @Test
    @DisplayName("Verify WorkingHeaderLayout inherits and dimensions")
    void testWorkingHeaderLayout() {
        WorkingHeaderLayout working = WorkingHeaderLayout.defaultLayout();
        assertThat(working).isInstanceOf(EncodingHeaderLayout.class);
        assertThat(working.headerBytes()).isEqualTo(64);
        assertThat(working.version()).isEqualTo(2);
    }

    @Test
    @DisplayName("Verify per-tier RegionLayout classes have uniform layoutId and correct metadata")
    void testPerTierRegionLayouts() {
        int vecBytes = 128;
        SemanticLayout semantic = new SemanticLayout(vecBytes);
        ProceduralLayout procedural = new ProceduralLayout(vecBytes);
        WorkingLayout working = new WorkingLayout(vecBytes);
        EpisodicLayout episodic = EpisodicLayout.defaultLayout();

        // Fixed-stride tiers share layout ID 0x434F4700
        assertThat(semantic.layoutId()).isEqualTo(FixedEngramLayout.LAYOUT_ID);
        assertThat(procedural.layoutId()).isEqualTo(FixedEngramLayout.LAYOUT_ID);
        assertThat(working.layoutId()).isEqualTo(FixedEngramLayout.LAYOUT_ID);

        // Stride is 64 + vecBytes
        assertThat(semantic.stride()).isEqualTo(64 + vecBytes);
        assertThat(procedural.stride()).isEqualTo(64 + vecBytes);
        assertThat(working.stride()).isEqualTo(64 + vecBytes);
        assertThat(semantic.recordStride()).isEqualTo(64 + vecBytes);

        // Vector offset is 64
        assertThat(semantic.vectorOffset(0)).isEqualTo(64);
        assertThat(semantic.vectorOffset(100)).isEqualTo(164);

        // Specific header layout types
        assertThat(semantic.headerLayout()).isInstanceOf(SemanticHeaderLayout.class);
        assertThat(procedural.headerLayout()).isInstanceOf(ProceduralHeaderLayout.class);
        assertThat(working.headerLayout()).isInstanceOf(WorkingHeaderLayout.class);

        // Episodic layout
        assertThat(episodic.layoutId()).isEqualTo(EpisodeLayout.LAYOUT_ID);
        assertThat(episodic.recordStride()).isEqualTo(0);
        assertThat(episodic.crcEnabled()).isTrue();
        assertThat(episodic.headerLayout()).isInstanceOf(EpisodicHeaderLayout.class);

        // EngramLayout implements FixedEngramLayout
        EngramLayout legacy = new EngramLayout(vecBytes);
        assertThat(legacy).isInstanceOf(FixedEngramLayout.class);
        assertThat(legacy.layoutId()).isEqualTo(FixedEngramLayout.LAYOUT_ID);
    }
}
