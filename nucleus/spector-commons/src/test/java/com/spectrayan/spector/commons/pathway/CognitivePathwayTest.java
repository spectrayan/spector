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
package com.spectrayan.spector.commons.pathway;

import static org.assertj.core.api.Assertions.*;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CognitivePathway")
class CognitivePathwayTest {

    static class TestSignal implements DivergentCapable<TestSignal> {
        final List<String> steps = new ArrayList<>();

        @Override
        public TestSignal fork() {
            return new TestSignal();
        }

        @Override
        public void merge(List<TestSignal> forks) {
            for (TestSignal fork : forks) {
                steps.addAll(fork.steps);
            }
        }
    }

    @Test
    @DisplayName("Linear Pathway: relays execute in order and append to signal")
    void linearPathway() {
        CognitivePathway<TestSignal> pathway = CognitivePathway.<TestSignal>pathway("Linear")
                .relay("R1", s -> { s.steps.add("1"); return true; })
                .relay("R2", s -> { s.steps.add("2"); return true; })
                .relay("R3", s -> { s.steps.add("3"); return true; })
                .build();

        TestSignal signal = new TestSignal();
        pathway.conduct(signal);

        assertThat(signal.steps).containsExactly("1", "2", "3");
    }

    @Test
    @DisplayName("Gated Relay: executes delegate when gate returns true")
    void gatedRelayActive() {
        CognitivePathway<TestSignal> pathway = CognitivePathway.<TestSignal>pathway("GatedActive")
                .gated("G1", s -> true, s -> { s.steps.add("Active"); return true; }, ErrorPolicy.FAIL_FAST)
                .build();

        TestSignal signal = new TestSignal();
        pathway.conduct(signal);

        assertThat(signal.steps).containsExactly("Active");
    }

    @Test
    @DisplayName("Gated Relay: skips delegate when gate returns false")
    void gatedRelayInhibited() {
        CognitivePathway<TestSignal> pathway = CognitivePathway.<TestSignal>pathway("GatedInhibited")
                .gated("G1", s -> false, s -> { s.steps.add("Inhibited"); return true; }, ErrorPolicy.FAIL_FAST)
                .build();

        TestSignal signal = new TestSignal();
        pathway.conduct(signal);

        assertThat(signal.steps).isEmpty();
    }

    @Test
    @DisplayName("Short-Circuit: pathway stops when a relay returns false")
    void shortCircuit() {
        CognitivePathway<TestSignal> pathway = CognitivePathway.<TestSignal>pathway("ShortCircuit")
                .relay("R1", s -> { s.steps.add("1"); return true; })
                .relay("R2", s -> { s.steps.add("2"); return false; })
                .relay("R3", s -> { s.steps.add("3"); return true; })
                .build();

        TestSignal signal = new TestSignal();
        pathway.conduct(signal);

        assertThat(signal.steps).containsExactly("1", "2");
    }

    @Test
    @DisplayName("ErrorPolicy.FAIL_FAST: exception propagates")
    void errorPolicyFailFast() {
        CognitivePathway<TestSignal> pathway = CognitivePathway.<TestSignal>pathway("FailFast")
                .relay("R1", s -> { s.steps.add("1"); return true; })
                .relay("R2", s -> { throw new RuntimeException("Boom"); }, ErrorPolicy.FAIL_FAST)
                .relay("R3", s -> { s.steps.add("3"); return true; })
                .build();

        TestSignal signal = new TestSignal();

        assertThatThrownBy(() -> pathway.conduct(signal))
                .isInstanceOf(CognitivePathwayException.class)
                .hasMessageContaining("Failed at relay: R2")
                .hasCauseInstanceOf(RuntimeException.class);

        assertThat(signal.steps).containsExactly("1");
    }

    @Test
    @DisplayName("ErrorPolicy.DEGRADE_GRACEFULLY: exception is caught and pathway continues")
    void errorPolicyDegradeGracefully() {
        CognitivePathway<TestSignal> pathway = CognitivePathway.<TestSignal>pathway("DegradeGracefully")
                .relay("R1", s -> { s.steps.add("1"); return true; })
                .relay("R2", s -> { throw new RuntimeException("Boom"); }, ErrorPolicy.DEGRADE_GRACEFULLY)
                .relay("R3", s -> { s.steps.add("3"); return true; })
                .build();

        TestSignal signal = new TestSignal();
        pathway.conduct(signal);

        assertThat(signal.steps).containsExactly("1", "3");
    }

    @Test
    @DisplayName("DivergentRelay: forks signal, runs branches in parallel, and merges results")
    void divergentRelay() {
        SynapticRelay<TestSignal> branch1 = s -> { s.steps.add("B1"); return true; };
        SynapticRelay<TestSignal> branch2 = s -> { s.steps.add("B2"); return true; };

        CognitivePathway<TestSignal> pathway = CognitivePathway.<TestSignal>pathway("Divergent")
                .divergent("Div1", List.of(branch1, branch2))
                .build();

        TestSignal signal = new TestSignal();
        pathway.conduct(signal);

        assertThat(signal.steps).containsExactlyInAnyOrder("B1", "B2");
    }

    @Test
    @DisplayName("ConsolidationRelay: dispatches asynchronous action")
    void consolidationRelay() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);

        CognitivePathway<TestSignal> pathway = CognitivePathway.<TestSignal>pathway("Consolidate")
                .consolidate("C1", s -> {
                    s.steps.add("Async");
                    latch.countDown();
                })
                .build();

        TestSignal signal = new TestSignal();
        pathway.conduct(signal);

        boolean await = latch.await(2, TimeUnit.SECONDS);
        assertThat(await).isTrue();
        assertThat(signal.steps).containsExactly("Async");
    }

    @Test
    @DisplayName("Interceptor: applies to all added relays")
    void interceptor() {
        AtomicInteger invocationCount = new AtomicInteger(0);

        CognitivePathway<TestSignal> pathway = CognitivePathway.<TestSignal>pathway("Intercepted")
                .withInterceptor(relay -> signal -> {
                    invocationCount.incrementAndGet();
                    return relay.transmit(signal);
                })
                .relay("R1", s -> true)
                .relay("R2", s -> true)
                .build();

        TestSignal signal = new TestSignal();
        pathway.conduct(signal);

        assertThat(invocationCount.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("Pathway Name: asserts the internal pathwayName is correctly stored")
    void pathwayName() throws Exception {
        CognitivePathway<TestSignal> pathway = CognitivePathway.<TestSignal>pathway("TestName").build();

        Field nameField = CognitivePathway.class.getDeclaredField("pathwayName");
        nameField.setAccessible(true);
        String name = (String) nameField.get(pathway);

        assertThat(name).isEqualTo("TestName");
    }

    @Test
    @DisplayName("Relay Name: correctly reports name via NamedRelay")
    void relayName() {
        SynapticRelay<TestSignal> named = new NamedRelay<>("MyRelay", s -> true);
        assertThat(named.relayName()).isEqualTo("MyRelay");
    }
}
