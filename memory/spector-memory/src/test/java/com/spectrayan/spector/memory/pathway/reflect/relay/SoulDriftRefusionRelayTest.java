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
package com.spectrayan.spector.memory.pathway.reflect.relay;

import com.spectrayan.spector.core.quantization.ScalarQuantizer;
import com.spectrayan.spector.memory.persist.PartitionManager;
import com.spectrayan.spector.memory.pathway.remember.RememberPathway;
import com.spectrayan.spector.memory.cortex.CognitiveMemoryRouter;
import com.spectrayan.spector.memory.cortex.PartitionHandle;
import com.spectrayan.spector.memory.cortex.SemanticRecordMemory;
import com.spectrayan.spector.memory.cortex.WorkingRecordMemory;
import com.spectrayan.spector.memory.kernel.layout.CognitiveRecordLayout;
import com.spectrayan.spector.memory.kernel.layout.CognitiveRecordLayout.CognitiveHeader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@DisplayName("SoulDriftRefusionRelay: Soul-Drift Detection and Re-Fusion Tests")
class SoulDriftRefusionRelayTest {

    private static final int DIMS = 16;

    @Test
    @DisplayName("SoulDriftRefusionRelay detects outdated soul versions and updates headers in-place")
    void testSoulDriftDetectionAndRefusion() {
        ScalarQuantizer quantizer = Mockito.mock(ScalarQuantizer.class);
        CognitiveRecordLayout layout = new CognitiveRecordLayout(DIMS);
        SemanticRecordMemory semanticMemory = new SemanticRecordMemory(DIMS, 100);
        WorkingRecordMemory workingMemory = new WorkingRecordMemory(DIMS, 100);

        CognitiveMemoryRouter router = new CognitiveMemoryRouter(workingMemory, null, semanticMemory, null);
        PartitionManager partitionManager = Mockito.mock(PartitionManager.class);
        PartitionHandle handle = new PartitionHandle(0, null, router, null, false);
        when(partitionManager.snapshot()).thenReturn(List.of(handle));

        RememberPathway rememberPathway = Mockito.mock(RememberPathway.class);
        when(rememberPathway.currentSoulVersion()).thenReturn((short) 2);

        // Write a memory with soulVersion = 1, importance = 0.4, encodingSurprise = 2.5
        CognitiveHeader header = new CognitiveHeader(
                System.currentTimeMillis(),
                0L,
                1.0f,
                0.4f,
                0,
                (short) 0,
                (byte) 10,
                (byte) 0,
                (byte) 5,
                1.0f,
                (byte) 0,
                (byte) 0,
                (byte) 0,
                (short) 1,
                2.5f,
                (byte) 0
        );

        byte[] quantized = new byte[layout.quantizedVecBytes()];
        semanticMemory.write(header, quantized);

        ReflectSignal signal = ReflectSignal.builder()
                .partitionManager(partitionManager)
                .rememberPathway(rememberPathway)
                .quantizer(quantizer)
                .soulDriftRefusionEnabled(true)
                .soulDriftRefusionBatchSize(10)
                .build();

        SoulDriftRefusionRelay relay = new SoulDriftRefusionRelay();
        boolean success = relay.transmit(signal);

        assertThat(success).isTrue();
        assertThat(signal.soulDriftedCount()).isEqualTo(1);
        assertThat(signal.soulRefusedCount()).isEqualTo(1);

        // Verify that the record header was updated in-place to target soul version 2
        long offset = semanticMemory.recordOffset(0);
        short newVersion = layout.readSoulVersion(semanticMemory.segment(), offset);
        assertThat(newVersion).isEqualTo((short) 2);

        semanticMemory.close();
        workingMemory.close();
    }

    @Test
    @DisplayName("SoulDriftRefusionRelay adapts generative prior mean toward autobiographical centroid")
    void testGenerativePriorPlasticityAdaptation() {
        ScalarQuantizer quantizer = Mockito.mock(ScalarQuantizer.class);
        float[] memoryVec = new float[DIMS];
        java.util.Arrays.fill(memoryVec, 1.0f);
        when(quantizer.decode(Mockito.any(byte[].class))).thenReturn(memoryVec);

        CognitiveRecordLayout layout = new CognitiveRecordLayout(DIMS);
        SemanticRecordMemory semanticMemory = new SemanticRecordMemory(DIMS, 100);
        CognitiveMemoryRouter router = new CognitiveMemoryRouter(null, null, semanticMemory, null);
        PartitionManager partitionManager = Mockito.mock(PartitionManager.class);
        PartitionHandle handle = new PartitionHandle(0, null, router, null, false);
        when(partitionManager.snapshot()).thenReturn(List.of(handle));

        CognitiveHeader header = new CognitiveHeader(
                System.currentTimeMillis(), 0L, 1.0f, 0.4f, 0, (short) 0,
                (byte) 10, (byte) 0, (byte) 5, 1.0f, (byte) 0, (byte) 0, (byte) 0, (short) 2, 1.0f, (byte) 0
        );
        byte[] quantized = new byte[layout.quantizedVecBytes()];
        semanticMemory.write(header, quantized);

        com.spectrayan.spector.memory.aisme.fegr.GenerativeSelfModel selfModel =
                com.spectrayan.spector.memory.aisme.fegr.GenerativeSelfModel.fromSoulAndProfile(
                        null, com.spectrayan.spector.memory.model.CognitiveProfile.BALANCED, DIMS);
        com.spectrayan.spector.memory.aisme.fegr.MentalStateTracker tracker =
                new com.spectrayan.spector.memory.aisme.fegr.MentalStateTracker(selfModel);

        assertThat(tracker.selfModel().priorMean()[0]).isEqualTo(0.0f);

        ReflectSignal signal = ReflectSignal.builder()
                .partitionManager(partitionManager)
                .quantizer(quantizer)
                .mentalStateTracker(tracker)
                .soulDriftRefusionEnabled(true)
                .build();

        SoulDriftRefusionRelay relay = new SoulDriftRefusionRelay();
        boolean success = relay.transmit(signal);

        assertThat(success).isTrue();
        // Prior mean should have adapted from 0.0 towards 1.0 by eta=0.005
        assertThat(tracker.selfModel().priorMean()[0]).isGreaterThan(0.0f);

        semanticMemory.close();
    }
}
