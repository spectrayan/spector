/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.spectrayan.spector.synapse.platform.events;

import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.kernel.api.MemoryType;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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
    private MeterRegistry meterRegistry;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        mockPublisher = mock(EventPublisher.class);
        mockMemory = mock(SpectorMemory.class);
        meterRegistry = new SimpleMeterRegistry();

        ObjectProvider<SpectorMemory> memProvider = mock(ObjectProvider.class);
        when(memProvider.getIfAvailable()).thenReturn(mockMemory);

        ObjectProvider<MeterRegistry> registryProvider = mock(ObjectProvider.class);
        when(registryProvider.getIfAvailable()).thenReturn(meterRegistry);

        service = new TelemetryBroadcasterService(mockPublisher, mock(ObjectProvider.class), memProvider, registryProvider);
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
    @DisplayName("broadcastHeartbeat — reads ops/sec from MeterRegistry timers with namespace tag (ADR-0083)")
    void broadcastHeartbeat() {
        when(mockMemory.namespaceId()).thenReturn("tenant-alpha");

        // Tagged observations for tenant-alpha
        Timer.builder("spector.memory.recall")
                .tag("spector.namespace", "tenant-alpha")
                .register(meterRegistry)
                .record(java.time.Duration.ofMillis(5));
        Timer.builder("spector.memory.recall")
                .tag("spector.namespace", "tenant-alpha")
                .register(meterRegistry)
                .record(java.time.Duration.ofMillis(3));
        Timer.builder("spector.memory.remember")
                .tag("spector.namespace", "tenant-alpha")
                .register(meterRegistry)
                .record(java.time.Duration.ofMillis(10));

        // Untagged timer from another context must NOT be inherited (leak prevention)
        Timer.builder("spector.memory.recall")
                .register(meterRegistry)
                .record(java.time.Duration.ofMillis(100));

        service.broadcastHeartbeat();

        verify(mockPublisher, atLeastOnce()).cortexEvent(eq("cortex.memory.diagnostic"), any());

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<Map<String, Object>> tickCaptor = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(mockPublisher, atLeastOnce()).cortexEvent(eq("cortex.metrics.tick"), tickCaptor.capture());
        Map<String, Object> tick = tickCaptor.getValue();
        assertThat(tick.get("namespace")).isEqualTo("tenant-alpha");

        var history = service.getLiveMetricsHistory("tenant-alpha");
        assertThat(history).isNotEmpty();
        assertThat(history.getLast().get("namespace")).isEqualTo("tenant-alpha");

        // Disjoint namespace returns empty history and does not inherit data
        var otherHistory = service.getLiveMetricsHistory("tenant-beta");
        assertThat(otherHistory).isEmpty();
    }

    @Test
    @DisplayName("evictNamespace — clears rolling history for evicted namespace (ADR-0083)")
    void evictNamespace_clearsRollingHistory() {
        when(mockMemory.namespaceId()).thenReturn("tenant-evict");
        Timer.builder("spector.memory.recall")
                .tag("spector.namespace", "tenant-evict")
                .register(meterRegistry)
                .record(java.time.Duration.ofMillis(5));

        service.broadcastHeartbeat();
        assertThat(service.getLiveMetricsHistory("tenant-evict")).isNotEmpty();

        service.evictNamespace("tenant-evict");
        assertThat(service.getLiveMetricsHistory("tenant-evict")).isEmpty();
    }
}
