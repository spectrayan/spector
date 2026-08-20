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
package com.spectrayan.spector.metrics;

import com.spectrayan.spector.commons.concurrent.MemoryScope;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.model.ReflectReport;
import com.spectrayan.spector.config.ObservabilityConfig;
import com.spectrayan.spector.metrics.observation.MicrometerMemoryObservationHook;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ObservedSpectorMemoryTest {

    @Mock
    private SpectorMemory delegate;

    private TestObservationRegistry registry;
    private ObservedSpectorMemory memory;

    @BeforeEach
    void setUp() {
        registry = TestObservationRegistry.create();
        memory = new ObservedSpectorMemory(delegate, registry, ObservabilityConfig.DEFAULT);
    }

    @Test
    @DisplayName("remember() creates observation with operation and tier tags")
    void testRememberObservation() {
        MemoryScope.runWithScope("sess-1", "ns-tenant",
                () -> memory.remember("mem-123", "Test remember text", MemoryType.SEMANTIC, MemorySource.USER_STATED));

        verify(delegate).remember("mem-123", "Test remember text", MemoryType.SEMANTIC, MemorySource.USER_STATED);

        TestObservationRegistryAssert.assertThat(registry)
                .hasObservationWithNameEqualTo("spector.memory.remember")
                .that()
                .hasLowCardinalityKeyValue("spector.operation", "spector.memory.remember")
                .hasLowCardinalityKeyValue("spector.tier", "SEMANTIC")
                .hasLowCardinalityKeyValue("spector.namespace", "ns-tenant")
                .hasLowCardinalityKeyValue("spector.status", "SUCCESS")
                .hasHighCardinalityKeyValue("spector.session_id", "sess-1");
    }

    @Test
    @DisplayName("recall() creates observation with query and results tags")
    void testRecallObservation() {
        CognitiveResult result = new CognitiveResult(
                "mem-1", "Alice is an engineer", 0.95f, 0.8f, 0f, 1, (byte) 0,
                MemoryType.SEMANTIC, MemorySource.USER_STATED, new String[]{"engineer"}, 1.0f, 1.0f);

        RecallOptions options = RecallOptions.builder().topK(5).memoryTypes(MemoryType.SEMANTIC).build();
        when(delegate.recall(eq("who is Alice"), any(RecallOptions.class)))
                .thenReturn(List.of(result));

        List<CognitiveResult> results = memory.recall("who is Alice", options);

        assertThat(results).hasSize(1);
        verify(delegate).recall(eq("who is Alice"), any(RecallOptions.class));

        TestObservationRegistryAssert.assertThat(registry)
                .hasObservationWithNameEqualTo("spector.memory.recall")
                .that()
                .hasLowCardinalityKeyValue("spector.operation", "spector.memory.recall")
                .hasLowCardinalityKeyValue("spector.status", "SUCCESS")
                .hasHighCardinalityKeyValue("spector.query", "who is Alice");
    }

    @Test
    @DisplayName("forget() creates observation and records status")
    void testForgetObservation() {
        memory.forget("mem-del");

        verify(delegate).forget("mem-del");

        TestObservationRegistryAssert.assertThat(registry)
                .hasObservationWithNameEqualTo("spector.memory.forget")
                .that()
                .hasLowCardinalityKeyValue("spector.status", "SUCCESS")
                .hasHighCardinalityKeyValue("spector.memory_id", "mem-del");
    }

    @Test
    @DisplayName("reflect() creates observation and returns ReflectReport")
    void testReflectObservation() {
        ReflectReport mockReport = new ReflectReport(5, 2, 1, 0, java.time.Duration.ofMillis(100), null, 3, 3, 0.45f, 5);
        when(delegate.reflect()).thenReturn(mockReport);

        ReflectReport report = memory.reflect();

        assertThat(report).isNotNull();
        assertThat(report.consolidatedCount()).isEqualTo(5);
        verify(delegate).reflect();

        TestObservationRegistryAssert.assertThat(registry)
                .hasObservationWithNameEqualTo("spector.memory.reflect")
                .that()
                .hasLowCardinalityKeyValue("spector.operation", "spector.memory.reflect")
                .hasLowCardinalityKeyValue("spector.status", "SUCCESS");
    }

    @Test
    @DisplayName("Error in delegate records error status and exception tag")
    void testErrorObservation() {
        doThrow(new IllegalStateException("Memory store closed"))
                .when(delegate).forget("mem-err");

        assertThatThrownBy(() -> memory.forget("mem-err"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Memory store closed");

        TestObservationRegistryAssert.assertThat(registry)
                .hasObservationWithNameEqualTo("spector.memory.forget")
                .that()
                .hasLowCardinalityKeyValue("spector.status", "ERROR")
                .hasLowCardinalityKeyValue("error", "IllegalStateException");
    }

    @Test
    @DisplayName("MicrometerMemoryObservationHook bridges zero-dependency SPI to ObservationRegistry")
    void testMemoryObservationHookAdapter() throws Exception {
        var hook = new MicrometerMemoryObservationHook(registry, ObservabilityConfig.DEFAULT);

        try (var handle = hook.start(com.spectrayan.spector.commons.observation.MemoryObservationHook.CHUNKING, Map.of(
                "tier", "EPISODIC",
                "namespace", "ns-42",
                "custom_key", "custom_val"
        ))) {
            assertThat(handle).isNotNull();
        }

        TestObservationRegistryAssert.assertThat(registry)
                .hasObservationWithNameEqualTo("chunking")
                .that()
                .hasLowCardinalityKeyValue("spector.tier", "EPISODIC")
                .hasLowCardinalityKeyValue("spector.namespace", "ns-42")
                .hasHighCardinalityKeyValue("custom_key", "custom_val");
    }
}
