/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-synapse/LICENSE
 *
 * Change Date: July 6, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.synapse.platform.events;

import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.model.MemoryType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@DisplayName("TelemetryBroadcasterService")
class TelemetryBroadcasterServiceTest {

    private TelemetryBroadcasterService service;
    private EventPublisher mockPublisher;
    private SpectorMemory mockMemory;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        mockPublisher = mock(EventPublisher.class);
        mockMemory = mock(SpectorMemory.class);

        ObjectProvider<SpectorMemory> memProvider = mock(ObjectProvider.class);
        when(memProvider.getIfAvailable()).thenReturn(mockMemory);

        service = new TelemetryBroadcasterService(mockPublisher, mock(ObjectProvider.class), memProvider);
    }

    @Test
    @DisplayName("getCurrentDiagnostics — compiles complete diagnostic telemetry map")
    void getCurrentDiagnostics() {
        when(mockMemory.totalMemories()).thenReturn(100);
        when(mockMemory.memoryCount(MemoryType.WORKING)).thenReturn(10);
        when(mockMemory.memoryCount(MemoryType.EPISODIC)).thenReturn(30);
        when(mockMemory.memoryCount(MemoryType.SEMANTIC)).thenReturn(50);
        when(mockMemory.memoryCount(MemoryType.PROCEDURAL)).thenReturn(10);

        Map<String, Object> diag = service.getCurrentDiagnostics(mockMemory);
        assertThat(diag).isNotEmpty();
        assertThat(diag.get("eventType")).isEqualTo("cortex.memory.diagnostic");
        assertThat(diag.get("workingCount")).isEqualTo(10);
        assertThat(diag.get("episodicCount")).isEqualTo(30);
        assertThat(diag.get("semanticCount")).isEqualTo(50);
        assertThat(diag.get("proceduralCount")).isEqualTo(10);
    }

    @Test
    @DisplayName("getDecayCurve — generates 0-30 day mathematical retention curve")
    void getDecayCurve() {
        List<Map<String, Object>> curve = service.getDecayCurve(mockMemory);
        assertThat(curve).isNotEmpty();
        assertThat(curve.getFirst().get("ageDays")).isEqualTo(0.0);
        assertThat(curve.getFirst().get("rawDecay")).isEqualTo(1.0);
        assertThat(curve.getFirst().get("ltpDecay")).isEqualTo(1.0);

        // Retention decays monotonically
        double lastRaw = 1.0;
        for (var pt : curve) {
            double raw = (double) pt.get("rawDecay");
            assertThat(raw).isLessThanOrEqualTo(lastRaw + 1e-6);
            lastRaw = raw;
        }
    }

    @Test
    @DisplayName("getHardwareInfo — detects CPU architecture and SIMD species")
    void getHardwareInfo() {
        Map<String, Object> hw = service.getHardwareInfo();
        assertThat(hw).isNotEmpty();
        assertThat(hw.get("architecture")).isNotNull();
        assertThat(hw.get("simdAccelerationActive")).isEqualTo(true);
        assertThat((int) hw.get("simdLaneCount")).isGreaterThanOrEqualTo(8);
    }

    @Test
    @DisplayName("broadcastHeartbeat — calculates delta rates and emits events")
    void broadcastHeartbeat() {
        service.recordRecall();
        service.recordRecall();
        service.recordRemember();
        service.broadcastHeartbeat();

        verify(mockPublisher, atLeastOnce()).cortexEvent(eq("cortex.memory.diagnostic"), any());
        verify(mockPublisher, atLeastOnce()).cortexEvent(eq("cortex.metrics.tick"), any());
    }
}
