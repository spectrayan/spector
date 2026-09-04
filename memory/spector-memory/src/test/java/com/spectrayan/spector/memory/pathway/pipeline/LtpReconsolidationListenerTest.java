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
package com.spectrayan.spector.memory.pathway.pipeline;

import com.spectrayan.spector.memory.cortex.CognitiveMemoryRouter;
import com.spectrayan.spector.memory.cortex.PartitionRegistry;
import com.spectrayan.spector.memory.cortex.StrengthMemory;
import com.spectrayan.spector.memory.cortex.index.IndexRecordMemory.MemoryLocation;
import com.spectrayan.spector.memory.cortex.index.MemoryIndex;
import com.spectrayan.spector.memory.kernel.layout.EngramLayout;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.sync.MemoryWal;
import com.spectrayan.spector.memory.sync.WalEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@DisplayName("LtpReconsolidationListenerTest")
class LtpReconsolidationListenerTest {

    @Test
    @DisplayName("onRecallComplete records recall in StrengthMemory and updates auto-LTP after cooldown")
    void testRecallTriggersStrengthAutoLtp() {
        try (Arena arena = Arena.ofConfined()) {
            MemoryIndex index = mock(MemoryIndex.class);
            PartitionRegistry partitionRegistry = mock(PartitionRegistry.class);
            MemoryWal wal = mock(MemoryWal.class);
            CognitiveMemoryRouter router = mock(CognitiveMemoryRouter.class);

            final int dimensions = 4;
            EngramLayout layout = new EngramLayout(dimensions);
            MemorySegment engramSegment = arena.allocate(layout.stride() * 2);

            // Write a timestamp into slot 0
            long creationTs = 1700000000000L;
            engramSegment.set(ValueLayout.JAVA_LONG, 0, creationTs);

            StrengthMemory strengthStore = StrengthMemory.heap(10, 10, 10);
            strengthStore.initializeDefault(MemoryType.SEMANTIC, 0, 3.0f, 1.0f, 0);

            when(index.locate("test-mem-1")).thenReturn(new MemoryLocation(MemoryType.SEMANTIC, 0, 0, 0, -1L, -1));
            when(index.findIdByOffset(0, MemoryType.SEMANTIC, 0)).thenReturn("test-mem-1");
            when(partitionRegistry.routerFor(0)).thenReturn(router);
            when(router.segmentFor(MemoryType.SEMANTIC)).thenReturn(engramSegment);
            when(router.layoutFor(MemoryType.SEMANTIC)).thenReturn(layout);
            when(router.strength()).thenReturn(strengthStore);

            LtpReconsolidationListener listener = new LtpReconsolidationListener(index, partitionRegistry, wal);

            CognitiveResult result = mock(CognitiveResult.class);
            when(result.id()).thenReturn("test-mem-1");

            // Initial recall triggers auto-LTP because lastAutoLtp was 0 (cooldown elapsed)
            listener.onRecallComplete(List.of(result));

            assertThat(strengthStore.readSpectorRecallCount(MemoryType.SEMANTIC, 0)).isEqualTo(1);
            assertThat(strengthStore.readStorageStrength(MemoryType.SEMANTIC, 0)).isGreaterThan(1.0f);
            assertThat(strengthStore.readLastAutoLtp(MemoryType.SEMANTIC, 0)).isPositive();
            verify(wal).append(eq(WalEvent.EventType.RECALL_HIT), eq("test-mem-1"), isNull());

            float currentStrength = strengthStore.readStorageStrength(MemoryType.SEMANTIC, 0);

            // Second immediate recall should NOT increment auto-LTP due to cooldown
            listener.onRecallComplete(List.of(result));

            assertThat(strengthStore.readSpectorRecallCount(MemoryType.SEMANTIC, 0)).isEqualTo(1);
            assertThat(strengthStore.readStorageStrength(MemoryType.SEMANTIC, 0)).isEqualTo(currentStrength);
            verify(wal, times(2)).append(eq(WalEvent.EventType.RECALL_HIT), eq("test-mem-1"), isNull());
        }
    }
}
