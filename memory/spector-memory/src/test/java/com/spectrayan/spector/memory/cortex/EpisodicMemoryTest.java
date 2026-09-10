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
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.kernel.store.EpisodicMemory;

import com.spectrayan.spector.kernel.engram.EncodingHeader;

import com.spectrayan.spector.kernel.store.EngramRegion;

import com.spectrayan.spector.kernel.region.RegionPreamble;
import com.spectrayan.spector.kernel.engram.field.EncodingHeaderFields;
import com.spectrayan.spector.kernel.store.codec.EpisodeCodec;
import com.spectrayan.spector.kernel.engram.EpisodicHeaderLayout;
import com.spectrayan.spector.kernel.layout.EpisodicLayout;
import com.spectrayan.spector.kernel.api.ConversationRole;
import com.spectrayan.spector.kernel.api.EngramSource;
import com.spectrayan.spector.kernel.api.EpisodeRecord;
import com.spectrayan.spector.kernel.api.SourceModality;
import com.spectrayan.spector.memory.session.EpisodicSessionIndex;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.foreign.ValueLayout;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("EpisodicMemory Tests (Option B & Dual-Read Parity)")
class EpisodicMemoryTest {

    private EpisodicMemory episodicMemory;
    private static final long CAPACITY = 1024 * 1024; // 1MB for tests

    @BeforeEach
    void setUp() {
        episodicMemory = new EpisodicMemory(CAPACITY);
    }

    @AfterEach
    void tearDown() {
        if (episodicMemory != null) {
            episodicMemory.close();
        }
    }

    @Test
    @DisplayName("Should successfully append Option B turn and read it back with affect")
    void shouldAppendAndReadTurn_whenRoundtrip() {
        byte[] body = "Hello, world!".getBytes();
        long offset = episodicMemory.appendTurn(
                ConversationRole.USER, 1, 1000L, 123L,
                body, (short) 1, 10, 0, 0, 999L, (short) 1, SourceModality.TEXT,
                0.85f, (byte) 20, (byte) -15, EngramSource.EXPERIENCED
        );

        EpisodeRecord record = episodicMemory.readTurn(offset, true);

        assertAll("Record contents",
                () -> assertEquals(ConversationRole.USER, record.role()),
                () -> assertEquals(1, record.sequenceId()),
                () -> assertEquals(1000L, record.timestampMs()),
                () -> assertEquals(123L, record.sessionId()),
                () -> assertEquals(body.length, record.bodyLength()),
                () -> assertArrayEquals(body, record.body()),
                () -> assertEquals((short) 1, record.modelId()),
                () -> assertEquals(10, record.tokenIn()),
                () -> assertEquals(0, record.tokenOut()),
                () -> assertEquals(0, record.latencyMs()),
                () -> assertEquals(999L, record.userId()),
                () -> assertEquals((short) 1, record.soulVersion()),
                () -> assertEquals(SourceModality.TEXT, record.modality()),
                () -> assertEquals(0.85f, record.importance(), 0.001f),
                () -> assertEquals((byte) 20, record.valence()),
                () -> assertEquals((byte) -15, record.arousal()),
                () -> assertEquals(EngramSource.EXPERIENCED, record.source())
        );

        // Verify Option B framing
        long absoluteOffset = episodicMemory.dataOffset() + offset;
        assertTrue(EpisodicHeaderLayout.INSTANCE.isOptionBRecord(episodicMemory.segment(), absoluteOffset));
        assertEquals(EpisodicLayout.MAGIC, EpisodicHeaderLayout.INSTANCE.readMagic(episodicMemory.segment(), absoluteOffset));
        assertEquals(0.85f, EpisodicHeaderLayout.INSTANCE.readImportanceRecord(episodicMemory.segment(), absoluteOffset), 0.001f);
    }

    @Test
    @DisplayName("Should append and read multiple turns in sequence")
    void shouldAppendAndReadMultiple_whenInSequence() {
        long offset1 = episodicMemory.appendTurn(ConversationRole.USER, 1, 1000L, 123L,
                "msg1".getBytes(), (short) 1, 1, 1, 1, 999L, (short) 1, SourceModality.TEXT);
        long offset2 = episodicMemory.appendTurn(ConversationRole.ASSISTANT, 2, 2000L, 123L,
                "msg2".getBytes(), (short) 1, 1, 1, 1, 999L, (short) 1, SourceModality.TEXT);

        EpisodeRecord rec1 = episodicMemory.readTurn(offset1, true);
        EpisodeRecord rec2 = episodicMemory.readTurn(offset2, true);

        assertEquals("msg1", new String(rec1.body()));
        assertEquals("msg2", new String(rec2.body()));
        assertTrue(offset2 > offset1);
    }

    @Test
    @DisplayName("Should read multiple turns from offsets")
    void shouldReadTurns_whenOffsetsProvided() {
        long offset1 = episodicMemory.appendTurn(ConversationRole.USER, 1, 1000L, 123L,
                "msg1".getBytes(), (short) 1, 1, 1, 1, 999L, (short) 1, SourceModality.TEXT);
        long offset2 = episodicMemory.appendTurn(ConversationRole.ASSISTANT, 2, 2000L, 123L,
                "msg2".getBytes(), (short) 1, 1, 1, 1, 999L, (short) 1, SourceModality.TEXT);

        List<EpisodeRecord> records = episodicMemory.readTurns(List.of(offset1, offset2), true);
        
        assertEquals(2, records.size());
        assertEquals("msg1", new String(records.get(0).body()));
        assertEquals("msg2", new String(records.get(1).body()));
    }

    @Test
    @DisplayName("Should tombstone record correctly")
    void shouldTombstone_whenRequested() {
        long offset = episodicMemory.appendTurn(ConversationRole.USER, 1, 1000L, 123L,
                "msg1".getBytes(), (short) 1, 1, 1, 1, 999L, (short) 1, SourceModality.TEXT);

        assertFalse(episodicMemory.readTurn(offset, false).isTombstoned());
        
        episodicMemory.tombstone(offset);
        
        assertTrue(episodicMemory.readTurn(offset, false).isTombstoned());
        
        // readTurns filters out tombstoned records
        assertTrue(episodicMemory.readTurns(List.of(offset), false).isEmpty());
    }

    @Test
    @DisplayName("Should mark record as consolidated")
    void shouldMarkConsolidated_whenRequested() {
        long offset = episodicMemory.appendTurn(ConversationRole.USER, 1, 1000L, 123L,
                "msg1".getBytes(), (short) 1, 1, 1, 1, 999L, (short) 1, SourceModality.TEXT);

        assertFalse(episodicMemory.readTurn(offset, false).isConsolidated());
        
        episodicMemory.markConsolidated(offset);
        
        assertTrue(episodicMemory.readTurn(offset, false).isConsolidated());
    }

    @Test
    @DisplayName("Should track write position correctly")
    void shouldAdvanceWritePosition_whenTurnsAppended() {
        assertEquals(0, episodicMemory.writePosition());
        
        episodicMemory.appendTurn(ConversationRole.USER, 1, 1000L, 123L,
                "msg1".getBytes(), (short) 1, 1, 1, 1, 999L, (short) 1, SourceModality.TEXT);
        
        long pos1 = episodicMemory.writePosition();
        assertTrue(pos1 > 0);
        
        episodicMemory.appendTurn(ConversationRole.USER, 2, 2000L, 123L,
                "msg2".getBytes(), (short) 1, 1, 1, 1, 999L, (short) 1, SourceModality.TEXT);
                
        assertTrue(episodicMemory.writePosition() > pos1);
    }

    @Test
    @DisplayName("Should rebuild session index properly")
    void shouldRebuildSessionIndex_whenRequested() {
        episodicMemory.appendTurn(ConversationRole.USER, 1, 1000L, 123L,
                "msg1".getBytes(), (short) 1, 1, 1, 1, 999L, (short) 1, SourceModality.TEXT);
        episodicMemory.appendTurn(ConversationRole.ASSISTANT, 2, 2000L, 123L,
                "msg2".getBytes(), (short) 1, 1, 1, 1, 999L, (short) 1, SourceModality.TEXT);
        episodicMemory.appendTurn(ConversationRole.USER, 1, 3000L, 456L,
                "msg3".getBytes(), (short) 1, 1, 1, 1, 999L, (short) 1, SourceModality.TEXT);
                
        EpisodicSessionIndex index = new EpisodicSessionIndex();
        int indexedCount = episodicMemory.rebuildSessionIndex(index);
        
        assertEquals(3, indexedCount);
        assertEquals(2, index.sessionCount());
        assertEquals(2, index.turnCount(123L));
        assertEquals(1, index.turnCount(456L));
    }

    @Test
    @DisplayName("Should track remaining bytes accurately")
    void shouldDecreaseRemainingBytes_whenAppended() {
        long initialRemaining = episodicMemory.remainingBytes();
        assertTrue(initialRemaining > 0);
        
        episodicMemory.appendTurn(ConversationRole.USER, 1, 1000L, 123L,
                "msg1".getBytes(), (short) 1, 1, 1, 1, 999L, (short) 1, SourceModality.TEXT);
                
        assertTrue(episodicMemory.remainingBytes() < initialRemaining);
    }

    @Test
    @DisplayName("Should handle empty body gracefully")
    void shouldHandleEmptyBody_whenNullOrEmpty() {
        long offset1 = episodicMemory.appendTurn(ConversationRole.USER, 1, 1000L, 123L,
                null, (short) 1, 1, 1, 1, 999L, (short) 1, SourceModality.TEXT);
        
        EpisodeRecord rec1 = episodicMemory.readTurn(offset1, true);
        assertEquals(0, rec1.bodyLength());
        assertNull(rec1.body());
        
        long offset2 = episodicMemory.appendTurn(ConversationRole.USER, 2, 2000L, 123L,
                new byte[0], (short) 1, 1, 1, 1, 999L, (short) 1, SourceModality.TEXT);
        
        EpisodeRecord rec2 = episodicMemory.readTurn(offset2, true);
        assertEquals(0, rec2.bodyLength());
        assertNull(rec2.body());
    }



    @Test
    @DisplayName("EpisodicMemory satisfies EngramRegion contract")
    void episodicMemoryEngramContract() {
        assertEquals(com.spectrayan.spector.kernel.api.MemoryType.EPISODIC, episodicMemory.type());
        assertEquals(0, episodicMemory.visibleCount());
        assertEquals(0.0f, episodicMemory.tombstoneRatio());

        long offset = episodicMemory.appendTurn(
                ConversationRole.USER, 1, 1000L, 123L,
                "Testing EngramRegion contract".getBytes(), (short) 1, 10, 0, 0, 999L, (short) 1,
                SourceModality.TEXT, 0.75f, (byte) 15, (byte) 30, EngramSource.EXPERIENCED
        );

        assertEquals(1, episodicMemory.visibleCount());
        assertFalse(episodicMemory.isTombstoned(offset));

        var header = episodicMemory.readHeader(offset);
        assertNotNull(header);
        assertEquals(1000L, header.timestampMs());
        assertEquals(0.75f, header.importance(), 0.001f);
        assertEquals(15, header.valence());
        assertEquals(30, header.arousal());
        assertEquals(EngramSource.EXPERIENCED, header.source());

        episodicMemory.tombstone(offset);
        assertTrue(episodicMemory.isTombstoned(offset));
        assertEquals(0, episodicMemory.visibleCount());
    }

    @Test
    @DisplayName("Verify honest header de-punning and dual-read backwards compatibility (ADR-0030)")
    void testHonestHeaderDePunningAndDualRead() {
        long sessionId = 9876543210123L;
        short modelId = 77;
        byte[] body = "De-punning test message".getBytes();

        long offset = episodicMemory.appendTurn(
                ConversationRole.ASSISTANT, 3, 5000L, sessionId,
                body, modelId, 25, 50, 120, 1001L, (short) 2, SourceModality.TEXT,
                0.92f, (byte) -10, (byte) 45, EngramSource.EXPERIENCED
        );

        long absOffset = episodicMemory.dataOffset() + offset;
        var hl = episodicMemory.layout().headerLayout();

        // 1. Assert honest fields read directly from header without decoding payload
        assertEquals(sessionId, hl.readSessionIdRecord(episodicMemory.segment(), absOffset));
        assertEquals(modelId, hl.readModelIdRecord(episodicMemory.segment(), absOffset));
        assertEquals((byte) ConversationRole.ASSISTANT.ordinal(), hl.readRoleRecord(episodicMemory.segment(), absOffset));

        // 2. Assert readTurn receives honest fields
        EpisodeRecord turn = episodicMemory.readTurn(offset, true);
        assertEquals(ConversationRole.ASSISTANT, turn.role());
        assertEquals(sessionId, turn.sessionId());
        assertEquals(modelId, turn.modelId());
        assertEquals(0.92f, turn.importance(), 0.001f);

        // 3. Test dual-read fallback: write an old-style punned record (sessionId at offset 24, +16 is zero)
        int punnedPayloadBytes = body.length + EpisodeCodec.PAYLOAD_METADATA_BYTES;
        int punnedTotalSize = EpisodicLayout.FIXED_OVERHEAD_BYTES + punnedPayloadBytes;
        long punnedWriteOffset = episodicMemory.dataOffset() + episodicMemory.writePosition();

        // Write punned header where offset +16 is 0 (exactNorm), centroidId is modelId at +20, and offset +24 has sessionId
        episodicMemory.segment().set(java.lang.foreign.ValueLayout.JAVA_BYTE, punnedWriteOffset + 16, (byte) 2); // version
        episodicMemory.segment().set(java.lang.foreign.ValueLayout.JAVA_INT_UNALIGNED, punnedWriteOffset + 16 + 16, 0); // exactNorm 0.0f
        episodicMemory.segment().set(java.lang.foreign.ValueLayout.JAVA_SHORT_UNALIGNED, punnedWriteOffset + 16 + 20, modelId); // centroidId
        episodicMemory.segment().set(java.lang.foreign.ValueLayout.JAVA_SHORT_UNALIGNED, punnedWriteOffset + 16 + 22, (short) 0); // pad0
        episodicMemory.segment().set(java.lang.foreign.ValueLayout.JAVA_LONG_UNALIGNED, punnedWriteOffset + 16 + 24, sessionId); // punned at +24
        episodicMemory.segment().set(java.lang.foreign.ValueLayout.JAVA_INT_UNALIGNED, punnedWriteOffset + 12, EpisodicLayout.MAGIC);

        // Verify dual-read resolves sessionId from legacy punned offset
        assertEquals(sessionId, hl.readSessionIdRecord(episodicMemory.segment(), punnedWriteOffset));
        assertEquals(modelId, hl.readModelIdRecord(episodicMemory.segment(), punnedWriteOffset));
    }

    // ── Issue #751: Slab-scan fallback tests ──

    @Test
    @DisplayName("lastConsolidatedTurnOffsets should return empty list when memory is empty or maxTurns <= 0")
    void lastConsolidatedTurnOffsets_emptyOrInvalidArgs() {
        assertTrue(episodicMemory.lastConsolidatedTurnOffsets(123L, 5).isEmpty());
        assertTrue(episodicMemory.lastConsolidatedTurnOffsets(123L, 0).isEmpty());
        assertTrue(episodicMemory.lastConsolidatedTurnOffsets(123L, -1).isEmpty());
    }

    @Test
    @DisplayName("lastConsolidatedTurnOffsets should return empty list when turns exist but none are consolidated")
    void lastConsolidatedTurnOffsets_noConsolidatedTurns() {
        long sessionId = 100L;
        episodicMemory.appendTurn(ConversationRole.USER, 1, 1000L, sessionId, "Turn 1".getBytes(), (short) 1, 0, 0, 0, 1L, (short) 1, SourceModality.TEXT);
        episodicMemory.appendTurn(ConversationRole.ASSISTANT, 2, 2000L, sessionId, "Turn 2".getBytes(), (short) 1, 0, 0, 0, 1L, (short) 1, SourceModality.TEXT);

        List<Long> offsets = episodicMemory.lastConsolidatedTurnOffsets(sessionId, 5);
        assertTrue(offsets.isEmpty(), "Unconsolidated turns must not be returned");
    }

    @Test
    @DisplayName("lastConsolidatedTurnOffsets should return consolidated turns in chronological order capped to maxTurns")
    void lastConsolidatedTurnOffsets_withConsolidatedTurnsAndLimit() {
        long targetSession = 200L;
        long otherSession = 300L;

        // Turn 1 (target, consolidated)
        long off1 = episodicMemory.appendTurn(ConversationRole.USER, 1, 1000L, targetSession, "Target 1".getBytes(), (short) 1, 0, 0, 0, 1L, (short) 1, SourceModality.TEXT);
        episodicMemory.markConsolidated(off1);

        // Turn 2 (other session, consolidated - should be ignored)
        long offOther = episodicMemory.appendTurn(ConversationRole.USER, 2, 1500L, otherSession, "Other 1".getBytes(), (short) 1, 0, 0, 0, 1L, (short) 1, SourceModality.TEXT);
        episodicMemory.markConsolidated(offOther);

        // Turn 3 (target, consolidated)
        long off3 = episodicMemory.appendTurn(ConversationRole.ASSISTANT, 3, 2000L, targetSession, "Target 2".getBytes(), (short) 1, 0, 0, 0, 1L, (short) 1, SourceModality.TEXT);
        episodicMemory.markConsolidated(off3);

        // Turn 4 (target, unconsolidated - should be ignored)
        episodicMemory.appendTurn(ConversationRole.USER, 4, 3000L, targetSession, "Target 3".getBytes(), (short) 1, 0, 0, 0, 1L, (short) 1, SourceModality.TEXT);

        // Turn 5 (target, consolidated)
        long off5 = episodicMemory.appendTurn(ConversationRole.ASSISTANT, 5, 4000L, targetSession, "Target 4".getBytes(), (short) 1, 0, 0, 0, 1L, (short) 1, SourceModality.TEXT);
        episodicMemory.markConsolidated(off5);

        // Query all 3 consolidated turns
        List<Long> allOffsets = episodicMemory.lastConsolidatedTurnOffsets(targetSession, 10);
        assertEquals(List.of(off1, off3, off5), allOffsets, "Should return all 3 consolidated turns in chronological order");

        // Query with maxTurns = 2 (should return the last 2: off3, off5)
        List<Long> limitedOffsets = episodicMemory.lastConsolidatedTurnOffsets(targetSession, 2);
        assertEquals(List.of(off3, off5), limitedOffsets, "Should return only the last 2 consolidated turns");
    }

    @Test
    @DisplayName("lastConsolidatedTurnOffsets should ignore tombstoned records")
    void lastConsolidatedTurnOffsets_ignoresTombstoned() {
        long sessionId = 400L;
        long off1 = episodicMemory.appendTurn(ConversationRole.USER, 1, 1000L, sessionId, "Turn 1".getBytes(), (short) 1, 0, 0, 0, 1L, (short) 1, SourceModality.TEXT);
        episodicMemory.markConsolidated(off1);

        long off2 = episodicMemory.appendTurn(ConversationRole.ASSISTANT, 2, 2000L, sessionId, "Turn 2".getBytes(), (short) 1, 0, 0, 0, 1L, (short) 1, SourceModality.TEXT);
        episodicMemory.markConsolidated(off2);
        episodicMemory.tombstone(off2);

        List<Long> offsets = episodicMemory.lastConsolidatedTurnOffsets(sessionId, 5);
        assertEquals(List.of(off1), offsets, "Tombstoned record must be excluded");
    }
}
