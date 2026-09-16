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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PathwayComposer.StageBuilder")
class StageBuilderTest {

    static class Signal extends AbstractSignal {
        int invocations = 0;
    }

    static class PlainRelay implements SynapticRelay<Signal> {
        @Override
        public boolean transmit(Signal signal) {
            signal.invocations++;
            return true;
        }
    }

    static class InterruptibleOnlyRelay implements SynapticRelay<Signal>, InterruptibleRelay {
        @Override
        public boolean transmit(Signal signal) {
            signal.invocations++;
            return true;
        }
    }

    static class IdempotentOnlyRelay implements SynapticRelay<Signal>, IdempotentRelay {
        @Override
        public boolean transmit(Signal signal) {
            signal.invocations++;
            return true;
        }
    }

    static class FullySafeRelay implements SynapticRelay<Signal>, IdempotentRelay, InterruptibleRelay {
        @Override
        public boolean transmit(Signal signal) {
            signal.invocations++;
            return true;
        }
    }

    @Test
    @DisplayName("Build-time rejection of .timeout() on non-InterruptibleRelay")
    void rejectsTimeoutOnNonInterruptible() {
        var composer = PathwayComposer.<Signal>of("test-pathway");
        var stageBuilder = composer.stage("test-stage")
                .relay(new PlainRelay())
                .timeout(Duration.ofMillis(100));

        assertThatThrownBy(stageBuilder::add)
                .isInstanceOf(CognitivePathwayException.class)
                .hasMessageContaining("does not implement InterruptibleRelay");
    }

    @Test
    @DisplayName("Build-time rejection of .retry() on non-IdempotentRelay")
    void rejectsRetryOnNonIdempotent() {
        var composer = PathwayComposer.<Signal>of("test-pathway");
        var stageBuilder = composer.stage("test-stage")
                .relay(new PlainRelay())
                .retry(RetryPolicy.of(3, Duration.ofMillis(10), FaultKind.TRANSIENT));

        assertThatThrownBy(stageBuilder::add)
                .isInstanceOf(CognitivePathwayException.class)
                .hasMessageContaining("does not implement IdempotentRelay");
    }

    @Test
    @DisplayName("Allows .timeout() on InterruptibleRelay")
    void allowsTimeoutOnInterruptible() {
        var composer = PathwayComposer.<Signal>of("test-pathway");
        composer.stage("test-stage")
                .relay(new InterruptibleOnlyRelay())
                .timeout(Duration.ofMillis(100))
                .add();

        var pathway = composer.build();
        assertThat(pathway).isNotNull();
    }

    @Test
    @DisplayName("Allows .retry() on IdempotentRelay")
    void allowsRetryOnIdempotent() {
        var composer = PathwayComposer.<Signal>of("test-pathway");
        composer.stage("test-stage")
                .relay(new IdempotentOnlyRelay())
                .retry(RetryPolicy.of(3, Duration.ofMillis(10), FaultKind.TRANSIENT))
                .add();

        var pathway = composer.build();
        assertThat(pathway).isNotNull();
    }

    @Test
    @DisplayName("Builds full decorator stack on fully safe relay")
    void buildsFullStackAndExecutes() throws Exception {
        var composer = PathwayComposer.<Signal>of("test-pathway");
        var breakerRef = new BreakerRef("test-breaker", CircuitBreakerConfig.builder().failureThreshold(2).build(), OnOpen.FAIL);

        composer.stage("safe-stage")
                .relay(new FullySafeRelay())
                .policy(ErrorPolicy.FAIL_FAST)
                .bulkhead(BulkheadConfig.of(5))
                .timeout(Duration.ofMillis(500))
                .retry(RetryPolicy.of(2, Duration.ofMillis(5), FaultKind.TRANSIENT))
                .breaker(breakerRef)
                .add();

        var pathway = composer.build();
        var signal = new Signal();
        var ctx = DefaultPathwayContext.builder().build();
        signal.bind(ctx);

        Signal processed = pathway.conduct(signal);
        assertThat(processed).isSameAs(signal);
        assertThat(signal.invocations).isEqualTo(1);
    }
}
