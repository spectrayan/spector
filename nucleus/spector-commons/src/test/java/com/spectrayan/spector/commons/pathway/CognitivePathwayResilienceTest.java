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

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CognitivePathway Resilience")
class CognitivePathwayResilienceTest {

    static class Signal extends AbstractSignal {
        final List<String> steps = new ArrayList<>();
    }

    @Test
    @DisplayName("ErrorPolicy.ABORT short-circuits execution without throwing")
    void abortPolicyStopsWithoutThrowing() {
        var engine = CognitivePathway.<Signal>pathway("abort-test")
                .relay("r1", s -> { s.steps.add("r1"); return true; }, ErrorPolicy.FAIL_FAST)
                .relay("r2-abort", s -> { throw new RuntimeException("abort me"); }, ErrorPolicy.ABORT)
                .relay("r3", s -> { s.steps.add("r3"); return true; }, ErrorPolicy.FAIL_FAST)
                .build();

        var ctx = DefaultPathwayContext.builder().build();
        var signal = new Signal();
        signal.bind(ctx);

        engine.conduct(signal);

        assertThat(signal.steps).containsExactly("r1");
    }

    @Test
    @DisplayName("INTERRUPTED restores interrupt status and throws regardless of error policy")
    void interruptedOverridesPolicyAndThrows() {
        var engine = CognitivePathway.<Signal>pathway("interrupt-test")
                .relay("r1", s -> { throw new InterruptedException("interrupted"); }, ErrorPolicy.DEGRADE_GRACEFULLY)
                .build();

        var ctx = DefaultPathwayContext.builder().build();
        var signal = new Signal();
        signal.bind(ctx);

        // Clear interrupt if set
        Thread.interrupted();

        assertThatThrownBy(() -> engine.conduct(signal))
                .isInstanceOf(CognitivePathwayException.class)
                .satisfies(e -> {
                    var cpe = (CognitivePathwayException) e;
                    assertThat(cpe.kind()).isEqualTo(FaultKind.INTERRUPTED);
                });

        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        // Clear interrupt flag after test
        Thread.interrupted();
    }

    @Test
    @DisplayName("DEGRADE_GRACEFULLY continues and records degraded outcome mark")
    void degradeGracefullyRecordsOutcome() {
        var engine = CognitivePathway.<Signal>pathway("degrade-test")
                .relay("r1-fail", s -> { throw new RuntimeException("non-fatal error"); }, ErrorPolicy.DEGRADE_GRACEFULLY)
                .relay("r2-success", s -> { s.steps.add("r2"); return true; }, ErrorPolicy.FAIL_FAST)
                .build();

        var ctx = DefaultPathwayContext.builder().build();
        var signal = new Signal();
        signal.bind(ctx);

        engine.conduct(signal);

        assertThat(signal.steps).containsExactly("r2");
        assertThat(ctx.outcome().degraded()).isTrue();
        assertThat(ctx.outcome().degradedMarks()).hasSize(1);
        assertThat(ctx.outcome().degradedMarks().get(0).scope()).isEqualTo("r1-fail");
        assertThat(ctx.outcome().degradedMarks().get(0).message()).contains("non-fatal error");
    }
}
