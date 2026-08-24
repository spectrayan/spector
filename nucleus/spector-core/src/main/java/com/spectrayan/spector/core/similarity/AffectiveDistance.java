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
import com.spectrayan.spector.core.simd.SimdCapability;

import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

/**
 * SIMD-accelerated affective resonance kernel.
 *
 * <p>Biological analog: Amygdala emotional resonance.
 * Evaluates the similarity between affective states using a Gaussian radial basis function kernel.</p>
 *
 * <h3>Mathematical Definition</h3>
 * <pre>
 *   k(a, b) = exp(-||a - b||² / (2σ²))
 * </pre>
 *
 * <p>A single pass computes the squared difference sum using SIMD, followed by the Gaussian exponentiation.</p>
 */
public final class AffectiveDistance {

    private static final VectorSpecies<Float> SPECIES = SimdCapability.PREFERRED_SPECIES;

    private AffectiveDistance() {
        // utility class
    }

    /**
     * Computes the affective resonance between two state vectors.
     *
     * @param stateA first state vector
     * @param stateB second state vector
     * @param sigma  bandwidth parameter for the Gaussian kernel
     * @return resonance score in (0, 1]
     */
    public static float compute(float[] stateA, float[] stateB, float sigma) {
        if (stateA.length != stateB.length) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Arrays must have same length");
        }
        return compute(stateA, 0, stateB, 0, stateA.length, sigma);
    }

    /**
     * Computes the affective resonance between two state vector slices.
     *
     * @param stateA  first state array
     * @param aOffset offset into {@code stateA}
     * @param stateB  second state array
     * @param bOffset offset into {@code stateB}
     * @param length  number of elements to process
     * @param sigma   bandwidth parameter for the Gaussian kernel
     * @return resonance score in (0, 1]
     */
    public static float compute(float[] stateA, int aOffset, float[] stateB, int bOffset, int length, float sigma) {
        VectorOps.validateSliceInputs(stateA, aOffset, stateB, bOffset, length);

        int laneCount = SPECIES.length();
        FloatVector sumSqDiff = FloatVector.zero(SPECIES);

        // ── Main vectorized loop ──
        int i = 0;
        int limit = SPECIES.loopBound(length);
        for (; i < limit; i += laneCount) {
            FloatVector va = FloatVector.fromArray(SPECIES, stateA, aOffset + i);
            FloatVector vb = FloatVector.fromArray(SPECIES, stateB, bOffset + i);
            FloatVector diff = va.sub(vb);
            
            sumSqDiff = sumSqDiff.add(diff.mul(diff));
        }

        // ── Tail: masked operations ──
        if (i < length) {
            VectorMask<Float> mask = SPECIES.indexInRange(i, length);
            FloatVector va = FloatVector.fromArray(SPECIES, stateA, aOffset + i, mask);
            FloatVector vb = FloatVector.fromArray(SPECIES, stateB, bOffset + i, mask);
            FloatVector diff = va.sub(vb);
            
            sumSqDiff = sumSqDiff.add(diff.mul(diff, mask));
        }

        float sqDiff = sumSqDiff.reduceLanes(VectorOperators.ADD);

        return (float) Math.exp(-sqDiff / (2.0 * sigma * sigma));
    }

    /**
     * Batch processes multiple candidate states against a current state.
     *
     * @param currentState the reference state
     * @param candidates   array of candidate states
     * @param sigma        bandwidth parameter
     * @return float array of resonance scores
     */
    public static float[] computeBatch(float[] currentState, float[][] candidates, float sigma) {
        float[] results = new float[candidates.length];
        for (int i = 0; i < candidates.length; i++) {
            results[i] = compute(currentState, candidates[i], sigma);
        }
        return results;
    }


}
