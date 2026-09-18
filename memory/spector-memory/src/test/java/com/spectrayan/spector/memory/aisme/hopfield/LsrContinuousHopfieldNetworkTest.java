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
package com.spectrayan.spector.memory.aisme.hopfield;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ContinuousHopfieldNetwork} with {@link KernelType#LSR}.
 */
class LsrContinuousHopfieldNetworkTest {

    @Test
    void retrieveAttractor_lsrKernel_singleStepExactSettlement() {
        ContinuousHopfieldNetwork lsrNetwork = new ContinuousHopfieldNetwork(KernelType.LSR, 5, 1e-4f);

        float[] target = {5.0f, 10.0f, 15.0f};
        float[] distractor = {-5.0f, -10.0f, -15.0f};
        float[][] patterns = {target, distractor};

        // Query slightly perturbed from target
        float[] query = {5.05f, 10.05f, 15.05f};

        // beta = 2.0 -> r_c^2 = 1.0. Perturbation dist^2 = 0.05^2 * 3 = 0.0075 < 1.0
        AttractorState state = lsrNetwork.retrieveAttractor(query, patterns, 2.0f);

        assertThat(state.type()).isEqualTo(AttractorType.FIXED_POINT);
        assertThat(state.attentionWeights()[0]).isCloseTo(1.0f, within(1e-5f));
        assertThat(state.attentionWeights()[1]).isCloseTo(0.0f, within(1e-5f));
        assertThat(state.iterations()).isEqualTo(1); // Settled in 1 exact step!

        assertThat(state.attractorVector()[0]).isCloseTo(5.0f, within(1e-5f));
        assertThat(state.attractorVector()[1]).isCloseTo(10.0f, within(1e-5f));
        assertThat(state.attractorVector()[2]).isCloseTo(15.0f, within(1e-5f));
    }

    @Test
    void retrieveAttractor_lseKernel_iterativeConvergence() {
        ContinuousHopfieldNetwork lseNetwork = new ContinuousHopfieldNetwork(KernelType.LSE, 5, 1e-4f);

        float[] target = {1.0f, 0.0f};
        float[] other = {0.0f, 1.0f};
        float[][] patterns = {target, other};

        float[] query = {0.9f, 0.1f};

        AttractorState state = lseNetwork.retrieveAttractor(query, patterns, 5.0f);
        assertThat(state.type()).isEqualTo(AttractorType.FIXED_POINT);
        assertThat(state.attractorVector()[0]).isGreaterThan(0.95f);
    }
}
