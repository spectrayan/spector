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

import com.spectrayan.spector.memory.persist.PartitionManager;
import com.spectrayan.spector.memory.cortex.CognitiveMemoryRouter;
import com.spectrayan.spector.memory.cortex.EpisodicRecordMemory;
import com.spectrayan.spector.memory.cortex.PartitionHandle;
import com.spectrayan.spector.memory.cortex.WorkingMemory;
import com.spectrayan.spector.memory.pathway.reflect.daemon.CircadianPolicy;
import com.spectrayan.spector.memory.kernel.layout.CognitiveRecordLayout;
import com.spectrayan.spector.memory.kernel.layout.CognitiveRecordLayout.CognitiveHeader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@DisplayName("SynapticPruningRelay: NREM Deep Sleep Pruning & Compaction Tests")
class SynapticPruningRelayTest {

    private static final int DIMS = 16;

    @Test
    @DisplayName("Downscales low importance memories and prunes below min-importance threshold")
    void testDeepSleepPruning() {
        CognitiveRecordLayout layout = new CognitiveRecordLayout(DIMS);
        EpisodicRecordMemory episodicMemory = new EpisodicRecordMemory(DIMS, 100);
        WorkingMemory workingMemory = new WorkingMemory(DIMS, 100);

        CognitiveHeader weakHeader = new CognitiveHeader(
                System.currentTimeMillis() - 100_000_000L, // old timestamp
                0L,
                1.0f,
                0.01f, // very weak importance below decayPruneThreshold (0.05)
                0,
                (short) 0,
                (byte) 0,
                (byte) 0,
                (byte) 0,
                0.1f, // weak storage strength
                (byte) 0,
                (byte) 0,
                (byte) 0,
                (short) 1,
                0.0f,
                (byte) 0
        );

        episodicMemory.write(weakHeader, new byte[layout.quantizedVecBytes()]);

        CognitiveMemoryRouter router = new CognitiveMemoryRouter(workingMemory, episodicMemory, null, null);
        PartitionManager partitionManager = Mockito.mock(PartitionManager.class);
        PartitionHandle handle = new PartitionHandle(0, null, router, null, false);
        when(partitionManager.snapshot()).thenReturn(List.of(handle));

        ReflectSignal signal = ReflectSignal.builder()
                .partitionManager(partitionManager)
                .policy(CircadianPolicy.builder().decayPruneThreshold(0.05f).build())
                .build();

        SynapticPruningRelay relay = new SynapticPruningRelay();
        boolean success = relay.transmit(signal);

        assertThat(success).isTrue();
        assertThat(signal.buildReport().tombstonedCount()).isGreaterThanOrEqualTo(1);

        episodicMemory.close();
        workingMemory.close();
    }
}
