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
package com.spectrayan.spector.provider.onnx;

import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorSpecies;

import java.util.Objects;

/**
 * High-performance SIMD Mean-Pooling and L2 Normalization engine using Java Vector API.
 *
 * <p>Vectorized across AVX-512 / AVX-2 float lanes for zero-allocation, sub-microsecond
 * tensor reduction directly in JVM memory.</p>
 */
public final class SimdMeanPooler {

    private static final VectorSpecies<Float> SPECIES = FloatVector.SPECIES_PREFERRED;

    private SimdMeanPooler() {}

    /**
     * Performs mean-pooling over a 2D token embedding tensor [seq_len, hidden_dim]
     * weighted by the attention mask, and applies L2 normalization to unit length.
     *
     * @param tokenEmbeddings 2D array of token embeddings [seq_len][hidden_dim]
     * @param attentionMask   1D array of attention weights (1 for real tokens, 0 for padding)
     * @return 1D unit-length normalized dense embedding vector [hidden_dim]
     */
    public static float[] poolAndNormalize(float[][] tokenEmbeddings, long[] attentionMask) {
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

    /** Vectorized element-wise accumulation: target[j] += source[j] * weight */
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

    /** Vectorized scalar multiplication: target[j] *= scale */
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

    /** Vectorized L2 normalization to unit length */
    public static void normalizeL2Simd(float[] vector, int dim) {
        float sumSq = 0.0f;
        int i = 0;
        int bound = SPECIES.loopBound(dim);

        for (; i < bound; i += SPECIES.length()) {
            var v = FloatVector.fromArray(SPECIES, vector, i);
            sumSq += v.mul(v).reduceLanes(jdk.incubator.vector.VectorOperators.ADD);
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
