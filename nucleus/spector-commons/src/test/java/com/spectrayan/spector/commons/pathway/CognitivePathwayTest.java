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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CognitivePathway")
class CognitivePathwayTest {

    static class TestSignal implements DivergentCapable<TestSignal>, TraceableSignal {
        final List<String> steps = new ArrayList<>();
        final List<RelayTrace> traces = Collections.synchronizedList(new ArrayList<>());
        boolean traceEnabled = false;

        @Override
        public TestSignal fork() {
            final TestSignal fork = new TestSignal();
            fork.steps.addAll(this.steps);
            fork.traceEnabled = this.traceEnabled;
            synchronized (this.traces) {
                fork.traces.addAll(this.traces);
            }
            return fork;
        }

        @Override
        public void merge(List<TestSignal> forks) {
            for (TestSignal fork : forks) {
                steps.addAll(fork.steps);
                synchronized (fork.traces) {
                    traces.addAll(fork.traces);
                }
            }
        }

        @Override
        public boolean isTraceEnabled() {
            return traceEnabled;
        }

        @Override
        public void recordTrace(RelayTrace trace) {
            traces.add(trace);
        }

        @Override
        public List<RelayTrace> traces() {
            return List.copyOf(traces);
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
    @DisplayName("ErrorPolicy.FAIL_FAST: wraps non-Spector exceptions in CognitivePathwayException")
    void errorPolicyFailFast() {
        CognitivePathway<TestSignal> pathway = CognitivePathway.<TestSignal>pathway("FailFast")
                .relay("R1", s -> { s.steps.add("1"); return true; })
                .relay("R2", s -> { throw new RuntimeException("Boom"); }, ErrorPolicy.FAIL_FAST)
                .relay("R3", s -> { s.steps.add("3"); return true; })
                .build();

        TestSignal signal = new TestSignal();

        assertThatThrownBy(() -> pathway.conduct(signal))
                .isInstanceOf(CognitivePathwayException.class)
                .hasMessageContaining("Failed at relay 'R2'")
                .hasCauseInstanceOf(RuntimeException.class);

        assertThat(signal.steps).containsExactly("1");
    }

    @Test
    @DisplayName("Domain Exception Preservation: SpectorException is rethrown directly")
    void domainExceptionPreserved() {
        CognitivePathway<TestSignal> pathway = CognitivePathway.<TestSignal>pathway("DomainPreservation")
                .relay("R1", s -> {
                    throw new SpectorValidationException(ErrorCode.DIMENSIONS_MISMATCH, 384, 768);
                }, ErrorPolicy.FAIL_FAST)
                .build();

        TestSignal signal = new TestSignal();

        assertThatThrownBy(() -> pathway.conduct(signal))
                .isInstanceOf(SpectorValidationException.class)
                .hasMessageContaining("[SPE-100-002]");
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
    @DisplayName("DivergentRelay with per-branch error policies: graceful branch error allows valid branch to merge")
    void divergentBranchGracefulDegradation() {
        SynapticRelay<TestSignal> successfulBranch = s -> { s.steps.add("Success"); return true; };
        SynapticRelay<TestSignal> failingBranch = s -> { throw new RuntimeException("Branch Failure"); };

        CognitivePathway<TestSignal> pathway = CognitivePathway.<TestSignal>pathway("DivergentGraceful")
                .divergent("DivGraceful",
                        List.of(successfulBranch, failingBranch),
                        List.of(ErrorPolicy.FAIL_FAST, ErrorPolicy.DEGRADE_GRACEFULLY))
                .build();

        TestSignal signal = new TestSignal();
        pathway.conduct(signal);

        assertThat(signal.steps).containsExactly("Success");
    }

    @Test
    @DisplayName("CircuitBreakerRelay: trips OPEN after threshold failures and bypasses execution")
    void circuitBreakerTripsAndRecovers() throws Exception {
        AtomicInteger attempts = new AtomicInteger(0);
        SynapticRelay<TestSignal> failingRelay = s -> {
            attempts.incrementAndGet();
            throw new RuntimeException("Service Unavailable");
        };

        CircuitBreakerRelay<TestSignal> cbRelay = new CircuitBreakerRelay<>(failingRelay, 3, 50L);
        assertThat(cbRelay.state()).isEqualTo(CircuitBreakerRelay.State.CLOSED);

        TestSignal signal = new TestSignal();

        // 3 consecutive failures
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> cbRelay.transmit(signal)).isInstanceOf(RuntimeException.class);
        }

        assertThat(cbRelay.state()).isEqualTo(CircuitBreakerRelay.State.OPEN);
        assertThat(cbRelay.failureCount()).isEqualTo(3);

        // While OPEN, transmit returns true (bypassed) without invoking delegate
        boolean bypassed = cbRelay.transmit(signal);
        assertThat(bypassed).isTrue();
        assertThat(attempts.get()).isEqualTo(3); // No new invocation

        // Wait for cooldown
        Thread.sleep(70L);

        // Reset manually
        cbRelay.reset();
        assertThat(cbRelay.state()).isEqualTo(CircuitBreakerRelay.State.CLOSED);
        assertThat(cbRelay.failureCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("Signal Step Tracing: records fine-grained trace records for all executed relays")
    void signalStepTracing() {
        CognitivePathway<TestSignal> pathway = CognitivePathway.<TestSignal>pathway("TracedPathway")
                .relay("R1", s -> { s.steps.add("1"); return true; })
                .gated("G1", s -> false, s -> { s.steps.add("2"); return true; }, ErrorPolicy.DEGRADE_GRACEFULLY)
                .relay("R3", s -> { throw new RuntimeException("Soft error"); }, ErrorPolicy.DEGRADE_GRACEFULLY)
                .relay("R4", s -> { s.steps.add("4"); return true; })
                .build();

        TestSignal signal = new TestSignal();
        signal.traceEnabled = true;

        pathway.conduct(signal);

        List<RelayTrace> traces = signal.traces();
        assertThat(traces).hasSize(4);
        assertThat(traces.get(0).relayName()).isEqualTo("R1");
        assertThat(traces.get(0).status()).isEqualTo(RelayTrace.TraceStatus.EXECUTED);

        assertThat(traces.get(1).relayName()).isEqualTo("G1");
        assertThat(traces.get(1).status()).isEqualTo(RelayTrace.TraceStatus.EXECUTED); // Gated relay itself executed and returned true

        assertThat(traces.get(2).relayName()).isEqualTo("R3");
        assertThat(traces.get(2).status()).isEqualTo(RelayTrace.TraceStatus.DEGRADED);
        assertThat(traces.get(2).detail()).isEqualTo("Soft error");

        assertThat(traces.get(3).relayName()).isEqualTo("R4");
        assertThat(traces.get(3).status()).isEqualTo(RelayTrace.TraceStatus.EXECUTED);
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
    void pathwayName() {
        CognitivePathway<TestSignal> pathway = CognitivePathway.<TestSignal>pathway("TestName").build();
        assertThat(pathway.pathwayName()).isEqualTo("TestName");
    }

    @Test
    @DisplayName("Relay Name: correctly reports name via NamedRelay")
    void relayName() {
        SynapticRelay<TestSignal> named = new NamedRelay<>("MyRelay", s -> true);
        assertThat(named.relayName()).isEqualTo("MyRelay");
    }
}
