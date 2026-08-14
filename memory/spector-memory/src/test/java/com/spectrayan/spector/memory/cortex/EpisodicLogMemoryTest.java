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

import com.spectrayan.spector.memory.kernel.layout.EpisodicFieldAccessor;
import com.spectrayan.spector.memory.model.ConversationRole;
import com.spectrayan.spector.memory.model.SourceModality;
import com.spectrayan.spector.memory.session.EpisodicSessionIndex;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("EpisodicLogMemory Tests")
class EpisodicLogMemoryTest {

    private EpisodicLogMemory logMemory;
    private static final long CAPACITY = 1024 * 1024; // 1MB for tests

    @BeforeEach
    void setUp() {
        logMemory = new EpisodicLogMemory(CAPACITY);
    }

    @AfterEach
    void tearDown() {
        if (logMemory != null) {
            logMemory.close();
        }
    }

    @Test
    @DisplayName("Should successfully append turn and read it back")
    void shouldAppendAndReadTurn_whenRoundtrip() {
        byte[] body = "Hello, world!".getBytes();
        long offset = logMemory.appendTurn(ConversationRole.USER, 1, 1000L, 123L,
                body, (short) 1, 10, 0, 0, 999L, (short) 1, SourceModality.TEXT);

        EpisodicFieldAccessor.EpisodicRecord record = logMemory.readTurn(offset, true);

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
                () -> assertEquals(SourceModality.TEXT, record.modality())
        );
    }

    @Test
    @DisplayName("Should append and read multiple turns in sequence")
    void shouldAppendAndReadMultiple_whenInSequence() {
        long offset1 = logMemory.appendTurn(ConversationRole.USER, 1, 1000L, 123L,
                "msg1".getBytes(), (short) 1, 1, 1, 1, 999L, (short) 1, SourceModality.TEXT);
        long offset2 = logMemory.appendTurn(ConversationRole.ASSISTANT, 2, 2000L, 123L,
                "msg2".getBytes(), (short) 1, 1, 1, 1, 999L, (short) 1, SourceModality.TEXT);

        EpisodicFieldAccessor.EpisodicRecord rec1 = logMemory.readTurn(offset1, true);
        EpisodicFieldAccessor.EpisodicRecord rec2 = logMemory.readTurn(offset2, true);

        assertEquals("msg1", new String(rec1.body()));
        assertEquals("msg2", new String(rec2.body()));
        assertTrue(offset2 > offset1);
    }

    @Test
    @DisplayName("Should read multiple turns from offsets")
    void shouldReadTurns_whenOffsetsProvided() {
        long offset1 = logMemory.appendTurn(ConversationRole.USER, 1, 1000L, 123L,
                "msg1".getBytes(), (short) 1, 1, 1, 1, 999L, (short) 1, SourceModality.TEXT);
        long offset2 = logMemory.appendTurn(ConversationRole.ASSISTANT, 2, 2000L, 123L,
                "msg2".getBytes(), (short) 1, 1, 1, 1, 999L, (short) 1, SourceModality.TEXT);

        List<EpisodicFieldAccessor.EpisodicRecord> records = logMemory.readTurns(List.of(offset1, offset2), true);
        
        assertEquals(2, records.size());
        assertEquals("msg1", new String(records.get(0).body()));
        assertEquals("msg2", new String(records.get(1).body()));
    }

    @Test
    @DisplayName("Should tombstone record correctly")
    void shouldTombstone_whenRequested() {
        long offset = logMemory.appendTurn(ConversationRole.USER, 1, 1000L, 123L,
                "msg1".getBytes(), (short) 1, 1, 1, 1, 999L, (short) 1, SourceModality.TEXT);

        assertFalse(EpisodicFieldAccessor.isTombstoned(logMemory.readTurn(offset, false).flags()));
        
        logMemory.tombstone(offset);
        
        assertTrue(EpisodicFieldAccessor.isTombstoned(logMemory.readTurn(offset, false).flags()));
        
        // readTurns filters out tombstoned records
        assertTrue(logMemory.readTurns(List.of(offset), false).isEmpty());
    }

    @Test
    @DisplayName("Should mark record as consolidated")
    void shouldMarkConsolidated_whenRequested() {
        long offset = logMemory.appendTurn(ConversationRole.USER, 1, 1000L, 123L,
                "msg1".getBytes(), (short) 1, 1, 1, 1, 999L, (short) 1, SourceModality.TEXT);

        assertFalse(EpisodicFieldAccessor.isConsolidated(logMemory.readTurn(offset, false).flags()));
        
        logMemory.markConsolidated(offset);
        
        assertTrue(EpisodicFieldAccessor.isConsolidated(logMemory.readTurn(offset, false).flags()));
    }

    @Test
    @DisplayName("Should track write position correctly")
    void shouldAdvanceWritePosition_whenTurnsAppended() {
        assertEquals(0, logMemory.writePosition());
        
        logMemory.appendTurn(ConversationRole.USER, 1, 1000L, 123L,
                "msg1".getBytes(), (short) 1, 1, 1, 1, 999L, (short) 1, SourceModality.TEXT);
        
        long pos1 = logMemory.writePosition();
        assertTrue(pos1 > 0);
        
        logMemory.appendTurn(ConversationRole.USER, 2, 2000L, 123L,
                "msg2".getBytes(), (short) 1, 1, 1, 1, 999L, (short) 1, SourceModality.TEXT);
                
        assertTrue(logMemory.writePosition() > pos1);
    }

    @Test
    @DisplayName("Should rebuild session index properly")
    void shouldRebuildSessionIndex_whenRequested() {
        logMemory.appendTurn(ConversationRole.USER, 1, 1000L, 123L,
                "msg1".getBytes(), (short) 1, 1, 1, 1, 999L, (short) 1, SourceModality.TEXT);
        logMemory.appendTurn(ConversationRole.ASSISTANT, 2, 2000L, 123L,
                "msg2".getBytes(), (short) 1, 1, 1, 1, 999L, (short) 1, SourceModality.TEXT);
        logMemory.appendTurn(ConversationRole.USER, 1, 3000L, 456L,
                "msg3".getBytes(), (short) 1, 1, 1, 1, 999L, (short) 1, SourceModality.TEXT);
                
        EpisodicSessionIndex index = new EpisodicSessionIndex();
        int indexedCount = logMemory.rebuildSessionIndex(index);
        
        assertEquals(3, indexedCount);
        assertEquals(2, index.sessionCount());
        assertEquals(2, index.turnCount(123L));
        assertEquals(1, index.turnCount(456L));
    }

    @Test
    @DisplayName("Should track remaining bytes accurately")
    void shouldDecreaseRemainingBytes_whenAppended() {
        long initialRemaining = logMemory.remainingBytes();
        assertTrue(initialRemaining > 0);
        
        logMemory.appendTurn(ConversationRole.USER, 1, 1000L, 123L,
                "msg1".getBytes(), (short) 1, 1, 1, 1, 999L, (short) 1, SourceModality.TEXT);
                
        assertTrue(logMemory.remainingBytes() < initialRemaining);
    }

    @Test
    @DisplayName("Should handle empty body gracefully")
    void shouldHandleEmptyBody_whenNullOrEmpty() {
        long offset1 = logMemory.appendTurn(ConversationRole.USER, 1, 1000L, 123L,
                null, (short) 1, 1, 1, 1, 999L, (short) 1, SourceModality.TEXT);
        
        EpisodicFieldAccessor.EpisodicRecord rec1 = logMemory.readTurn(offset1, true);
        assertEquals(0, rec1.bodyLength());
        assertNull(rec1.body());
        
        long offset2 = logMemory.appendTurn(ConversationRole.USER, 2, 2000L, 123L,
                new byte[0], (short) 1, 1, 1, 1, 999L, (short) 1, SourceModality.TEXT);
        
        EpisodicFieldAccessor.EpisodicRecord rec2 = logMemory.readTurn(offset2, true);
        assertEquals(0, rec2.bodyLength());
        assertNull(rec2.body());
    }

    @Test
    @DisplayName("Should handle large CBOR body")
    void shouldHandleLargeBody_whenAppended() {
        byte[] largeBody = new byte[100_000];
        for (int i = 0; i < largeBody.length; i++) {
            largeBody[i] = (byte) (i % 256);
        }
        
        long offset = logMemory.appendTurn(ConversationRole.USER, 1, 1000L, 123L,
                largeBody, (short) 1, 1, 1, 1, 999L, (short) 1, SourceModality.TEXT);
                
        EpisodicFieldAccessor.EpisodicRecord rec = logMemory.readTurn(offset, true);
        assertEquals(largeBody.length, rec.bodyLength());
        assertArrayEquals(largeBody, rec.body());
    }
}
