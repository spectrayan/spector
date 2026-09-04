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
package com.spectrayan.spector.memory.kernel.layout.compat;

import com.spectrayan.spector.memory.kernel.layout.EncodingHeaderFields;
import com.spectrayan.spector.memory.model.ConversationRole;
import com.spectrayan.spector.memory.model.EpisodeRecord;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.SourceModality;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LegacyEpisodeHeaderReader Tests")
class LegacyEpisodeHeaderReaderTest {

    private Arena arena;
    private MemorySegment segment;
    private static final long OFFSET = 0L;

    @BeforeEach
    void setUp() {
        arena = Arena.ofConfined();
        segment = arena.allocate(128);
    }

    @AfterEach
    void tearDown() {
        if (arena != null) {
            arena.close();
        }
    }

    private void writeLegacyHeader(MemorySegment seg, long offset, ConversationRole role, int seqId,
                                   long ts, long sessionId, int bodyLen, short modelId,
                                   int tokenIn, int tokenOut, int latency, long userId,
                                   short soulVer, SourceModality modality) {
        seg.fill((byte) 0);
        seg.set(ValueLayout.JAVA_BYTE, offset + EncodingHeaderFields.OFFSET_VERSION, (byte) 1);
        byte flags = EncodingHeaderFields.withMemoryType((byte) 0, MemoryType.EPISODIC.ordinal());
        if (modality != null && modality != SourceModality.TEXT) {
            flags = EncodingHeaderFields.withSourceModality(flags, modality.ordinal());
        }
        seg.set(ValueLayout.JAVA_BYTE, offset + EncodingHeaderFields.OFFSET_FLAGS, flags);
        seg.set(ValueLayout.JAVA_BYTE, offset + EncodingHeaderFields.OFFSET_VALENCE, (byte) role.ordinal());
        seg.set(ValueLayout.JAVA_INT_UNALIGNED, offset + EncodingHeaderFields.OFFSET_IMPORTANCE, seqId);
        seg.set(ValueLayout.JAVA_LONG_UNALIGNED, offset + EncodingHeaderFields.OFFSET_TIMESTAMP, ts);
        seg.set(ValueLayout.JAVA_LONG_UNALIGNED, offset + EncodingHeaderFields.OFFSET_SYNAPTIC_TAGS, sessionId);
        seg.set(ValueLayout.JAVA_INT_UNALIGNED, offset + EncodingHeaderFields.OFFSET_ENCODING_SURPRISE, bodyLen);
        seg.set(ValueLayout.JAVA_SHORT_UNALIGNED, offset + EncodingHeaderFields.OFFSET_CENTROID_ID, modelId);
        seg.set(ValueLayout.JAVA_INT_UNALIGNED, offset + EncodingHeaderFields.OFFSET_AGENT_RECALL_COUNT, tokenIn);
        seg.set(ValueLayout.JAVA_INT_UNALIGNED, offset + EncodingHeaderFields.OFFSET_EXACT_NORM, tokenOut);
        seg.set(ValueLayout.JAVA_INT_UNALIGNED, offset + EncodingHeaderFields.OFFSET_STORAGE_STRENGTH, latency);
        seg.set(ValueLayout.JAVA_LONG_UNALIGNED, offset + EncodingHeaderFields.OFFSET_LAST_AUTO_LTP, userId);
        seg.set(ValueLayout.JAVA_SHORT_UNALIGNED, offset + EncodingHeaderFields.OFFSET_SOUL_VERSION, soulVer);
    }

    @Test
    @DisplayName("Should successfully read legacy punned header for all ConversationRoles")
    void shouldReadLegacyHeader_whenAllConversationRolesUsed() {
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

            writeLegacyHeader(segment, OFFSET, role, seqId, ts, sessionId,
                    bodyLen, modelId, tokenIn, tokenOut, latency, userId, soulVer, modality);

            EpisodeRecord record = LegacyEpisodeHeaderReader.readRecord(segment, OFFSET, false);

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
    @DisplayName("Should read individual legacy fields correctly")
    void shouldReadIndividualFields_whenLegacyHeaderIsWritten() {
        writeLegacyHeader(segment, OFFSET, ConversationRole.ASSISTANT, 100, 1000L, 2000L,
                50, (short) 1, 5, 15, 200, 3000L, (short) 3, SourceModality.IMAGE);

        assertAll("Individual fields",
                () -> assertEquals(ConversationRole.ASSISTANT, LegacyEpisodeHeaderReader.readRole(segment, OFFSET)),
                () -> assertEquals(100, LegacyEpisodeHeaderReader.readSequenceId(segment, OFFSET)),
                () -> assertEquals(1000L, LegacyEpisodeHeaderReader.readTimestamp(segment, OFFSET)),
                () -> assertEquals(2000L, LegacyEpisodeHeaderReader.readSessionId(segment, OFFSET)),
                () -> assertEquals(50, LegacyEpisodeHeaderReader.readBodyLength(segment, OFFSET)),
                () -> assertEquals((short) 1, LegacyEpisodeHeaderReader.readModelId(segment, OFFSET)),
                () -> assertEquals(5, LegacyEpisodeHeaderReader.readTokenInCount(segment, OFFSET)),
                () -> assertEquals(15, LegacyEpisodeHeaderReader.readTokenOutCount(segment, OFFSET)),
                () -> assertEquals(200, LegacyEpisodeHeaderReader.readLatencyMs(segment, OFFSET)),
                () -> assertEquals(3000L, LegacyEpisodeHeaderReader.readUserId(segment, OFFSET)),
                () -> assertEquals((short) 3, LegacyEpisodeHeaderReader.readSoulVersion(segment, OFFSET)),
                () -> assertEquals(SourceModality.IMAGE, LegacyEpisodeHeaderReader.readModality(segment, OFFSET))
        );
    }

    @Test
    @DisplayName("Should read flags and check tombstone / consolidated status")
    void shouldCheckFlags() {
        writeLegacyHeader(segment, OFFSET, ConversationRole.USER, 1, 1L, 1L,
                10, (short) 1, 1, 1, 1, 1L, (short) 1, SourceModality.TEXT);

        assertFalse(LegacyEpisodeHeaderReader.isTombstoned(segment, OFFSET));
        assertFalse(LegacyEpisodeHeaderReader.isConsolidated(segment, OFFSET));

        // Set tombstone flag (bit 0 of flags byte)
        segment.set(ValueLayout.JAVA_BYTE, OFFSET + EncodingHeaderFields.OFFSET_FLAGS, (byte) 1);
        assertTrue(LegacyEpisodeHeaderReader.isTombstoned(segment, OFFSET));

        // Set consolidated flag (bit 3 of flags byte)
        segment.set(ValueLayout.JAVA_BYTE, OFFSET + EncodingHeaderFields.OFFSET_FLAGS, EncodingHeaderFields.FLAG_CONSOLIDATED);
        assertTrue(LegacyEpisodeHeaderReader.isConsolidated(segment, OFFSET));
    }

    @Test
    @DisplayName("Should handle edge cases properly: max int, negative values, zero lengths")
    void shouldHandleEdgeCases_whenExtremeValuesProvided() {
        writeLegacyHeader(segment, OFFSET, ConversationRole.SYSTEM, Integer.MAX_VALUE, -1L, -2L,
                0, Short.MAX_VALUE, -5, -10, -100, -3L, Short.MIN_VALUE, SourceModality.AUDIO);

        EpisodeRecord record = LegacyEpisodeHeaderReader.readRecord(segment, OFFSET, false);

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
