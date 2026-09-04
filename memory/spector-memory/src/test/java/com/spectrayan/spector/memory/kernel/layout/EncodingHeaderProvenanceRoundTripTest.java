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

import com.spectrayan.spector.memory.kernel.layout.CognitiveRecordLayout.CognitiveHeader;
import com.spectrayan.spector.memory.kernel.layout.compat.LegacyEncodingHeaderReader;
import com.spectrayan.spector.memory.model.MemoryType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validates that all header fields, especially provenance consolidationFlags and soulVersion,
 * survive write/read round-trips in {@link EncodingHeaderLayout}, and that legacy V1 records
 * are decoded correctly by {@link LegacyEncodingHeaderReader}.
 */
class EncodingHeaderProvenanceRoundTripTest {

    private final EncodingHeaderLayout layout = EncodingHeaderLayout.INSTANCE;
    private final LegacyEncodingHeaderReader legacyReader = LegacyEncodingHeaderReader.INSTANCE;

    @Test
    @DisplayName("MR-01: consolidationFlags (FLAG_SIMULATED) and soulVersion survive write/read round trip in EncodingHeaderLayout")
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
            assertThat(read.arousal()).isEqualTo((byte) 200);
            assertThat(read.flags()).isEqualTo(procFlags);
            assertThat(read.consolidationFlags()).isEqualTo(SynapticHeaderConstants.FLAG_SIMULATED);
            assertThat(SynapticHeaderConstants.isSimulated(read.consolidationFlags())).isTrue();
            assertThat(read.soulVersion()).isEqualTo((short) 9);
            assertThat(read.encodingSurprise()).isEqualTo(3.14f);
        }
    }

    @Test
    @DisplayName("MR-01: consolidationFlags (FLAG_CRYSTALLIZED) survives write/read round trip in EncodingHeaderLayout")
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

    @Test
    @DisplayName("LegacyEncodingHeaderReader correctly decodes legacy V1 engram header fields")
    void legacyV1HeaderDecodesCorrectly() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(64L);

            long now = 1716900000000L;
            byte procFlags = SynapticHeaderConstants.withMemoryType((byte) 0, MemoryType.SEMANTIC.ordinal());

            // Write raw V1 bytes
            segment.set(ValueLayout.JAVA_BYTE, SynapticHeaderConstants.OFFSET_HEADER_VERSION, (byte) 1);
            segment.set(ValueLayout.JAVA_BYTE, SynapticHeaderConstants.OFFSET_FLAGS, procFlags);
            segment.set(ValueLayout.JAVA_BYTE, SynapticHeaderConstants.OFFSET_VALENCE, (byte) 30);
            segment.set(ValueLayout.JAVA_BYTE, SynapticHeaderConstants.OFFSET_AROUSAL, (byte) 150);
            segment.set(ValueLayout.JAVA_FLOAT, SynapticHeaderConstants.OFFSET_IMPORTANCE, 0.85f);
            segment.set(ValueLayout.JAVA_LONG, SynapticHeaderConstants.OFFSET_TIMESTAMP, now);
            segment.set(ValueLayout.JAVA_INT, SynapticHeaderConstants.OFFSET_AGENT_RECALL_COUNT, 5);
            segment.set(ValueLayout.JAVA_FLOAT, SynapticHeaderConstants.OFFSET_EXACT_NORM, 1.12f);
            segment.set(ValueLayout.JAVA_LONG, SynapticHeaderConstants.OFFSET_SYNAPTIC_TAGS, 0xCAFEBABE1234L);
            segment.set(ValueLayout.JAVA_SHORT, SynapticHeaderConstants.OFFSET_CENTROID_ID, (short) 7);
            segment.set(ValueLayout.JAVA_BYTE, SynapticHeaderConstants.OFFSET_CONSOLIDATION_FLAGS, SynapticHeaderConstants.FLAG_SIMULATED);
            segment.set(ValueLayout.JAVA_BYTE, SynapticHeaderConstants.OFFSET_ENCODING_PROFILE, (byte) 2);
            segment.set(ValueLayout.JAVA_FLOAT, SynapticHeaderConstants.OFFSET_STORAGE_STRENGTH, 2.75f);
            segment.set(ValueLayout.JAVA_INT, SynapticHeaderConstants.OFFSET_SPECTOR_RECALL_COUNT, 8);
            segment.set(ValueLayout.JAVA_BYTE, SynapticHeaderConstants.OFFSET_ENCODING_ALPHA, (byte) 64);
            segment.set(ValueLayout.JAVA_BYTE, SynapticHeaderConstants.OFFSET_ENCODING_BETA, (byte) 128);
            segment.set(ValueLayout.JAVA_SHORT, SynapticHeaderConstants.OFFSET_SOUL_VERSION, (short) 4);
            segment.set(ValueLayout.JAVA_LONG, SynapticHeaderConstants.OFFSET_LAST_AUTO_LTP, now - 1000L);
            segment.set(ValueLayout.JAVA_FLOAT, SynapticHeaderConstants.OFFSET_ENCODING_SURPRISE, 1.414f);
            segment.set(ValueLayout.JAVA_BYTE, SynapticHeaderConstants.OFFSET_LAST_RECALL_PROFILE, (byte) 1);

            assertThat(legacyReader.version()).isEqualTo(1);
            assertThat(legacyReader.headerBytes()).isEqualTo(64);

            CognitiveHeader read = legacyReader.readHeader(segment, 0L);
            assertThat(read.timestampMs()).isEqualTo(now);
            assertThat(read.synapticTags()).isEqualTo(0xCAFEBABE1234L);
            assertThat(read.exactNorm()).isEqualTo(1.12f);
            assertThat(read.importance()).isEqualTo(0.85f);
            assertThat(read.agentRecallCount()).isEqualTo(5);
            assertThat(read.centroidId()).isEqualTo((short) 7);
            assertThat(read.valence()).isEqualTo((byte) 30);
            assertThat(read.flags()).isEqualTo(procFlags);
            assertThat(read.arousal()).isEqualTo((byte) 150);
            assertThat(read.storageStrength()).isEqualTo(2.75f);
            assertThat(read.encodingProfile()).isEqualTo((byte) 2);
            assertThat(read.encodingAlpha()).isEqualTo((byte) 64);
            assertThat(read.encodingBeta()).isEqualTo((byte) 128);
            assertThat(read.soulVersion()).isEqualTo((short) 4);
            assertThat(read.encodingSurprise()).isEqualTo(1.414f);
            assertThat(read.consolidationFlags()).isEqualTo(SynapticHeaderConstants.FLAG_SIMULATED);

            assertThat(legacyReader.readSpectorRecallCount(segment, 0L)).isEqualTo(8);
            assertThat(legacyReader.readLastAutoLtp(segment, 0L)).isEqualTo(now - 1000L);
            assertThat(legacyReader.readLastRecallProfile(segment, 0L)).isEqualTo((byte) 1);
        }
    }
}
