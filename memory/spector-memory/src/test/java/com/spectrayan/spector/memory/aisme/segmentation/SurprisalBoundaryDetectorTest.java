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
package com.spectrayan.spector.memory.aisme.segmentation;

import static org.assertj.core.api.Assertions.assertThat;

import com.spectrayan.spector.memory.aisme.segmentation.SurprisalBoundaryDetector.BoundaryEvaluation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

/**
 * Unit tests for {@link SurprisalBoundaryDetector}.
 */
class SurprisalBoundaryDetectorTest {

    private static final int DIMENSIONS = 8;
    private SurprisalBoundaryDetector detector;
    private float[] posteriorMean;
    private float[] obsPrecision;

    @BeforeEach
    void setUp() {
        posteriorMean = new float[DIMENSIONS];
        Arrays.fill(posteriorMean, 0.5f);
        obsPrecision = new float[DIMENSIONS];
        Arrays.fill(obsPrecision, 2.0f);

        BayesianOnlineChangePointDetector bocpd = new BayesianOnlineChangePointDetector(
                DIMENSIONS, 100.0f, 100, posteriorMean, obsPrecision
        );

        detector = new SurprisalBoundaryDetector(bocpd, 0.65f, 1.50f, 50);
    }

    @Test
    @DisplayName("evaluate: Predictable frame does not trigger boundary")
    void predictableFrame_noBoundary() {
        float[] obs = new float[DIMENSIONS];
        Arrays.fill(obs, 0.5f);

        BoundaryEvaluation eval = detector.evaluate(posteriorMean, obs, obsPrecision, 10);

        assertThat(eval.isBoundary()).isFalse();
        assertThat(eval.reason()).isNull();
        assertThat(eval.surprisal()).isZero();
    }

    @Test
    @DisplayName("evaluate: Surprisal shock triggers SURPRISAL_SPIKE boundary cut")
    void surprisalShock_triggersSurprisalSpikeBoundary() {
        float[] shockObs = new float[DIMENSIONS];
        Arrays.fill(shockObs, 3.5f); // High quadratic error

        BoundaryEvaluation eval = detector.evaluate(posteriorMean, shockObs, obsPrecision, 10);

        assertThat(eval.isBoundary()).isTrue();
        assertThat(eval.reason()).isEqualTo(BoundaryReason.SURPRISAL_SPIKE);
        assertThat(eval.surprisal()).isGreaterThanOrEqualTo(1.50f);
    }

    @Test
    @DisplayName("evaluate: Buffer capacity triggers MAX_DURATION_TIMEOUT boundary cut")
    void bufferCapacity_triggersTimeoutBoundary() {
        float[] obs = new float[DIMENSIONS];
        Arrays.fill(obs, 0.5f);

        BoundaryEvaluation eval = detector.evaluate(posteriorMean, obs, obsPrecision, 50);

        assertThat(eval.isBoundary()).isTrue();
        assertThat(eval.reason()).isEqualTo(BoundaryReason.MAX_DURATION_TIMEOUT);
    }
}
