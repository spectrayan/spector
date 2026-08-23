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

import com.spectrayan.spector.core.simd.SimdCapability;
import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;
import com.spectrayan.spector.commons.error.SpectorValidationException;
import com.spectrayan.spector.commons.error.ErrorCode;

public final class StylometricKernel {

    private static final VectorSpecies<Float> SPECIES = SimdCapability.PREFERRED_SPECIES;

    private StylometricKernel() {
        // utility class
    }

    public static float stylometricDistance(float[] featuresA, float[] featuresB, float[] weights) {
        int length = featuresA.length;
        if (featuresB.length != length || weights.length != length) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Array lengths must match");
        }

        int laneCount = SPECIES.length();
        FloatVector sum = FloatVector.zero(SPECIES);

        int i = 0;
        int limit = SPECIES.loopBound(length);
        for (; i < limit; i += laneCount) {
            FloatVector va = FloatVector.fromArray(SPECIES, featuresA, i);
            FloatVector vb = FloatVector.fromArray(SPECIES, featuresB, i);
            FloatVector vw = FloatVector.fromArray(SPECIES, weights, i);
            FloatVector diff = va.sub(vb);
            FloatVector diffSq = diff.mul(diff);
            FloatVector weightedDiffSq = diffSq.mul(vw);
            sum = sum.add(weightedDiffSq);
        }

        if (i < length) {
            VectorMask<Float> mask = SPECIES.indexInRange(i, length);
            FloatVector va = FloatVector.fromArray(SPECIES, featuresA, i, mask);
            FloatVector vb = FloatVector.fromArray(SPECIES, featuresB, i, mask);
            FloatVector vw = FloatVector.fromArray(SPECIES, weights, i, mask);
            FloatVector diff = va.sub(vb, mask);
            FloatVector diffSq = diff.mul(diff, mask);
            FloatVector weightedDiffSq = diffSq.mul(vw, mask);
            sum = sum.add(weightedDiffSq);
        }

        return (float) Math.sqrt(sum.reduceLanes(VectorOperators.ADD));
    }

    public static float stylometricSimilarity(float[] featuresA, float[] featuresB, float[] weights) {
        return 1.0f / (1.0f + stylometricDistance(featuresA, featuresB, weights));
    }
}
