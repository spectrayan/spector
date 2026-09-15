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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AbstractPathway")
class AbstractPathwayTest {

    static class Signal extends AbstractSignal {
        int value = 0;
    }

    static class TestPathway extends AbstractPathway<Signal, Integer> {
        TestPathway(CognitivePathway<Signal> engine) {
            super("test-pathway", Signal.class, Integer.class, engine);
        }

        @Override
        protected Integer project(Signal signal) {
            return signal.value;
        }
    }

    @Test
    @DisplayName("Throws CognitivePathwayException with CONTRACT when conducted without context")
    void throwsWithoutContext() {
        var pathway = new TestPathway(CognitivePathway.<Signal>pathway("test").build());
        var signal = new Signal();

        assertThatThrownBy(() -> pathway.conduct(signal))
                .isInstanceOf(CognitivePathwayException.class)
                .satisfies(e -> {
                    var cpe = (CognitivePathwayException) e;
                    assertThat(cpe.kind()).isEqualTo(FaultKind.CONTRACT);
                });
    }

    @Test
    @DisplayName("Conducts successfully and projects output")
    void conductAndProject() {
        var engine = CognitivePathway.<Signal>pathway("test-pathway")
                .relay("add10", s -> { s.value += 10; return true; })
                .build();
        var pathway = new TestPathway(engine);

        var ctx = DefaultPathwayContext.builder().build();
        var signal = new Signal();
        signal.bind(ctx);

        int result = pathway.conduct(signal);
        assertThat(result).isEqualTo(10);
        assertThat(ctx.outcome().finish()).isEqualTo(ConductionOutcome.Finish.COMPLETED);
    }

    @Test
    @DisplayName("Sets SHORT_CIRCUITED finish when engine short-circuits")
    void shortCircuitFinish() {
        var engine = CognitivePathway.<Signal>pathway("test-pathway")
                .relay("stop", s -> false)
                .relay("never", s -> { s.value += 100; return true; })
                .build();
        var pathway = new TestPathway(engine);

        var ctx = DefaultPathwayContext.builder().build();
        var signal = new Signal();
        signal.bind(ctx);

        int result = pathway.conduct(signal);
        assertThat(result).isEqualTo(0);
        assertThat(ctx.outcome().finish()).isEqualTo(ConductionOutcome.Finish.SHORT_CIRCUITED);
    }

    @Test
    @DisplayName("Sets FAILED finish when engine throws")
    void failureFinish() {
        var engine = CognitivePathway.<Signal>pathway("test-pathway")
                .relay("fail", s -> { throw new RuntimeException("boom"); })
                .build();
        var pathway = new TestPathway(engine);

        var ctx = DefaultPathwayContext.builder().build();
        var signal = new Signal();
        signal.bind(ctx);

        assertThatThrownBy(() -> pathway.conduct(signal))
                .isInstanceOf(CognitivePathwayException.class);

        assertThat(ctx.outcome().finish()).isEqualTo(ConductionOutcome.Finish.FAILED);
    }
}
