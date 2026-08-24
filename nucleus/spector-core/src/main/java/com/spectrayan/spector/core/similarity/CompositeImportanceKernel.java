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
package com.spectrayan.spector.core.similarity;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;

import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

/**
 * SIMD-accelerated mathematical kernel evaluating multi-dimensional composite episodic importance \(I(o_t)\).
 *
 * <h3>Biological Analog: Multi-Limbic Salience Synthesis</h3>
 * <p>Synthesizes epistemic surprise, affective resonance, prospective goal relevance, social context,
 * and environmental novelty into a calibrated scalar priority metric \(I(o_t) \in [0.0, 1.0]\).</p>
 */
public final class CompositeImportanceKernel {

    public static final int SIGNAL_DIMENSIONS = 5;
    private static final VectorSpecies<Float> SPECIES = FloatVector.SPECIES_PREFERRED;

    private CompositeImportanceKernel() {
        // Utility class
    }

    /**
     * Evaluates composite importance score \(I(o_t) = \text{clamp}\left(\sum_{i=1}^5 w_i \cdot s_i, 0.0, 1.0\right)\)
     * using hardware-accelerated SIMD instructions.
     *
     * @param signals 5-dimensional normalized signal vector \(\boldsymbol{s} \in [0.0, 1.0]^5\)
     * @param weights 5-dimensional normalized weight vector \(\boldsymbol{w} \ge 0, \sum w_i = 1.0\)
     * @return clamped scalar importance score in \([0.0, 1.0]\)
     */
    public static float evaluate(float[] signals, float[] weights) {
        if (signals == null || weights == null) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "signals and weights must not be null");
        }
        if (signals.length < SIGNAL_DIMENSIONS || weights.length < SIGNAL_DIMENSIONS) {
            throw new SpectorValidationException(
                    ErrorCode.ARGUMENT_INVALID,
                    "signals and weights must have length at least " + SIGNAL_DIMENSIONS
            );
        }

        int upperBound = SPECIES.loopBound(SIGNAL_DIMENSIONS);
        FloatVector acc = FloatVector.zero(SPECIES);
        int i = 0;

        for (; i < upperBound; i += SPECIES.length()) {
            FloatVector vSig = FloatVector.fromArray(SPECIES, signals, i);
            FloatVector vWeight = FloatVector.fromArray(SPECIES, weights, i);
            acc = vSig.fma(vWeight, acc);
        }

        float dot = acc.reduceLanes(VectorOperators.ADD);

        // Tail elements
        for (; i < SIGNAL_DIMENSIONS; i++) {
            dot += signals[i] * weights[i];
        }

        return Math.max(0.0f, Math.min(1.0f, dot));
    }

    /**
     * Scalar reference implementation for testing and fallback.
     *
     * @param signals 5-dimensional signal vector
     * @param weights 5-dimensional weight vector
     * @return scalar importance score in [0.0, 1.0]
     */
    public static float evaluateScalar(float[] signals, float[] weights) {
        if (signals == null || weights == null) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "signals and weights must not be null");
        }
        if (signals.length < SIGNAL_DIMENSIONS || weights.length < SIGNAL_DIMENSIONS) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Array length must be at least " + SIGNAL_DIMENSIONS);
        }

        float sum = 0.0f;
        for (int i = 0; i < SIGNAL_DIMENSIONS; i++) {
            sum += signals[i] * weights[i];
        }
        return Math.max(0.0f, Math.min(1.0f, sum));
    }

    /**
     * Normalizes a 5-dimensional weight vector so that its components sum to 1.0.
     *
     * @param rawWeights raw component weights
     * @return normalized weight vector
     */
    public static float[] normalizeWeights(float[] rawWeights) {
        if (rawWeights == null || rawWeights.length < SIGNAL_DIMENSIONS) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "rawWeights must have length at least " + SIGNAL_DIMENSIONS);
        }

        float sum = 0.0f;
        for (int i = 0; i < SIGNAL_DIMENSIONS; i++) {
            if (rawWeights[i] < 0.0f || Float.isNaN(rawWeights[i])) {
                throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "weights must be non-negative");
            }
            sum += rawWeights[i];
        }

        if (sum <= 0.0f) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "sum of weights must be positive");
        }

        float[] norm = new float[SIGNAL_DIMENSIONS];
        for (int i = 0; i < SIGNAL_DIMENSIONS; i++) {
            norm[i] = rawWeights[i] / sum;
        }
        return norm;
    }
}
