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

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ConductionScope")
class ConductionScopeTest {

    @Test
    @DisplayName("Pushes and pops frames matching pathway lifecycle")
    void pushAndPopFrames() {
        var scope = new ConductionScope();
        assertThat(scope.pathwayName()).isEqualTo("root");
        assertThat(scope.depth()).isEqualTo(0);

        scope.enter("recall");
        assertThat(scope.pathwayName()).isEqualTo("recall");
        assertThat(scope.depth()).isEqualTo(1);
        assertThat(scope.segment()).isEqualTo("recall");

        scope.pushSegment("nested_filter");
        scope.enter("filter");
        assertThat(scope.pathwayName()).isEqualTo("filter");
        assertThat(scope.depth()).isEqualTo(2);
        assertThat(scope.segment()).isEqualTo("recall/nested_filter");

        scope.leave("filter");
        assertThat(scope.pathwayName()).isEqualTo("recall");
        assertThat(scope.depth()).isEqualTo(1);

        scope.leave("recall");
        assertThat(scope.pathwayName()).isEqualTo("root");
        assertThat(scope.depth()).isEqualTo(0);
    }

    @Test
    @DisplayName("Asserts thread confinement when accessed from another thread")
    void assertsThreadConfinement() throws Exception {
        var scope = new ConductionScope();
        scope.enter("recall");

        try (var executor = Executors.newSingleThreadExecutor()) {
            var future = executor.submit(() -> {
                scope.pathwayName();
                return null;
            });

            assertThatThrownBy(future::get)
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("ConductionScope is thread-confined");
        }
    }

    @Test
    @DisplayName("Detects recursion cycles and throws CognitivePathwayException with PATHWAY_CYCLE")
    void detectsRecursionCycle() {
        var scope = new ConductionScope();
        scope.enter("remember");
        scope.enter("dream");

        assertThatThrownBy(() -> scope.assertNotOnStack("remember"))
                .isInstanceOf(CognitivePathwayException.class)
                .satisfies(e -> {
                    var cpe = (CognitivePathwayException) e;
                    assertThat(cpe.errorCode()).isEqualTo(ErrorCode.PATHWAY_CYCLE);
                    assertThat(cpe.kind()).isEqualTo(FaultKind.CONTRACT);
                    assertThat(cpe.pathwayName()).isEqualTo("remember");
                });
    }

    @Test
    @DisplayName("Leave fails on stack mismatch or empty stack")
    void leaveFailsOnMismatch() {
        var scope = new ConductionScope();
        assertThatThrownBy(() -> scope.leave("recall"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("stack is empty");

        scope.enter("recall");
        assertThatThrownBy(() -> scope.leave("remember"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Stack mismatch");
    }

    @Test
    @DisplayName("Tracks short-circuited frames")
    void tracksShortCircuitedFrames() {
        var scope = new ConductionScope();
        scope.enter("remember");
        assertThat(scope.shortCircuited("remember")).isFalse();

        scope.markShortCircuited("remember");
        assertThat(scope.shortCircuited("remember")).isTrue();
        scope.leave("remember");

        assertThat(scope.shortCircuited("remember")).isFalse();
    }
}
