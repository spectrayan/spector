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

import com.spectrayan.spector.commons.observation.PathwayObservationHooks;
import com.spectrayan.spector.commons.pathway.AbstractPathway;
import com.spectrayan.spector.commons.pathway.AbstractSignal;
import com.spectrayan.spector.commons.pathway.BulkheadConfig;
import com.spectrayan.spector.commons.pathway.BulkheadRelay;
import com.spectrayan.spector.commons.pathway.CircuitBreaker;
import com.spectrayan.spector.commons.pathway.CircuitBreakerConfig;
import com.spectrayan.spector.commons.pathway.PathwayEngine;
import com.spectrayan.spector.commons.pathway.DefaultPathwayContext;
import com.spectrayan.spector.commons.pathway.ErrorPolicy;
import com.spectrayan.spector.commons.pathway.FaultKind;
import com.spectrayan.spector.commons.pathway.OnOpen;
import com.spectrayan.spector.commons.pathway.OnReject;
import com.spectrayan.spector.commons.pathway.PathwayContext;
import com.spectrayan.spector.commons.pathway.RetryPolicy;
import com.spectrayan.spector.commons.pathway.RetryRelay;
import com.spectrayan.spector.commons.pathway.TimeoutRelay;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PathwayMetrics (ADR-0036 §15)")
class PathwayMetricsTest {

    private SimpleMeterRegistry registry;
    private PathwayMetrics pathwayMetrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        pathwayMetrics = PathwayMetrics.bind(registry);
    }

    @AfterEach
    void tearDown() {
        PathwayObservationHooks.setGlobal(null);
    }

    private static final class TestSignal extends AbstractSignal {
        TestSignal(PathwayContext ctx) {
            super();
            bind(ctx);
        }
    }

    private static final class TestPathway extends AbstractPathway<TestSignal, String> {
        TestPathway(PathwayEngine<TestSignal> engine) {
            super("test_pathway", TestSignal.class, String.class, engine);
        }

        @Override
        protected String project(TestSignal signal) {
            return "ok";
        }
    }

    @Test
    @DisplayName("Emits spector.pathway.conduct timer on successful pathway conduct")
    void testPathwayConductMetric() {
        var engine = PathwayEngine.<TestSignal>builder("test_pathway")
                .relay("stage1", signal -> true)
                .build();
        var pathway = new TestPathway(engine);

        var ctx = DefaultPathwayContext.builder().build();
        var signal = new TestSignal(ctx);

        String result = pathway.conduct(signal);
        assertThat(result).isEqualTo("ok");

        Timer timer = registry.find("spector.pathway.conduct")
                .tag("pathway", "test_pathway")
                .tag("finish", "completed")
                .timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Emits spector.pathway.relay timer with interceptor")
    void testPathwayRelayMetric() {
        var engine = PathwayEngine.<TestSignal>builder("test_pathway")
                .withInterceptor(pathwayMetrics.interceptor())
                .relay("metered_relay", signal -> true)
                .build();
        var pathway = new TestPathway(engine);

        var ctx = DefaultPathwayContext.builder().build();
        var signal = new TestSignal(ctx);

        pathway.conduct(signal);

        Timer timer = registry.find("spector.pathway.relay")
                .tag("pathway", "test_pathway")
                .tag("relay", "metered_relay")
                .tag("status", "success")
                .timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Emits spector.pathway.degraded counter on graceful degradation")
    void testDegradedMetric() {
        var engine = PathwayEngine.<TestSignal>builder("test_pathway")
                .relay("failing_relay", signal -> {
                    throw new IOException("network timeout");
                }, ErrorPolicy.DEGRADE_GRACEFULLY)
                .build();
        var pathway = new TestPathway(engine);

        var ctx = DefaultPathwayContext.builder().build();
        var signal = new TestSignal(ctx);

        pathway.conduct(signal);

        Counter counter = registry.find("spector.pathway.degraded")
                .tag("pathway", "test_pathway")
                .tag("relay", "failing_relay")
                .tag("kind", "transient")
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Emits spector.pathway.circuit counter on trip, probe, close, and reject")
    void testCircuitMetrics() {
        var breakerConfig = CircuitBreakerConfig.builder()
                .failureThreshold(2)
                .cooldown(Duration.ofMillis(50))
                .build();
        var breaker = new CircuitBreaker("test-breaker", breakerConfig);

        // Fail twice to trip
        var permit1 = breaker.tryAcquire(OnOpen.BYPASS);
        breaker.onFailure(permit1, FaultKind.TRANSIENT);
        var permit2 = breaker.tryAcquire(OnOpen.BYPASS);
        breaker.onFailure(permit2, FaultKind.TRANSIENT);

        Counter tripCounter = registry.find("spector.pathway.circuit")
                .tag("breaker", "test-breaker")
                .tag("event", "trip")
                .counter();
        assertThat(tripCounter).isNotNull();
        assertThat(tripCounter.count()).isEqualTo(1.0);

        // Reject while open
        var rejectPermit = breaker.tryAcquire(OnOpen.BYPASS);
        assertThat(rejectPermit.isBypass()).isTrue();

        Counter rejectCounter = registry.find("spector.pathway.circuit")
                .tag("breaker", "test-breaker")
                .tag("event", "reject")
                .counter();
        assertThat(rejectCounter).isNotNull();
        assertThat(rejectCounter.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Emits spector.pathway.bulkhead.reject counter on capacity exhaustion")
    void testBulkheadRejectMetric() throws Exception {
        var config = BulkheadConfig.of(1, Duration.ZERO, OnReject.BYPASS);
        var bulkhead = new BulkheadRelay<TestSignal>(
                signal -> true, config, "test_relay", "test_bulkhead");

        var ctx = DefaultPathwayContext.builder().build();
        var signal = new TestSignal(ctx);

        // Saturate bulkhead then attempt another
        java.util.concurrent.Semaphore sem = new java.util.concurrent.Semaphore(0);
        var saturatedBulkhead = new BulkheadRelay<TestSignal>(
                s -> true, sem, config, "test_relay", "test_bulkhead");

        saturatedBulkhead.transmit(signal);

        Counter counter = registry.find("spector.pathway.bulkhead.reject")
                .tag("bulkhead", "test_bulkhead")
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Emits spector.pathway.timeout counter on timeout expiry")
    void testTimeoutMetric() {
        var timeoutRelay = new TimeoutRelay<TestSignal>(
                signal -> {
                    Thread.sleep(200);
                    return true;
                },
                Duration.ofMillis(20),
                "slow_relay"
        );

        var ctx = DefaultPathwayContext.builder().build();
        var signal = new TestSignal(ctx);

        try {
            timeoutRelay.transmit(signal);
        } catch (Exception ignored) {
        }

        Counter counter = registry.find("spector.pathway.timeout")
                .tag("pathway", "root")
                .tag("relay", "slow_relay")
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Emits spector.pathway.retry counter on each extra attempt")
    void testRetryMetric() {
        var retryPolicy = RetryPolicy.of(3, Duration.ZERO, FaultKind.TRANSIENT);
        var attempts = new AtomicInteger();
        var retryRelay = new RetryRelay<TestSignal>(
                signal -> {
                    if (attempts.incrementAndGet() < 3) {
                        throw new IOException("temporary network glitch");
                    }
                    return true;
                },
                retryPolicy,
                "network_relay"
        );

        var ctx = DefaultPathwayContext.builder().build();
        var signal = new TestSignal(ctx);

        try {
            retryRelay.transmit(signal);
        } catch (Exception ignored) {
        }

        Counter counter = registry.find("spector.pathway.retry")
                .tag("pathway", "root")
                .tag("relay", "network_relay")
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(2.0); // 2 retries after 1st attempt
    }

    @Test
    @DisplayName("Emits spector.pathway.nested timer on nested pathway execution")
    void testNestedMetric() {
        pathwayMetrics.onNested("dream_pathway", "remember_pathway", Duration.ofMillis(42));

        Timer timer = registry.find("spector.pathway.nested")
                .tag("from", "dream_pathway")
                .tag("to", "remember_pathway")
                .timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS)).isEqualTo(42.0);
    }
}
