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
import com.spectrayan.spector.memory.model.SourceModality;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("EpisodicFieldAccessor Tests")
class EpisodicFieldAccessorTest {

    private Arena arena;
    private MemorySegment segment;
    private static final long OFFSET = 0L;

    @BeforeEach
    void setUp() {
        arena = Arena.ofConfined();
        segment = arena.allocate(128); // allocate at least 128 bytes
    }

    @AfterEach
    void tearDown() {
        if (arena != null) {
            arena.close();
        }
    }

    @Test
    @DisplayName("Should successfully roundtrip writeHeader and readRecord for all ConversationRoles")
    void shouldRoundtripHeader_whenAllConversationRolesUsed() {
        for (ConversationRole role : ConversationRole.values()) {
            int seqId = 42;
            long ts = 1680000000000L;
            long sessionId = 123456789L;
            int bodyLen = 32;
            short modelId = 5;
            int tokenIn = 10;
            int tokenOut = 20;
            int latency = 150;
            long userId = 987654321L;
            short soulVer = 2;
            SourceModality modality = SourceModality.TEXT;

            EpisodicFieldAccessor.writeHeader(segment, OFFSET, role, seqId, ts, sessionId,
                    bodyLen, modelId, tokenIn, tokenOut, latency, userId, soulVer, modality);

            EpisodicFieldAccessor.EpisodicRecord record = EpisodicFieldAccessor.readRecord(segment, OFFSET, false);

            assertAll("Record fields",
                    () -> assertEquals(role, record.role()),
                    () -> assertEquals(seqId, record.sequenceId()),
                    () -> assertEquals(ts, record.timestampMs()),
                    () -> assertEquals(sessionId, record.sessionId()),
                    () -> assertEquals(bodyLen, record.bodyLength()),
                    () -> assertEquals(modelId, record.modelId()),
                    () -> assertEquals(tokenIn, record.tokenIn()),
                    () -> assertEquals(tokenOut, record.tokenOut()),
                    () -> assertEquals(latency, record.latencyMs()),
                    () -> assertEquals(userId, record.userId()),
                    () -> assertEquals(soulVer, record.soulVersion()),
                    () -> assertEquals(modality, record.modality())
            );
        }
    }

    @Test
    @DisplayName("Should read individual fields correctly")
    void shouldReadIndividualFields_whenHeaderIsWritten() {
        EpisodicFieldAccessor.writeHeader(segment, OFFSET, ConversationRole.ASSISTANT, 100, 1000L, 2000L,
                50, (short) 1, 5, 15, 200, 3000L, (short) 3, SourceModality.IMAGE);

        assertAll("Individual fields",
                () -> assertEquals(ConversationRole.ASSISTANT, EpisodicFieldAccessor.readRole(segment, OFFSET)),
                () -> assertEquals(100, EpisodicFieldAccessor.readSequenceId(segment, OFFSET)),
                () -> assertEquals(1000L, EpisodicFieldAccessor.readTimestamp(segment, OFFSET)),
                () -> assertEquals(2000L, EpisodicFieldAccessor.readSessionId(segment, OFFSET)),
                () -> assertEquals(50, EpisodicFieldAccessor.readBodyLength(segment, OFFSET)),
                () -> assertEquals((short) 1, EpisodicFieldAccessor.readModelId(segment, OFFSET)),
                () -> assertEquals(5, EpisodicFieldAccessor.readTokenInCount(segment, OFFSET)),
                () -> assertEquals(15, EpisodicFieldAccessor.readTokenOutCount(segment, OFFSET)),
                () -> assertEquals(200, EpisodicFieldAccessor.readLatencyMs(segment, OFFSET)),
                () -> assertEquals(3000L, EpisodicFieldAccessor.readUserId(segment, OFFSET)),
                () -> assertEquals((short) 3, EpisodicFieldAccessor.readSoulVersion(segment, OFFSET)),
                () -> assertEquals(SourceModality.IMAGE, EpisodicFieldAccessor.readModality(segment, OFFSET))
            );
    }

    @Test
    @DisplayName("Should set tombstone bit when tombstone() is called")
    void shouldSetTombstoneBit_whenTombstoneIsCalled() {
        EpisodicFieldAccessor.writeHeader(segment, OFFSET, ConversationRole.USER, 1, 1L, 1L,
                10, (short) 1, 1, 1, 1, 1L, (short) 1, SourceModality.TEXT);

        assertFalse(EpisodicFieldAccessor.isTombstoned(segment, OFFSET));
        
        EpisodicFieldAccessor.tombstone(segment, OFFSET);
        
        assertTrue(EpisodicFieldAccessor.isTombstoned(segment, OFFSET));
    }

    @Test
    @DisplayName("Should set consolidated bit when markConsolidated() is called")
    void shouldSetConsolidatedBit_whenMarkConsolidatedIsCalled() {
        EpisodicFieldAccessor.writeHeader(segment, OFFSET, ConversationRole.USER, 1, 1L, 1L,
                10, (short) 1, 1, 1, 1, 1L, (short) 1, SourceModality.TEXT);

        assertFalse(EpisodicFieldAccessor.isConsolidated(segment, OFFSET));
        
        EpisodicFieldAccessor.markConsolidated(segment, OFFSET);
        
        assertTrue(EpisodicFieldAccessor.isConsolidated(segment, OFFSET));
    }

    @Test
    @DisplayName("Should handle edge cases properly: max int, negative values, zero lengths")
    void shouldHandleEdgeCases_whenExtremeValuesProvided() {
        EpisodicFieldAccessor.writeHeader(segment, OFFSET, ConversationRole.SYSTEM, Integer.MAX_VALUE, -1L, -2L,
                0, Short.MAX_VALUE, -5, -10, -100, -3L, Short.MIN_VALUE, SourceModality.AUDIO);

        EpisodicFieldAccessor.EpisodicRecord record = EpisodicFieldAccessor.readRecord(segment, OFFSET, false);

        assertAll("Edge case fields",
                () -> assertEquals(Integer.MAX_VALUE, record.sequenceId()),
                () -> assertEquals(-1L, record.timestampMs()),
                () -> assertEquals(-2L, record.sessionId()),
                () -> assertEquals(0, record.bodyLength()),
                () -> assertEquals(Short.MAX_VALUE, record.modelId()),
                () -> assertEquals(-5, record.tokenIn()),
                () -> assertEquals(-10, record.tokenOut()),
                () -> assertEquals(-100, record.latencyMs()),
                () -> assertEquals(-3L, record.userId()),
                () -> assertEquals(Short.MIN_VALUE, record.soulVersion()),
                () -> assertEquals(SourceModality.AUDIO, record.modality()),
                () -> assertNull(record.body())
        );
    }
}
