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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the {@link RelayTrace.TraceStatus#BYPASSED} rows of the ADR-0036 §13 trace table.
 *
 * <p>Before this, {@code BYPASSED} had zero producers in main source: a relay that
 * self-bypassed returned {@code true} and was traced as {@code EXECUTED}, so a closed
 * gate, an OPEN circuit and a full bulkhead were all indistinguishable from real work.</p>
 */
@DisplayName("ADR-0036 §13 — BYPASSED trace production")
class BypassedTraceTest {

    /** Minimal contextual + traceable signal. */
    static final class TracedSignal extends AbstractSignal {
    }

    private static TracedSignal tracedSignal() {
        final TracedSignal signal = new TracedSignal();
        signal.bind(DefaultPathwayContext.builder().traceEnabled(true).build());
        return signal;
    }

    private static RelayTrace traceFor(final TracedSignal signal, final String relayName) {
        final List<RelayTrace> traces = signal.traces();
        return traces.stream()
                .filter(t -> relayName.equals(t.relayName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no trace for relay '" + relayName + "' in " + traces));
    }

    @Test
    @DisplayName("Closed gate traces BYPASSED with the specification reason, not EXECUTED")
    void closedGateTracesBypassed() {
        final AtomicBoolean delegateRan = new AtomicBoolean(false);
        final PathwayEngine<TracedSignal> pathway = PathwayEngine.<TracedSignal>builder("GateTrace")
                .gated("Gated", s -> false, s -> {
                    delegateRan.set(true);
                    return true;
                }, ErrorPolicy.FAIL_FAST)
                .build();

        final TracedSignal signal = tracedSignal();
        pathway.conduct(signal);

        assertThat(delegateRan).isFalse();
        final RelayTrace trace = traceFor(signal, "Gated");
        assertThat(trace.status()).isEqualTo(RelayTrace.TraceStatus.BYPASSED);
        assertThat(trace.detail()).isEqualTo("gate condition false");
    }

    @Test
    @DisplayName("Open gate still traces EXECUTED with a null detail")
    void openGateTracesExecuted() {
        final PathwayEngine<TracedSignal> pathway = PathwayEngine.<TracedSignal>builder("GateTrace")
                .gated("Gated", s -> true, s -> true, ErrorPolicy.FAIL_FAST)
                .build();

        final TracedSignal signal = tracedSignal();
        pathway.conduct(signal);

        final RelayTrace trace = traceFor(signal, "Gated");
        assertThat(trace.status()).isEqualTo(RelayTrace.TraceStatus.EXECUTED);
        assertThat(trace.detail()).isNull();
    }

    @Test
    @DisplayName("OPEN circuit with onOpen=BYPASS traces BYPASSED with circuit_open:<name>")
    void openCircuitTracesBypassed() {
        final String breakerName = "bypass-trace-breaker-" + System.nanoTime();
        final CircuitBreaker breaker = new CircuitBreaker(
                breakerName,
                CircuitBreakerConfig.builder().failureThreshold(1).build());
        breaker.onFailure(breaker.tryAcquire(OnOpen.BYPASS), FaultKind.TRANSIENT);
        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.OPEN);

        final AtomicBoolean delegateRan = new AtomicBoolean(false);
        final SynapticRelay<TracedSignal> delegate = new NamedRelay<>("Remote", s -> {
            delegateRan.set(true);
            return true;
        });
        final CircuitBreakerRelay<TracedSignal> relay =
                new CircuitBreakerRelay<>(delegate, breaker, OnOpen.BYPASS);
        final PathwayEngine<TracedSignal> pathway = PathwayEngine.<TracedSignal>builder("BreakerTrace")
                .relay("Remote", relay)
                .build();

        final TracedSignal signal = tracedSignal();
        pathway.conduct(signal);

        assertThat(delegateRan).isFalse();
        final RelayTrace trace = traceFor(signal, "Remote");
        assertThat(trace.status()).isEqualTo(RelayTrace.TraceStatus.BYPASSED);
        assertThat(trace.detail()).isEqualTo("circuit_open:" + breakerName);
    }

    @Test
    @DisplayName("Full bulkhead with onReject=BYPASS traces BYPASSED with bulkhead:<name>")
    void fullBulkheadTracesBypassed() throws InterruptedException {
        final String bulkheadName = "bypass-trace-bulkhead-" + System.nanoTime();
        final BulkheadConfig config = BulkheadConfig.of(1, Duration.ZERO, OnReject.BYPASS);

        // Occupy the only permit so the traced conduction is guaranteed to be rejected.
        final Semaphore semaphore = new Semaphore(1, true);
        semaphore.acquire();

        final AtomicBoolean delegateRan = new AtomicBoolean(false);
        final SynapticRelay<TracedSignal> delegate = new NamedRelay<>("Nested", s -> {
            delegateRan.set(true);
            return true;
        });
        final BulkheadRelay<TracedSignal> relay =
                new BulkheadRelay<>(delegate, semaphore, config, "Nested", bulkheadName);
        final PathwayEngine<TracedSignal> pathway = PathwayEngine.<TracedSignal>builder("BulkheadTrace")
                .relay("Nested", relay)
                .build();

        final TracedSignal signal = tracedSignal();
        pathway.conduct(signal);

        assertThat(delegateRan).isFalse();
        final RelayTrace trace = traceFor(signal, "Nested");
        assertThat(trace.status()).isEqualTo(RelayTrace.TraceStatus.BYPASSED);
        assertThat(trace.detail()).isEqualTo("bulkhead:" + bulkheadName);
    }

    @Test
    @DisplayName("Timeout under DEGRADE traces DEGRADED with timeout:<budget>")
    void timeoutTracesBudgetDetail() {
        final Duration budget = Duration.ofMillis(50);
        final TimeoutRelay<TracedSignal> relay = new TimeoutRelay<>(s -> {
            Thread.sleep(2_000);
            return true;
        }, budget, "Slow");
        final PathwayEngine<TracedSignal> pathway = PathwayEngine.<TracedSignal>builder("TimeoutTrace")
                .relay("Slow", relay, ErrorPolicy.DEGRADE_GRACEFULLY)
                .build();

        final TracedSignal signal = tracedSignal();
        pathway.conduct(signal);

        final RelayTrace trace = traceFor(signal, "Slow");
        assertThat(trace.status()).isEqualTo(RelayTrace.TraceStatus.DEGRADED);
        assertThat(trace.detail()).isEqualTo("timeout:" + budget);
    }
}
