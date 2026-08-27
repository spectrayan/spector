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
import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

import java.util.Objects;

/**
 * CPU SIMD Mean-Pooling and L2 Normalization Kernel using Java 25 Panama Vector API.
 *
 * <p>Vectorized across AVX-512, AVX2, and ARM NEON registers for sub-microsecond
 * transformer token tensor reduction and dense vector normalization.</p>
 */
public final class CpuSimdMeanPoolKernel {

    public static final CpuSimdMeanPoolKernel INSTANCE = new CpuSimdMeanPoolKernel();

    private static final VectorSpecies<Float> SPECIES = SimdCapability.PREFERRED_SPECIES;

    public CpuSimdMeanPoolKernel() {}

    /**
     * Performs mean-pooling over a 2D token embedding tensor [seq_len, hidden_dim]
     * weighted by the attention mask, and applies L2 normalization to unit length.
     *
     * @param tokenEmbeddings 2D array of token embeddings [seq_len][hidden_dim]
     * @param attentionMask   1D array of attention weights (1 for real tokens, 0 for padding)
     * @return 1D unit-length normalized dense embedding vector [hidden_dim]
     */
    public float[] poolAndNormalize(float[][] tokenEmbeddings, long[] attentionMask) {
        Objects.requireNonNull(tokenEmbeddings, "tokenEmbeddings must not be null");
        if (tokenEmbeddings.length == 0) {
            throw new IllegalArgumentException("tokenEmbeddings must not be empty");
        }

        int seqLen = tokenEmbeddings.length;
        int hiddenDim = tokenEmbeddings[0].length;
        float[] pooled = new float[hiddenDim];

        float totalMask = 0.0f;
        for (int i = 0; i < seqLen; i++) {
            float mask = (attentionMask != null && i < attentionMask.length) ? (float) attentionMask[i] : 1.0f;
            if (mask > 0.0f) {
                totalMask += mask;
                accumulateSimd(pooled, tokenEmbeddings[i], mask, hiddenDim);
            }
        }

        if (totalMask > 0.0f) {
            float invMask = 1.0f / totalMask;
            scaleSimd(pooled, invMask, hiddenDim);
        }

        normalizeL2Simd(pooled, hiddenDim);
        return pooled;
    }

    private static void accumulateSimd(float[] target, float[] source, float weight, int dim) {
        int i = 0;
        int bound = SPECIES.loopBound(dim);
        var weightVec = FloatVector.broadcast(SPECIES, weight);

        for (; i < bound; i += SPECIES.length()) {
            var vTarget = FloatVector.fromArray(SPECIES, target, i);
            var vSource = FloatVector.fromArray(SPECIES, source, i);
            vTarget.add(vSource.mul(weightVec)).intoArray(target, i);
        }

        for (; i < dim; i++) {
            target[i] += source[i] * weight;
        }
    }

    private static void scaleSimd(float[] target, float scale, int dim) {
        int i = 0;
        int bound = SPECIES.loopBound(dim);
        var scaleVec = FloatVector.broadcast(SPECIES, scale);

        for (; i < bound; i += SPECIES.length()) {
            var v = FloatVector.fromArray(SPECIES, target, i);
            v.mul(scaleVec).intoArray(target, i);
        }

        for (; i < dim; i++) {
            target[i] *= scale;
        }
    }

    /**
     * Vectorized L2 normalization of an in-place float array to unit length.
     *
     * @param vector the vector to normalize
     * @param dim    the dimensionality
     */
    public void normalizeL2Simd(float[] vector, int dim) {
        float sumSq = 0.0f;
        int i = 0;
        int bound = SPECIES.loopBound(dim);

        for (; i < bound; i += SPECIES.length()) {
            var v = FloatVector.fromArray(SPECIES, vector, i);
            sumSq += v.mul(v).reduceLanes(VectorOperators.ADD);
        }

        for (; i < dim; i++) {
            sumSq += vector[i] * vector[i];
        }

        if (sumSq > 1e-12f) {
            float norm = (float) Math.sqrt(sumSq);
            float invNorm = 1.0f / norm;
            scaleSimd(vector, invNorm, dim);
        }
    }
}
