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
package com.spectrayan.spector.memory.aisme.relay;

import com.spectrayan.spector.memory.cortex.SemanticMemory;
import com.spectrayan.spector.memory.kernel.layout.EngramLayout;
import com.spectrayan.spector.memory.kernel.layout.EncodingHeader;
import com.spectrayan.spector.memory.kernel.layout.EncodingHeaderFields;
import com.spectrayan.spector.memory.model.MemoryType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Durability integration tests for constructive memory simulation provenance (MR-01).
 */
class ConstructivePersistenceDurabilityTest {

    private static final int DIMS = 16;

    @Test
    @DisplayName("MR-01: Persisted synthetic simulation retains FLAG_SIMULATED, arousal, and soulVersion across store operations")
    void testSyntheticMemoryDurabilityInSemanticStore() {
        EngramLayout layout = new EngramLayout(DIMS);
        SemanticMemory semanticStore = new SemanticMemory(DIMS, 100);

        long timestamp = System.currentTimeMillis();
        byte procFlags = EncodingHeaderFields.withMemoryType((byte) 0, MemoryType.SEMANTIC.ordinal());
        short soulVersion = 3;
        byte arousal = (byte) 180;
        byte valence = (byte) 25;
        float importance = 8.2f;

        EncodingHeader syntheticHeader = EncodingHeader.createSynthetic(
                timestamp, 0x55AAL, 1.0f, importance,
                valence, arousal, procFlags,
                EncodingHeaderFields.FLAG_SIMULATED,
                soulVersion, 0.45f
        );

        byte[] vectorBytes = new byte[layout.quantizedVecBytes()];
        semanticStore.append(syntheticHeader, vectorBytes);
        long offset = semanticStore.recordOffset(0);

        // 1. Direct segment read
        byte cFlags = layout.readConsolidationFlags(semanticStore.segment(), offset);
        assertThat(EncodingHeaderFields.isSimulated(cFlags)).isTrue();

        // 2. Full EncodingHeader read
        EncodingHeader readHeader = layout.readHeader(semanticStore.segment(), offset);
        assertThat(EncodingHeaderFields.isSimulated(readHeader.consolidationFlags())).isTrue();
        assertThat(readHeader.arousal()).isEqualTo(arousal); // Must NOT be corrupted to FLAG_SIMULATED (32)
        assertThat(readHeader.valence()).isEqualTo(valence);
        assertThat(readHeader.soulVersion()).isEqualTo(soulVersion);
        assertThat(readHeader.importance()).isEqualTo(importance);

        semanticStore.close();
    }
}
