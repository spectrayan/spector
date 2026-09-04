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

import com.spectrayan.spector.memory.kernel.RegionPreamble;
import com.spectrayan.spector.memory.kernel.layout.EncodingHeaderFields;
import com.spectrayan.spector.memory.kernel.layout.EpisodeLayout;
import com.spectrayan.spector.memory.kernel.layout.EpisodicHeaderAccessor;
import com.spectrayan.spector.memory.model.ConversationRole;
import com.spectrayan.spector.memory.model.EngramSource;
import com.spectrayan.spector.memory.model.EpisodeRecord;
import com.spectrayan.spector.memory.model.SourceModality;
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
        assertTrue(EpisodicHeaderAccessor.isOptionBRecord(episodicMemory.segment(), absoluteOffset));
        assertEquals(EpisodeLayout.MAGIC, EpisodicHeaderAccessor.readMagic(episodicMemory.segment(), absoluteOffset));
        assertEquals(0.85f, EpisodicHeaderAccessor.readImportance(episodicMemory.segment(), absoluteOffset), 0.001f);
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
    @DisplayName("Should support dual-read of legacy punned records (R4.4)")
    void shouldSupportDualRead_ofLegacyPunnedRecords() {
        // Option B turn first
        long optBOffset = episodicMemory.appendTurn(ConversationRole.USER, 1, 1000L, 123L,
                "Option B message".getBytes(), (short) 1, 10, 5, 50, 777L, (short) 1, SourceModality.TEXT,
                0.9f, (byte) 10, (byte) 5, EngramSource.EXPERIENCED);

        EpisodeRecord optBRec = episodicMemory.readTurn(optBOffset, true);
        assertEquals("Option B message", new String(optBRec.body()));
        assertEquals(0.9f, optBRec.importance(), 0.001f);
        assertEquals(EngramSource.EXPERIENCED, optBRec.source());

        // Now simulate a legacy punned record manually in the buffer
        long legacyOffset = episodicMemory.writePosition();
        long legacyAbs = episodicMemory.dataOffset() + legacyOffset;
        byte[] legacyBody = "Legacy message".getBytes();

        var seg = episodicMemory.segment();
        seg.set(ValueLayout.JAVA_BYTE, legacyAbs + EncodingHeaderFields.OFFSET_VERSION, (byte) 1);
        seg.set(ValueLayout.JAVA_BYTE, legacyAbs + EncodingHeaderFields.OFFSET_VALENCE, (byte) ConversationRole.ASSISTANT.ordinal());
        seg.set(ValueLayout.JAVA_INT_UNALIGNED, legacyAbs + EncodingHeaderFields.OFFSET_IMPORTANCE, 2); // sequenceId
        seg.set(ValueLayout.JAVA_LONG_UNALIGNED, legacyAbs + EncodingHeaderFields.OFFSET_TIMESTAMP, 2000L);
        seg.set(ValueLayout.JAVA_LONG_UNALIGNED, legacyAbs + EncodingHeaderFields.OFFSET_SYNAPTIC_TAGS, 123L); // sessionId
        seg.set(ValueLayout.JAVA_INT_UNALIGNED, legacyAbs + EncodingHeaderFields.OFFSET_ENCODING_SURPRISE, legacyBody.length);
        seg.set(ValueLayout.JAVA_SHORT_UNALIGNED, legacyAbs + EncodingHeaderFields.OFFSET_CENTROID_ID, (short) 2);
        seg.set(ValueLayout.JAVA_LONG_UNALIGNED, legacyAbs + EncodingHeaderFields.OFFSET_LAST_AUTO_LTP, 888L); // userId
        // write body immediately after 64B legacy header
        seg.asSlice(legacyAbs + 64, legacyBody.length).copyFrom(java.lang.foreign.MemorySegment.ofArray(legacyBody));

        // Dual-read verification
        assertFalse(EpisodicHeaderAccessor.isOptionBRecord(seg, legacyAbs));
        EpisodeRecord legacyRec = episodicMemory.readTurn(legacyOffset, true);
        assertEquals(ConversationRole.ASSISTANT, legacyRec.role());
        assertEquals(2, legacyRec.sequenceId());
        assertEquals(2000L, legacyRec.timestampMs());
        assertEquals(123L, legacyRec.sessionId());
        assertEquals("Legacy message", new String(legacyRec.body()));
        assertEquals(888L, legacyRec.userId());
    }
}
