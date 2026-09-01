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

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

import com.spectrayan.spector.memory.kernel.layout.CognitiveRecordLayout.CognitiveHeader;
import com.spectrayan.spector.memory.model.MemoryType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validates that all header fields, especially V3 provenance consolidationFlags and soulVersion,
 * survive write/read round-trips in {@link HeaderLayout64} (MR-01).
 */
class HeaderLayout64ProvenanceRoundTripTest {

    private final HeaderLayout64 layout = new HeaderLayout64();

    @Test
    @DisplayName("MR-01: consolidationFlags (FLAG_SIMULATED) and soulVersion survive write/read round trip")
    void consolidationFlagsSimulatedSurvivesRoundTrip() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(64L);

            long now = System.currentTimeMillis();
            byte procFlags = SynapticHeaderConstants.withMemoryType((byte) 0, MemoryType.EPISODIC.ordinal());
            CognitiveHeader header = CognitiveHeader.createSynthetic(
                    now, 0xABCDEFL, 1.25f, 7.5f,
                    (byte) 42, (byte) 200, procFlags,
                    SynapticHeaderConstants.FLAG_SIMULATED, (short) 9, 3.14f
            );

            layout.writeHeader(segment, 0L, header);
            CognitiveHeader read = layout.readHeader(segment, 0L);

            assertThat(read.timestampMs()).isEqualTo(now);
            assertThat(read.synapticTags()).isEqualTo(0xABCDEFL);
            assertThat(read.exactNorm()).isEqualTo(1.25f);
            assertThat(read.importance()).isEqualTo(7.5f);
            assertThat(read.valence()).isEqualTo((byte) 42);
            assertThat(read.arousal()).isEqualTo((byte) 200); // Regression guard against Defect A
            assertThat(read.flags()).isEqualTo(procFlags);
            assertThat(read.consolidationFlags()).isEqualTo(SynapticHeaderConstants.FLAG_SIMULATED);
            assertThat(SynapticHeaderConstants.isSimulated(read.consolidationFlags())).isTrue();
            assertThat(read.soulVersion()).isEqualTo((short) 9); // Regression guard against Defect §1.5
            assertThat(read.encodingSurprise()).isEqualTo(3.14f);
        }
    }

    @Test
    @DisplayName("MR-01: consolidationFlags (FLAG_CRYSTALLIZED) survives write/read round trip")
    void consolidationFlagsCrystallizedSurvivesRoundTrip() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(64L);

            long now = System.currentTimeMillis();
            byte procFlags = SynapticHeaderConstants.withMemoryType((byte) 0, MemoryType.PROCEDURAL.ordinal());
            CognitiveHeader header = CognitiveHeader.createSynthetic(
                    now, 0x123456L, 1.0f, 4.0f,
                    (byte) 0, (byte) 0, procFlags,
                    SynapticHeaderConstants.FLAG_CRYSTALLIZED, (short) 3, 0.0f
            );

            layout.writeHeader(segment, 0L, header);
            CognitiveHeader read = layout.readHeader(segment, 0L);

            assertThat(read.consolidationFlags()).isEqualTo(SynapticHeaderConstants.FLAG_CRYSTALLIZED);
            assertThat(SynapticHeaderConstants.isCrystallized(read.consolidationFlags())).isTrue();
            assertThat(read.soulVersion()).isEqualTo((short) 3);
        }
    }
}
