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

import com.spectrayan.spector.memory.cortex.SemanticRecordMemory;
import com.spectrayan.spector.memory.kernel.layout.CognitiveRecordLayout;
import com.spectrayan.spector.memory.kernel.layout.CognitiveRecordLayout.CognitiveHeader;
import com.spectrayan.spector.memory.kernel.layout.SynapticHeaderConstants;
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
        CognitiveRecordLayout layout = new CognitiveRecordLayout(DIMS);
        SemanticRecordMemory semanticStore = new SemanticRecordMemory(DIMS, 100);

        long timestamp = System.currentTimeMillis();
        byte procFlags = SynapticHeaderConstants.withMemoryType((byte) 0, MemoryType.SEMANTIC.ordinal());
        short soulVersion = 3;
        byte arousal = (byte) 180;
        byte valence = (byte) 25;
        float importance = 8.2f;

        CognitiveHeader syntheticHeader = CognitiveHeader.createSynthetic(
                timestamp, 0x55AAL, 1.0f, importance,
                valence, arousal, procFlags,
                SynapticHeaderConstants.FLAG_SIMULATED,
                soulVersion, 0.45f
        );

        byte[] vectorBytes = new byte[layout.quantizedVecBytes()];
        semanticStore.append(syntheticHeader, vectorBytes);
        long offset = semanticStore.recordOffset(0);

        // 1. Direct segment read
        byte cFlags = layout.readConsolidationFlags(semanticStore.segment(), offset);
        assertThat(SynapticHeaderConstants.isSimulated(cFlags)).isTrue();

        // 2. Full CognitiveHeader read
        CognitiveHeader readHeader = layout.readHeader(semanticStore.segment(), offset);
        assertThat(SynapticHeaderConstants.isSimulated(readHeader.consolidationFlags())).isTrue();
        assertThat(readHeader.arousal()).isEqualTo(arousal); // Must NOT be corrupted to FLAG_SIMULATED (32)
        assertThat(readHeader.valence()).isEqualTo(valence);
        assertThat(readHeader.soulVersion()).isEqualTo(soulVersion);
        assertThat(readHeader.importance()).isEqualTo(importance);

        semanticStore.close();
    }
}
