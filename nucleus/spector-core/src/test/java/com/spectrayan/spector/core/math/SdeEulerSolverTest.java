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
package com.spectrayan.spector.core.math;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.FloatRange;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit and property-based tests for {@link SdeEulerSolver}.
 */
class SdeEulerSolverTest {

    @Test
    void nullInputsDoNotThrow() {
        SdeEulerSolver.stepEulerMaruyama(null, null, null, null, null, null, null, 0.1f, null, null);
        float[] state = {0.1f};
        SdeEulerSolver.stepEulerMaruyama(state, null, null, null, null, null, null, 0.1f, null, null);
    }

    @Test
    void pureDecayIntegrationMatchesAnalyticalStep() {
        // h_t = [0.5, -0.5], A = diag(-1.0, -1.0), dt = 0.1
        // dh = A * h * dt = [-0.5 * 0.1, 0.5 * 0.1] = [-0.05, 0.05]
        // h_next = [0.45, -0.45]
        float[] state = {0.5f, -0.5f};
        float[][] aMatrix = {{-1.0f, 0.0f}, {0.0f, -1.0f}};
        float[] out = new float[2];

        SdeEulerSolver.stepEulerMaruyama(state, aMatrix, null, null, null, null, null, 0.1f, null, out);

        assertThat(out[0]).isCloseTo(0.45f, within(1e-6f));
        assertThat(out[1]).isCloseTo(-0.45f, within(1e-6f));
    }

    @Test
    void fullCouplingWithExternalInputRecallAndNoise() {
        float[] state = {0.0f};
        float[][] aMatrix = {{0.0f}};
        float[][] bMatrix = {{2.0f}};
        float[] uInput = {0.5f}; // B*u = 1.0
        float[][] cMatrix = {{0.5f}};
        float[] rRecall = {0.4f}; // C*r = 0.2
        float[] sigma = {0.1f};
        float[] noise = {3.0f}; // sigma*noise = 0.3
        float dt = 0.5f;
        // drift = 0 + 1.0 + 0.2 + 0.3 = 1.5
        // rawNext = 0.0 + 0.5 * 1.5 = 0.75
        float[] out = new float[1];

        SdeEulerSolver.stepEulerMaruyama(state, aMatrix, bMatrix, uInput, cMatrix, rRecall, sigma, dt, noise, out);

        assertThat(out[0]).isCloseTo(0.75f, within(1e-6f));
    }

    @Test
    void saturationClampedToUnitInterval() {
        float[] state = {0.9f, -0.9f};
        float[][] aMatrix = {{10.0f, 0.0f}, {0.0f, 10.0f}};
        float dt = 1.0f;
        float[] out = new float[2];

        SdeEulerSolver.stepEulerMaruyama(state, aMatrix, null, null, null, null, null, dt, null, out);

        assertThat(out[0]).isEqualTo(1.0f);
        assertThat(out[1]).isEqualTo(-1.0f);
    }

    @Property(tries = 100)
    void stateAlwaysStaysClamped(
            @ForAll @FloatRange(min = -10.0f, max = 10.0f) float s1,
            @ForAll @FloatRange(min = -10.0f, max = 10.0f) float a11,
            @ForAll @FloatRange(min = 0.01f, max = 2.0f) float dt,
            @ForAll @FloatRange(min = -5.0f, max = 5.0f) float noise
    ) {
        float[] state = {s1};
        float[][] a = {{a11}};
        float[] sigma = {0.5f};
        float[] z = {noise};
        float[] out = new float[1];

        SdeEulerSolver.stepEulerMaruyama(state, a, null, null, null, null, sigma, dt, z, out);

        assertThat(out[0]).isBetween(-1.0f, 1.0f);
    }
}
