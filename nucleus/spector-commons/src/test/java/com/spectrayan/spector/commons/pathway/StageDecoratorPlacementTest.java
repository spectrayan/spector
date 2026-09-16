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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies decorator placement and the strict/optional safety-gate split
 * introduced when wiring ADR-0036 §5.5 into production recipes.
 */
@DisplayName("Stage decorator placement and safety gates")
class StageDecoratorPlacementTest {

    /** Minimal contextual signal so gated stages can record bypass traces. */
    private static final class Signal extends AbstractSignal {
    }

    private static PathwayContext ctx() {
        return DefaultPathwayContext.builder()
                .namespaceId("test")
                .catalog(new DefaultPathwayCatalog())
                .build();
    }

    /** Interruptible + idempotent — the shape of a remote HTTP relay. */
    private static final class RemoteRelay
            implements SynapticRelay<Signal>, InterruptibleRelay, IdempotentRelay {
        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public boolean transmit(final Signal signal) {
            calls.incrementAndGet();
            return true;
        }
    }

    /** Neither marker — the shape of CorticalWriteTransactionRelay. */
    private static final class WriteRelay implements SynapticRelay<Signal> {
        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public boolean transmit(final Signal signal) {
            calls.incrementAndGet();
            return true;
        }
    }

    @Nested
    @DisplayName("Strict gates (statically known relays)")
    class StrictGates {

        @Test
        @DisplayName("timeout() on a non-interruptible relay fails at BUILD time")
        void timeoutOnWriteRejected() {
            assertThatThrownBy(() -> PathwayComposer.<Signal>of("remember")
                    .stage("cortical_write")
                    .relay(new WriteRelay())
                    .timeout(Duration.ofMillis(200))
                    .add())
                    .isInstanceOf(CognitivePathwayException.class)
                    .hasMessageContaining("does not implement InterruptibleRelay");
        }

        @Test
        @DisplayName("retry() on a non-idempotent relay fails at BUILD time")
        void retryOnWriteRejected() {
            assertThatThrownBy(() -> PathwayComposer.<Signal>of("remember")
                    .stage("cortical_write")
                    .relay(new WriteRelay())
                    .retry(RetryPolicy.of(3, Duration.ofMillis(1)))
                    .add())
                    .isInstanceOf(CognitivePathwayException.class)
                    .hasMessageContaining("does not implement IdempotentRelay");
        }
    }

    @Nested
    @DisplayName("Optional gates (injected relays / test doubles)")
    class OptionalGates {

        @Test
        @DisplayName("timeoutIfInterruptible() SKIPS the budget instead of failing")
        void timeoutSkippedForNonInterruptible() {
            final var write = new WriteRelay();
            assertThatCode(() -> {
                var engine = PathwayComposer.<Signal>of("remember")
                        .stage("injected")
                        .relay(write)
                        .timeoutIfInterruptible(Duration.ofMillis(200))
                        .retryIfIdempotent(RetryPolicy.of(3, Duration.ofMillis(1)))
                        .add()
                        .build();
                var s = new Signal();
                s.bind(ctx());
                engine.conduct(s);
            }).doesNotThrowAnyException();
            assertThat(write.calls).hasValue(1);
        }

        @Test
        @DisplayName("timeoutIfInterruptible() STILL applies when the relay opts in")
        void timeoutAppliedForInterruptible() {
            final var remote = new RemoteRelay();
            var engine = PathwayComposer.<Signal>of("recall")
                    .stage("transduction")
                    .relay(remote)
                    .timeoutIfInterruptible(Duration.ofSeconds(2))
                    .add()
                    .build();
            var s = new Signal();
            s.bind(ctx());
            engine.conduct(s);
            assertThat(remote.calls).hasValue(1);
        }
    }

    @Nested
    @DisplayName("Gate placement (ADR-0036 §6)")
    class GatePlacement {

        @Test
        @DisplayName("A closed gate does not invoke the relay and does not consume decorators")
        void closedGateSkipsDecoratedRelay() {
            final var remote = new RemoteRelay();
            var engine = PathwayComposer.<Signal>of("recall")
                    .stage("rerank")
                    .relay(remote)
                    .gate(sig -> false)
                    .timeoutIfInterruptible(Duration.ofMillis(80))
                    .breaker(BreakerRef.of("rerank-remote", CircuitBreakerConfig.remote(), OnOpen.BYPASS))
                    .bulkhead("rerank-remote", BulkheadConfig.of(1))
                    .add()
                    .build();

            var s = new Signal();
            s.bind(ctx());
            engine.conduct(s);

            // Gate is outermost, so the relay never ran and no permit was taken.
            assertThat(remote.calls).hasValue(0);
        }

        @Test
        @DisplayName("An open gate runs the fully decorated relay")
        void openGateRunsDecoratedRelay() {
            final var remote = new RemoteRelay();
            var engine = PathwayComposer.<Signal>of("recall")
                    .stage("rerank")
                    .relay(remote)
                    .gate(sig -> true)
                    .timeoutIfInterruptible(Duration.ofSeconds(1))
                    .add()
                    .build();

            var s = new Signal();
            s.bind(ctx());
            engine.conduct(s);

            assertThat(remote.calls).hasValue(1);
        }
    }
}
