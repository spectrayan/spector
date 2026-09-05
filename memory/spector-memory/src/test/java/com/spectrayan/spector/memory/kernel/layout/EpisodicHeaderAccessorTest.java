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

import com.spectrayan.spector.memory.model.EngramSource;
import com.spectrayan.spector.memory.model.MemoryType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@DisplayName("EpisodicHeaderAccessor Tests (Option B Framing & Header Delegation)")
class EpisodicHeaderAccessorTest {

    private Arena arena;
    private MemorySegment segment;
    private static final long RECORD_OFFSET = 64L;

    @BeforeEach
    void setUp() {
        arena = Arena.ofConfined();
        segment = arena.allocate(256);
    }

    @AfterEach
    void tearDown() {
        if (arena != null) {
            arena.close();
        }
    }

    @Test
    @DisplayName("Option B record discrimination and prefix fields")
    void testOptionBDiscriminationAndPrefix() {
        assertThat(EpisodicHeaderAccessor.isOptionBRecord(segment, RECORD_OFFSET)).isFalse();

        // Write prefix
        segment.set(ValueLayout.JAVA_INT_UNALIGNED, RECORD_OFFSET, 120); // payloadBytes
        segment.set(ValueLayout.JAVA_INT_UNALIGNED, RECORD_OFFSET + 4, 42); // sequenceId
        segment.set(ValueLayout.JAVA_INT_UNALIGNED, RECORD_OFFSET + 8, 0xABCDEF01); // checksum
        segment.set(ValueLayout.JAVA_INT_UNALIGNED, RECORD_OFFSET + 12, EpisodeLayout.MAGIC); // magic

        assertThat(EpisodicHeaderAccessor.isOptionBRecord(segment, RECORD_OFFSET)).isTrue();
        assertThat(EpisodicHeaderAccessor.readPayloadBytes(segment, RECORD_OFFSET)).isEqualTo(120);
        assertThat(EpisodicHeaderAccessor.readSequenceId(segment, RECORD_OFFSET)).isEqualTo(42);
        assertThat(EpisodicHeaderAccessor.readChecksum(segment, RECORD_OFFSET)).isEqualTo(0xABCDEF01);
        assertThat(EpisodicHeaderAccessor.readMagic(segment, RECORD_OFFSET)).isEqualTo(EpisodeLayout.MAGIC);
    }

    @Test
    @DisplayName("EncodingHeader write and read delegation at offset +16")
    void testHeaderDelegation() {
        // Set prefix magic so record is valid Option B
        segment.set(ValueLayout.JAVA_INT_UNALIGNED, RECORD_OFFSET, 50);
        segment.set(ValueLayout.JAVA_INT_UNALIGNED, RECORD_OFFSET + 12, EpisodeLayout.MAGIC);

        EncodingHeader header = new EncodingHeader(
                1700000000000L,
                42L,
                0.0f,
                0.92f,
                0,
                (short) 0,
                (byte) 15,
                (byte) 0,
                (byte) -10,
                1.0f,
                (byte) 0,
                (byte) 0,
                (byte) 0,
                (short) 2,
                0.0f,
                (byte) 0,
                EngramSource.EXPERIENCED
        );

        EpisodicHeaderAccessor.writeHeader(segment, RECORD_OFFSET, header);

        assertAll("Header field delegations",
                () -> assertThat(EpisodicHeaderAccessor.readImportance(segment, RECORD_OFFSET)).isEqualTo(0.92f),
                () -> assertThat(EpisodicHeaderAccessor.readValence(segment, RECORD_OFFSET)).isEqualTo((byte) 15),
                () -> assertThat(EpisodicHeaderAccessor.readArousal(segment, RECORD_OFFSET)).isEqualTo((byte) -10),
                () -> assertThat(EpisodicHeaderAccessor.readSource(segment, RECORD_OFFSET)).isEqualTo(EngramSource.EXPERIENCED),
                () -> assertThat(EpisodicHeaderAccessor.readTimestamp(segment, RECORD_OFFSET)).isEqualTo(1700000000000L)
        );

        EncodingHeader roundtrip = EpisodicHeaderAccessor.readHeader(segment, RECORD_OFFSET);
        assertThat(roundtrip.importance()).isEqualTo(0.92f);
        assertThat(roundtrip.source()).isEqualTo(EngramSource.EXPERIENCED);
    }
}
