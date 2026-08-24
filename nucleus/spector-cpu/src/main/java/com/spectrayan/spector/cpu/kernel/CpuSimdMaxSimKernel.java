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
package com.spectrayan.spector.cpu.kernel;

import com.spectrayan.spector.core.simd.SimdCapability;
import com.spectrayan.spector.core.spi.MaxSimKernel;

import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

import java.util.Objects;

/**
 * CPU SIMD implementation of {@link MaxSimKernel} using Java 25 Vector API.
 *
 * <p>Uses fused multiply-add (FMA) vector instructions to compute token-level
 * MaxSim scoring across query and document matrices.</p>
 */
public final class CpuSimdMaxSimKernel implements MaxSimKernel {

    /** Singleton instance. */
    public static final CpuSimdMaxSimKernel INSTANCE = new CpuSimdMaxSimKernel();

    private static final VectorSpecies<Float> SPECIES = SimdCapability.PREFERRED_SPECIES;

    public CpuSimdMaxSimKernel() {
    }

    @Override
    public float maxSim(float[][] queryTokens, float[][] docTokens) {
        if (queryTokens == null || docTokens == null || queryTokens.length == 0 || docTokens.length == 0) {
            return 0.0f;
        }

        float totalScore = 0.0f;
        for (float[] qToken : queryTokens) {
            float maxDot = Float.NEGATIVE_INFINITY;
            for (float[] dToken : docTokens) {
                float dot = simdDotProduct(qToken, dToken);
                if (dot > maxDot) {
                    maxDot = dot;
                }
            }
            if (maxDot > Float.NEGATIVE_INFINITY) {
                totalScore += maxDot;
            }
        }
        return totalScore;
    }

    @Override
    public void maxSimBatch(float[][] queryTokens, float[][][] docTokensBatch, float[] outScores) {
        Objects.requireNonNull(queryTokens, "queryTokens must not be null");
        Objects.requireNonNull(docTokensBatch, "docTokensBatch must not be null");
        Objects.requireNonNull(outScores, "outScores must not be null");

        int docCount = docTokensBatch.length;
        for (int i = 0; i < docCount; i++) {
            outScores[i] = maxSim(queryTokens, docTokensBatch[i]);
        }
    }

    private static float simdDotProduct(float[] a, float[] b) {
        int n = Math.min(a.length, b.length);
        int vectorLen = SPECIES.length();
        int simdBound = n - (n % vectorLen);

        FloatVector sumVec = FloatVector.zero(SPECIES);
        int i = 0;

        for (; i < simdBound; i += vectorLen) {
            FloatVector va = FloatVector.fromArray(SPECIES, a, i);
            FloatVector vb = FloatVector.fromArray(SPECIES, b, i);
            sumVec = va.fma(vb, sumVec);
        }

        float result = sumVec.reduceLanes(VectorOperators.ADD);
        for (; i < n; i++) {
            result += a[i] * b[i];
        }

        return result;
    }
}
