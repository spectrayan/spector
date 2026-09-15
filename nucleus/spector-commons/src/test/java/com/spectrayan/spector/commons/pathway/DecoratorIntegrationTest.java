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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ADR-0036 §17 Resilience Decorator Matrix Integration Test")
class DecoratorIntegrationTest {

    static class Signal extends AbstractSignal {
        int processedCount = 0;
    }

    static class MockRemoteRelay implements SynapticRelay<Signal>, IdempotentRelay, InterruptibleRelay {
        final AtomicInteger attempts = new AtomicInteger(0);
        volatile boolean succeedEventually = false;
        volatile int succeedOnAttempt = Integer.MAX_VALUE;
        volatile boolean sleepLong = false;

        @Override
        public boolean transmit(Signal signal) throws Exception {
            int attempt = attempts.incrementAndGet();
            if (sleepLong) {
                Thread.sleep(500);
            }
            if (succeedEventually && attempt >= succeedOnAttempt) {
                signal.processedCount++;
                return true;
            }
            throw new IOException("Remote call failed at attempt " + attempt);
        }
    }

    @Test
    @DisplayName("Bulkhead rejection stops invocation before timeout or retry allocation")
    void bulkheadRejectionBeforeTimeoutAndRetry() {
        var composer = PathwayComposer.<Signal>of("test-pathway");
        var relay = new MockRemoteRelay();

        composer.stage("remote-stage")
                .relay(relay)
                .policy(ErrorPolicy.FAIL_FAST)
                .bulkhead(BulkheadConfig.of(1, Duration.ZERO, OnReject.FAIL))
                .timeout(Duration.ofMillis(100))
                .retry(RetryPolicy.of(3, Duration.ofMillis(5), FaultKind.TRANSIENT))
                .breaker(new BreakerRef("b1", CircuitBreakerConfig.builder().failureThreshold(5).build(), OnOpen.FAIL))
                .add();

        var pathway = composer.build();
        var registry = BulkheadRegistry.create();
        // Exhaust the 1 permit
        registry.get("remote-stage", BulkheadConfig.of(1)).acquireUninterruptibly();
        var ctx = DefaultPathwayContext.builder()
                .bind(BulkheadRegistry.class, registry)
                .build();

        var signal = new Signal();
        signal.bind(ctx);

        assertThatThrownBy(() -> pathway.conduct(signal))
                .isInstanceOf(CognitivePathwayException.class)
                .satisfies(e -> {
                    var cpe = (CognitivePathwayException) e;
                    assertThat(cpe.errorCode()).isEqualTo(ErrorCode.PATHWAY_BULKHEAD);
                });

        // The inner relay was NEVER invoked because bulkhead rejected at the outermost barrier!
        assertThat(relay.attempts.get()).isEqualTo(0);
    }

    @Test
    @DisplayName("Timeout is enforced per-attempt in the retry loop, and succeeds on eventual attempt")
    void timeoutPerAttemptWithRetrySuccess() {
        var composer = PathwayComposer.<Signal>of("test-pathway");
        var relay = new MockRemoteRelay();
        relay.succeedEventually = true;
        relay.succeedOnAttempt = 2; // fails attempt 1 with IOException, succeeds attempt 2

        composer.stage("remote-stage")
                .relay(relay)
                .policy(ErrorPolicy.FAIL_FAST)
                .timeout(Duration.ofMillis(200))
                .retry(RetryPolicy.of(3, Duration.ofMillis(5), 0.0, FaultKind.TRANSIENT))
                .add();

        var pathway = composer.build();
        var signal = new Signal();
        var ctx = DefaultPathwayContext.builder().build();
        signal.bind(ctx);

        pathway.conduct(signal);

        assertThat(signal.processedCount).isEqualTo(1);
        assertThat(relay.attempts.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("Circuit breaker sees only 1 post-retry failure when retries are exhausted")
    void breakerSeesPostRetryFailure() {
        var composer = PathwayComposer.<Signal>of("test-pathway");
        var relay = new MockRemoteRelay(); // always throws IOException

        var breakerConfig = CircuitBreakerConfig.builder()
                .failureThreshold(2) // trips after 2 post-retry failures
                .build();
        var breakerRef = new BreakerRef("post-retry-breaker", breakerConfig, OnOpen.FAIL);

        composer.stage("remote-stage")
                .relay(relay)
                .policy(ErrorPolicy.FAIL_FAST)
                .timeout(Duration.ofMillis(200))
                .retry(RetryPolicy.of(3, Duration.ofMillis(2), 0.0, FaultKind.TRANSIENT))
                .breaker(breakerRef)
                .add();

        var pathway = composer.build();
        var breakerRegistry = CircuitBreakerRegistry.create();
        var breaker = breakerRegistry.get(breakerRef);

        var ctx1 = DefaultPathwayContext.builder()
                .bind(CircuitBreakerRegistry.class, breakerRegistry)
                .build();
        var signal1 = new Signal();
        signal1.bind(ctx1);

        // First conduction exhausts 3 retries, but breaker counts it as 1 failure!
        assertThatThrownBy(() -> pathway.conduct(signal1))
                .isInstanceOf(CognitivePathwayException.class)
                .hasCauseInstanceOf(IOException.class);

        assertThat(relay.attempts.get()).isEqualTo(3);
        assertThat(breaker.consecutiveFailures()).isEqualTo(1);
        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.CLOSED);

        // Second conduction exhausts 3 more retries, and breaker trips OPEN (2nd failure)!
        var ctx2 = DefaultPathwayContext.builder()
                .bind(CircuitBreakerRegistry.class, breakerRegistry)
                .build();
        var signal2 = new Signal();
        signal2.bind(ctx2);

        assertThatThrownBy(() -> pathway.conduct(signal2))
                .isInstanceOf(CognitivePathwayException.class)
                .hasCauseInstanceOf(IOException.class);

        assertThat(relay.attempts.get()).isEqualTo(6);
        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.OPEN);

        // Third conduction is blocked immediately by OPEN circuit breaker without running relay or retries!
        var ctx3 = DefaultPathwayContext.builder()
                .bind(CircuitBreakerRegistry.class, breakerRegistry)
                .build();
        var signal3 = new Signal();
        signal3.bind(ctx3);

        assertThatThrownBy(() -> pathway.conduct(signal3))
                .isInstanceOf(CognitivePathwayException.class)
                .satisfies(e -> {
                    var cpe = (CognitivePathwayException) e;
                    assertThat(cpe.errorCode()).isEqualTo(ErrorCode.PATHWAY_CIRCUIT_OPEN);
                });

        // Relay attempt count did NOT increase — circuit breaker tripped before any retries or relay!
        assertThat(relay.attempts.get()).isEqualTo(6);
    }
}
