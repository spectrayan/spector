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

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CircuitBreaker and Resilience Primitives")
class CircuitBreakerTest {

    static class Signal extends AbstractSignal {
        int count = 0;
    }

    @Nested
    @DisplayName("CircuitBreaker State Machine")
    class StateMachineTests {

        @Test
        @DisplayName("Starts CLOSED and ignores non-trip kinds (VALIDATION, CONTRACT, CONTROL, INTERRUPTED)")
        void ignoresNonTripKinds() {
            var config = CircuitBreakerConfig.builder()
                    .failureThreshold(2)
                    .cooldown(Duration.ofMillis(100))
                    .build();
            var breaker = new CircuitBreaker("test-breaker", config);

            assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.CLOSED);

            // Validation fault
            var p1 = breaker.tryAcquire(OnOpen.FAIL);
            assertThat(p1.isClosed()).isTrue();
            breaker.onFailure(p1, FaultKind.VALIDATION);
            assertThat(breaker.consecutiveFailures()).isEqualTo(0);
            assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.CLOSED);

            // Contract fault
            var p2 = breaker.tryAcquire(OnOpen.FAIL);
            breaker.onFailure(p2, FaultKind.CONTRACT);
            assertThat(breaker.consecutiveFailures()).isEqualTo(0);

            // Interrupted fault
            var p3 = breaker.tryAcquire(OnOpen.FAIL);
            breaker.onFailure(p3, FaultKind.INTERRUPTED);
            assertThat(breaker.consecutiveFailures()).isEqualTo(0);
            assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.CLOSED);
        }

        @Test
        @DisplayName("Trips to OPEN on reaching failure threshold with trip kinds")
        void tripsToOpen() {
            var config = CircuitBreakerConfig.builder()
                    .failureThreshold(2)
                    .cooldown(Duration.ofMillis(50))
                    .build();
            var breaker = new CircuitBreaker("test-breaker", config);

            var p1 = breaker.tryAcquire(OnOpen.FAIL);
            breaker.onFailure(p1, FaultKind.TRANSIENT);
            assertThat(breaker.consecutiveFailures()).isEqualTo(1);
            assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.CLOSED);

            var p2 = breaker.tryAcquire(OnOpen.FAIL);
            breaker.onFailure(p2, FaultKind.DOWNSTREAM);
            assertThat(breaker.consecutiveFailures()).isEqualTo(2);
            assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.OPEN);

            // In OPEN state, fail vs bypass
            assertThatThrownBy(() -> breaker.tryAcquire(OnOpen.FAIL))
                    .isInstanceOf(CircuitOpenException.class)
                    .satisfies(e -> {
                        var coe = (CircuitOpenException) e;
                        assertThat(coe.errorCode()).isEqualTo(ErrorCode.PATHWAY_CIRCUIT_OPEN);
                        assertThat(coe.kind()).isEqualTo(FaultKind.TRANSIENT);
                        assertThat(coe.breakerName()).isEqualTo("test-breaker");
                    });

            var bypassPermit = breaker.tryAcquire(OnOpen.BYPASS);
            assertThat(bypassPermit.isBypass()).isTrue();
        }

        @Test
        @DisplayName("Recovers through HALF_OPEN after cooldown")
        void recoversThroughHalfOpen() throws Exception {
            var config = CircuitBreakerConfig.builder()
                    .failureThreshold(1)
                    .cooldown(Duration.ofMillis(30))
                    .halfOpenProbes(1)
                    .halfOpenSuccesses(2)
                    .build();
            var breaker = new CircuitBreaker("recovering-breaker", config);

            var p = breaker.tryAcquire(OnOpen.FAIL);
            breaker.onFailure(p, FaultKind.TRANSIENT);
            assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.OPEN);

            Thread.sleep(40);

            // First call after cooldown enters HALF_OPEN as probe
            var probe1 = breaker.tryAcquire(OnOpen.FAIL);
            assertThat(probe1.isProbe()).isTrue();
            assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.HALF_OPEN);

            // Saturated probe capacity triggers OnOpen
            assertThatThrownBy(() -> breaker.tryAcquire(OnOpen.FAIL))
                    .isInstanceOf(CircuitOpenException.class);
            assertThat(breaker.tryAcquire(OnOpen.BYPASS).isBypass()).isTrue();

            // First probe succeeds (needs 2)
            breaker.onSuccess(probe1);
            assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.HALF_OPEN);

            // Second probe
            var probe2 = breaker.tryAcquire(OnOpen.FAIL);
            assertThat(probe2.isProbe()).isTrue();
            breaker.onSuccess(probe2);

            // Closed!
            assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.CLOSED);
            assertThat(breaker.consecutiveFailures()).isEqualTo(0);
        }

        @Test
        @DisplayName("Probe failure in HALF_OPEN trips immediately back to OPEN")
        void probeFailureReopens() throws Exception {
            var config = CircuitBreakerConfig.builder()
                    .failureThreshold(1)
                    .cooldown(Duration.ofMillis(20))
                    .build();
            var breaker = new CircuitBreaker("probe-fail-breaker", config);

            var p = breaker.tryAcquire(OnOpen.FAIL);
            breaker.onFailure(p, FaultKind.TRANSIENT);
            assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.OPEN);

            Thread.sleep(30);

            var probe = breaker.tryAcquire(OnOpen.FAIL);
            assertThat(probe.isProbe()).isTrue();
            breaker.onFailure(probe, FaultKind.TRANSIENT);

            assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.OPEN);
        }
    }

    @Nested
    @DisplayName("CircuitBreakerRegistry")
    class RegistryTests {

        @Test
        @DisplayName("First registration wins on mismatched config")
        void firstRegistrationWins() {
            var registry = CircuitBreakerRegistry.create();
            var cfg1 = CircuitBreakerConfig.builder().failureThreshold(3).build();
            var cfg2 = CircuitBreakerConfig.builder().failureThreshold(10).build();

            var breaker1 = registry.get("shared-target", cfg1);
            var breaker2 = registry.get("shared-target", cfg2);

            assertThat(breaker1).isSameAs(breaker2);
            assertThat(breaker2.config().failureThreshold()).isEqualTo(3);
        }

        @Test
        @DisplayName("resetAll resets all registered circuit breakers")
        void resetAll() {
            var registry = CircuitBreakerRegistry.create();
            var breaker = registry.get("b1", CircuitBreakerConfig.builder().failureThreshold(1).build());
            var p = breaker.tryAcquire(OnOpen.FAIL);
            breaker.onFailure(p, FaultKind.TRANSIENT);
            assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.OPEN);

            registry.resetAll();
            assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.CLOSED);
            assertThat(breaker.consecutiveFailures()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("Shared Breaker & Call-Site Semantics")
    class SharedBreakerTests {

        @Test
        @DisplayName("Shared embed-provider breaker: trips once, Recall fails fast while Dream gracefully bypasses")
        void sharedEmbedProviderBreakerRecallFailsDreamBypasses() throws Exception {
            var registry = CircuitBreakerRegistry.create();
            var config = CircuitBreakerConfig.builder()
                    .failureThreshold(1)
                    .cooldown(Duration.ofMillis(500))
                    .build();

            var recallRef = BreakerRef.of("embed-provider", config, OnOpen.FAIL);
            var dreamRef = BreakerRef.of("embed-provider", config, OnOpen.BYPASS);

            AtomicInteger providerCalls = new AtomicInteger(0);
            AtomicBoolean providerFails = new AtomicBoolean(false);

            SynapticRelay<Signal> embedRelay = s -> {
                providerCalls.incrementAndGet();
                if (providerFails.get()) {
                    throw new IOException("Ollama connection refused");
                }
                s.count += 1;
                return true;
            };

            var recallRelay = new CircuitBreakerRelay<>(embedRelay, recallRef, registry);
            var dreamRelay = new CircuitBreakerRelay<>(embedRelay, dreamRef, registry);

            // Both share the exact same breaker instance
            assertThat(recallRelay.circuitBreaker()).isSameAs(dreamRelay.circuitBreaker());

            var ctxRecall = DefaultPathwayContext.builder().build();
            var sigRecall = new Signal();
            sigRecall.bind(ctxRecall);

            var ctxDream = DefaultPathwayContext.builder().build();
            var sigDream = new Signal();
            sigDream.bind(ctxDream);

            // Initial calls succeed
            assertThat(recallRelay.transmit(sigRecall)).isTrue();
            assertThat(dreamRelay.transmit(sigDream)).isTrue();
            assertThat(providerCalls.get()).isEqualTo(2);

            // Now provider fails on next call
            providerFails.set(true);
            assertThatThrownBy(() -> recallRelay.transmit(sigRecall))
                    .isInstanceOf(IOException.class);

            // Breaker is now OPEN
            assertThat(recallRelay.state()).isEqualTo(CircuitBreakerRelay.State.OPEN);
            assertThat(dreamRelay.state()).isEqualTo(CircuitBreakerRelay.State.OPEN);

            // Recall call site (OnOpen.FAIL) fails fast with CircuitOpenException
            assertThatThrownBy(() -> recallRelay.transmit(sigRecall))
                    .isInstanceOf(CircuitOpenException.class)
                    .satisfies(e -> {
                        var coe = (CircuitOpenException) e;
                        assertThat(coe.breakerName()).isEqualTo("embed-provider");
                    });

            // Dream call site (OnOpen.BYPASS) gracefully bypasses without invoking provider
            int providerCallsBeforeDream = providerCalls.get();
            boolean dreamResult = dreamRelay.transmit(sigDream);
            assertThat(dreamResult).isTrue();
            assertThat(providerCalls.get()).isEqualTo(providerCallsBeforeDream); // Provider was NOT invoked!

            // Dream context records bypassed mark
            assertThat(ctxDream.outcome().bypassedMarks())
                    .isNotEmpty()
                    .anySatisfy(mark -> {
                        assertThat(mark.message()).isEqualTo("circuit_open:embed-provider");
                    });
        }
    }

    @Nested
    @DisplayName("Conductor INTERRUPTED and ABORT Handling")
    class ConductorResilienceTests {

        @Test
        @DisplayName("Conductor INTERRUPTED handling restores thread interrupt and fails fast")
        void conductorInterruptedRestoresFlagAndFailsFast() {
            var breaker = new CircuitBreaker("interrupted-breaker", CircuitBreakerConfig.builder().failureThreshold(1).build());
            var cbRelay = new CircuitBreakerRelay<>(
                    (SynapticRelay<Signal>) s -> { throw new InterruptedException("Worker canceled"); },
                    breaker,
                    OnOpen.FAIL
            );

            var pathway = CognitivePathway.<Signal>pathway("interrupted-pathway")
                    .relay("relay1", cbRelay, ErrorPolicy.DEGRADE_GRACEFULLY)
                    .relay("relay2", s -> { s.count += 99; return true; }, ErrorPolicy.DEGRADE_GRACEFULLY)
                    .build();

            var ctx = DefaultPathwayContext.builder().build();
            var signal = new Signal();
            signal.bind(ctx);

            assertThatThrownBy(() -> pathway.conduct(signal))
                    .isInstanceOf(CognitivePathwayException.class)
                    .satisfies(e -> {
                        var cpe = (CognitivePathwayException) e;
                        assertThat(cpe.kind()).isEqualTo(FaultKind.INTERRUPTED);
                    });

            // Thread interrupt flag was restored!
            assertThat(Thread.interrupted()).isTrue();

            // Next relay was not executed
            assertThat(signal.count).isEqualTo(0);

            // Breaker was NOT tripped by INTERRUPTED
            assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.CLOSED);
            assertThat(breaker.consecutiveFailures()).isEqualTo(0);
        }

        @Test
        @DisplayName("Conductor ABORT policy short-circuits gracefully without throwing")
        void conductorAbortPolicyShortCircuitsGracefully() {
            var pathway = CognitivePathway.<Signal>pathway("abort-pathway")
                    .relay("init", s -> { s.count += 1; return true; })
                    .relay("aborting", s -> { throw new IllegalStateException("Not in mood to dream"); }, ErrorPolicy.ABORT)
                    .relay("unreached", s -> { s.count += 100; return true; })
                    .build();

            var ctx = DefaultPathwayContext.builder().build();
            var signal = new Signal();
            signal.bind(ctx);
            ctx.scope().enter("abort-pathway");
            try {
                var result = pathway.conduct(signal);
                assertThat(result).isSameAs(signal);
                assertThat(signal.count).isEqualTo(1); // unreached did not run
                assertThat(ctx.scope().shortCircuited("abort-pathway")).isTrue();
            } finally {
                ctx.scope().leave("abort-pathway");
            }
            assertThat(ctx.scope().shortCircuited("abort-pathway")).isFalse();
        }
    }
}
