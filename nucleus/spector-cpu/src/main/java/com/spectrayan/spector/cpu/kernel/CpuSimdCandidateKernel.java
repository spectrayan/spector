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

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;
import com.spectrayan.spector.core.simd.SimdCapability;
import com.spectrayan.spector.core.similarity.SimilarityFunction;
import com.spectrayan.spector.core.spi.HnswCandidateKernel;

import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

import java.util.Objects;

/**
 * CPU SIMD implementation of {@link HnswCandidateKernel} using Java 25 Vector API.
 */
public final class CpuSimdCandidateKernel implements HnswCandidateKernel {

    /** Singleton instance. */
    public static final CpuSimdCandidateKernel INSTANCE = new CpuSimdCandidateKernel();

    private static final VectorSpecies<Float> SPECIES = SimdCapability.PREFERRED_SPECIES;

    public CpuSimdCandidateKernel() {
    }

    @Override
    public void evaluateCandidates(
            float[] query,
            float[] candidateVectorsFlat,
            int candidateCount,
            int dimensions,
            SimilarityFunction function,
            float[] outScores
    ) {
        validateInputs(query, candidateVectorsFlat, candidateCount, dimensions, outScores);
        if (candidateCount == 0) {
            return;
        }

        switch (function) {
            case COSINE -> evaluateCosine(query, candidateVectorsFlat, candidateCount, dimensions, outScores);
            case DOT_PRODUCT -> evaluateDotProduct(query, candidateVectorsFlat, candidateCount, dimensions, outScores);
            case EUCLIDEAN -> evaluateEuclidean(query, candidateVectorsFlat, candidateCount, dimensions, outScores);
            default -> {
                for (int i = 0; i < candidateCount; i++) {
                    float[] vec = new float[dimensions];
                    System.arraycopy(candidateVectorsFlat, i * dimensions, vec, 0, dimensions);
                    outScores[i] = function.compute(query, vec);
                }
            }
        }
    }

    private void evaluateCosine(
            float[] query,
            float[] candidateVectorsFlat,
            int candidateCount,
            int dimensions,
            float[] outScores
    ) {
        int vectorLen = SPECIES.length();
        int simdBound = dimensions - (dimensions % vectorLen);

        // Precompute query norm
        FloatVector qNormVec = FloatVector.zero(SPECIES);
        int d = 0;
        for (; d < simdBound; d += vectorLen) {
            FloatVector qVec = FloatVector.fromArray(SPECIES, query, d);
            qNormVec = qVec.fma(qVec, qNormVec);
        }
        float queryNormSq = qNormVec.reduceLanes(VectorOperators.ADD);
        for (; d < dimensions; d++) {
            queryNormSq += query[d] * query[d];
        }
        float queryNorm = (float) Math.sqrt(queryNormSq);

        if (queryNorm == 0f) {
            for (int i = 0; i < candidateCount; i++) {
                outScores[i] = 0f;
            }
            return;
        }

        for (int i = 0; i < candidateCount; i++) {
            int offset = i * dimensions;
            FloatVector dotVec = FloatVector.zero(SPECIES);
            FloatVector normVec = FloatVector.zero(SPECIES);

            d = 0;
            for (; d < simdBound; d += vectorLen) {
                FloatVector qVec = FloatVector.fromArray(SPECIES, query, d);
                FloatVector dbVec = FloatVector.fromArray(SPECIES, candidateVectorsFlat, offset + d);
                dotVec = qVec.fma(dbVec, dotVec);
                normVec = dbVec.fma(dbVec, normVec);
            }

            float dot = dotVec.reduceLanes(VectorOperators.ADD);
            float docNormSq = normVec.reduceLanes(VectorOperators.ADD);

            for (; d < dimensions; d++) {
                dot += query[d] * candidateVectorsFlat[offset + d];
                docNormSq += candidateVectorsFlat[offset + d] * candidateVectorsFlat[offset + d];
            }

            float docNorm = (float) Math.sqrt(docNormSq);
            outScores[i] = (docNorm > 0f) ? (dot / (queryNorm * docNorm)) : 0f;
        }
    }

    private void evaluateDotProduct(
            float[] query,
            float[] candidateVectorsFlat,
            int candidateCount,
            int dimensions,
            float[] outScores
    ) {
        int vectorLen = SPECIES.length();
        int simdBound = dimensions - (dimensions % vectorLen);

        for (int i = 0; i < candidateCount; i++) {
            int offset = i * dimensions;
            FloatVector sumVec = FloatVector.zero(SPECIES);
            int d = 0;

            for (; d < simdBound; d += vectorLen) {
                FloatVector qVec = FloatVector.fromArray(SPECIES, query, d);
                FloatVector dbVec = FloatVector.fromArray(SPECIES, candidateVectorsFlat, offset + d);
                sumVec = qVec.fma(dbVec, sumVec);
            }

            float dot = sumVec.reduceLanes(VectorOperators.ADD);
            for (; d < dimensions; d++) {
                dot += query[d] * candidateVectorsFlat[offset + d];
            }
            outScores[i] = dot;
        }
    }

    private void evaluateEuclidean(
            float[] query,
            float[] candidateVectorsFlat,
            int candidateCount,
            int dimensions,
            float[] outScores
    ) {
        int vectorLen = SPECIES.length();
        int simdBound = dimensions - (dimensions % vectorLen);

        for (int i = 0; i < candidateCount; i++) {
            int offset = i * dimensions;
            FloatVector sumVec = FloatVector.zero(SPECIES);
            int d = 0;

            for (; d < simdBound; d += vectorLen) {
                FloatVector qVec = FloatVector.fromArray(SPECIES, query, d);
                FloatVector dbVec = FloatVector.fromArray(SPECIES, candidateVectorsFlat, offset + d);
                FloatVector diff = qVec.sub(dbVec);
                sumVec = diff.fma(diff, sumVec);
            }

            float distSq = sumVec.reduceLanes(VectorOperators.ADD);
            for (; d < dimensions; d++) {
                float diff = query[d] - candidateVectorsFlat[offset + d];
                distSq += diff * diff;
            }
            outScores[i] = (float) Math.sqrt(distSq);
        }
    }

    private static void validateInputs(
            float[] query,
            float[] candidateVectorsFlat,
            int candidateCount,
            int dimensions,
            float[] outScores
    ) {
        Objects.requireNonNull(query, "query must not be null");
        Objects.requireNonNull(candidateVectorsFlat, "candidateVectorsFlat must not be null");
        Objects.requireNonNull(outScores, "outScores must not be null");
        if (query.length != dimensions) {
            throw new SpectorValidationException(
                    ErrorCode.ARGUMENT_INVALID,
                    "Query vector length (" + query.length + ") does not match dimensions (" + dimensions + ")"
            );
        }
        if (candidateCount < 0 || dimensions <= 0) {
            throw new SpectorValidationException(
                    ErrorCode.ARGUMENT_INVALID,
                    "Invalid candidateCount (" + candidateCount + ") or dimensions (" + dimensions + ")"
            );
        }
        long expectedLength = (long) candidateCount * dimensions;
        if (candidateVectorsFlat.length < expectedLength) {
            throw new SpectorValidationException(
                    ErrorCode.ARGUMENT_INVALID,
                    "Candidate array length (" + candidateVectorsFlat.length + ") is smaller than expected (" + expectedLength + ")"
            );
        }
        if (outScores.length < candidateCount) {
            throw new SpectorValidationException(
                    ErrorCode.ARGUMENT_INVALID,
                    "Output scores array length (" + outScores.length + ") is smaller than candidateCount (" + candidateCount + ")"
            );
        }
    }
}
