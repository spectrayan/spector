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
package com.spectrayan.spector.metrics.observation;

import com.spectrayan.spector.commons.concurrent.MemoryScope;
import com.spectrayan.spector.commons.pathway.CognitivePathway;
import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.config.ObservabilityConfig;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ObservableRelayTest")
class ObservableRelayTest {

    private TestObservationRegistry registry;
    private ObservabilityConfig config;

    static class Signal {
        final List<String> history = new ArrayList<>();
    }

    @BeforeEach
    void setUp() {
        registry = TestObservationRegistry.create();
        config = ObservabilityConfig.DEFAULT;
    }

    @Test
    @DisplayName("Successful Relay: records observation with operation, namespace, and status=SUCCESS")
    void testSuccessfulRelayObservation() throws Exception {
        final SynapticRelay<Signal> delegate = new SynapticRelay<>() {
            @Override
            public boolean transmit(Signal signal) {
                signal.history.add("executed");
                return true;
            }

            @Override
            public String relayName() {
                return "test.relay";
            }
        };

        final ObservableRelay<Signal> observableRelay = new ObservableRelay<>(delegate, registry, config);
        final Signal signal = new Signal();

        MemoryScope.runWithScope("sess-100", "ns-tenant", () -> {
            try {
                boolean result = observableRelay.transmit(signal);
                assertThat(result).isTrue();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        assertThat(signal.history).containsExactly("executed");

        TestObservationRegistryAssert.assertThat(registry)
                .hasObservationWithNameEqualTo("test.relay")
                .that()
                .hasLowCardinalityKeyValue("spector.operation", "test.relay")
                .hasLowCardinalityKeyValue("spector.namespace", "ns-tenant")
                .hasLowCardinalityKeyValue("spector.status", "SUCCESS")
                .hasHighCardinalityKeyValue("spector.session_id", "sess-100");
    }

    @Test
    @DisplayName("Failed Relay: records observation with status=ERROR and error class tag")
    void testFailedRelayObservation() {
        final SynapticRelay<Signal> failingDelegate = new SynapticRelay<>() {
            @Override
            public boolean transmit(Signal signal) {
                throw new IllegalArgumentException("Invalid state");
            }

            @Override
            public String relayName() {
                return "failing.relay";
            }
        };

        final ObservableRelay<Signal> observableRelay = new ObservableRelay<>(failingDelegate, registry, config);
        final Signal signal = new Signal();

        assertThatThrownBy(() -> observableRelay.transmit(signal))
                .isInstanceOf(IllegalArgumentException.class);

        TestObservationRegistryAssert.assertThat(registry)
                .hasObservationWithNameEqualTo("failing.relay")
                .that()
                .hasLowCardinalityKeyValue("spector.status", "ERROR")
                .hasLowCardinalityKeyValue("error", "IllegalArgumentException");
    }

    @Test
    @DisplayName("Disabled Relay: bypasses observation recording when disabled in ObservabilityConfig")
    void testDisabledRelayObservation() throws Exception {
        final ObservabilityConfig disabledConfig = new ObservabilityConfig(false, 1.0, Map.of(), false);

        final SynapticRelay<Signal> delegate = new SynapticRelay<>() {
            @Override
            public boolean transmit(Signal signal) {
                signal.history.add("bypassed-obs");
                return true;
            }

            @Override
            public String relayName() {
                return "disabled.relay";
            }
        };

        final ObservableRelay<Signal> observableRelay = new ObservableRelay<>(delegate, registry, disabledConfig);
        final Signal signal = new Signal();

        boolean result = observableRelay.transmit(signal);
        assertThat(result).isTrue();
        assertThat(signal.history).containsExactly("bypassed-obs");

        TestObservationRegistryAssert.assertThat(registry)
                .hasNumberOfObservationsWithNameEqualTo("disabled.relay", 0);
    }

    @Test
    @DisplayName("Pathway Interceptor: wraps full CognitivePathway pipeline and captures all relay observations")
    void testPathwayWithObservableRelayInterceptor() {
        final CognitivePathway<Signal> pathway = CognitivePathway.<Signal>pathway("ObservedPipeline")
                .withInterceptor(ObservableRelay.interceptor(registry, config))
                .relay("stage1", s -> { s.history.add("s1"); return true; })
                .relay("stage2", s -> { s.history.add("s2"); return true; })
                .relay("stage3", s -> { s.history.add("s3"); return true; })
                .build();

        final Signal signal = new Signal();
        pathway.conduct(signal);

        assertThat(signal.history).containsExactly("s1", "s2", "s3");

        TestObservationRegistryAssert.assertThat(registry)
                .hasNumberOfObservationsWithNameEqualTo("stage1", 1)
                .hasNumberOfObservationsWithNameEqualTo("stage2", 1)
                .hasNumberOfObservationsWithNameEqualTo("stage3", 1);
    }

    @Test
    @DisplayName("ScopedValue Parent Context: passes parent observation name down via ScopedValue")
    void testParentObservationContext() throws Exception {
        final AtomicReference<String> parentSeen = new AtomicReference<>();

        final SynapticRelay<Signal> childRelay = new SynapticRelay<>() {
            @Override
            public boolean transmit(Signal signal) {
                if (ObservableRelay.PARENT_OBSERVATION.isBound()) {
                    parentSeen.set(ObservableRelay.PARENT_OBSERVATION.get());
                }
                return true;
            }

            @Override
            public String relayName() {
                return "child.relay";
            }
        };

        final ObservableRelay<Signal> observableRelay = new ObservableRelay<>(childRelay, registry, config);
        final Signal signal = new Signal();

        observableRelay.transmit(signal);

        assertThat(parentSeen.get()).isEqualTo("child.relay");
    }
}
