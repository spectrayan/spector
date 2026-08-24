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
package com.spectrayan.spector.core.spi;

import com.spectrayan.spector.core.simd.SimdCapability;
import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

import java.util.Objects;

/**
 * CPU SIMD implementation of {@link SimilarityKernel} using Java's Vector API.
 *
 * <p>Processes batch vector operations across host vector registers (AVX-512,
 * AVX2, ARM NEON). This is the default in-tree engine with zero external dependencies.</p>
 */
public final class CpuSimdSimilarityKernel implements SimilarityKernel {

    /** Singleton instance. */
    public static final CpuSimdSimilarityKernel INSTANCE = new CpuSimdSimilarityKernel();

    private static final VectorSpecies<Float> SPECIES = SimdCapability.PREFERRED_SPECIES;

    private CpuSimdSimilarityKernel() {
    }

    @Override
    public float[] cosineSimilarity(float[] query, float[] database, int numVectors, int dimensions) {
        validateInputs(query, database, numVectors, dimensions);
        if (numVectors == 0) {
            return new float[0];
        }

        int vectorLen = SPECIES.length();
        int simdBound = dimensions - (dimensions % vectorLen);

        // Precompute query norm (single SIMD pass, amortized over all N database vectors)
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

        float[] results = new float[numVectors];
        if (queryNorm == 0f) {
            return results;
        }

        // Fused dot-product + doc-norm per database vector
        for (int i = 0; i < numVectors; i++) {
            int offset = i * dimensions;
            FloatVector dotVec = FloatVector.zero(SPECIES);
            FloatVector normVec = FloatVector.zero(SPECIES);

            d = 0;
            for (; d < simdBound; d += vectorLen) {
                FloatVector qVec = FloatVector.fromArray(SPECIES, query, d);
                FloatVector dbVec = FloatVector.fromArray(SPECIES, database, offset + d);
                dotVec = qVec.fma(dbVec, dotVec);
                normVec = dbVec.fma(dbVec, normVec);
            }

            float dot = dotVec.reduceLanes(VectorOperators.ADD);
            float docNormSq = normVec.reduceLanes(VectorOperators.ADD);

            for (; d < dimensions; d++) {
                dot += query[d] * database[offset + d];
                docNormSq += database[offset + d] * database[offset + d];
            }

            float docNorm = (float) Math.sqrt(docNormSq);
            results[i] = (docNorm > 0f) ? (dot / (queryNorm * docNorm)) : 0f;
        }

        return results;
    }

    @Override
    public float[] dotProduct(float[] query, float[] database, int numVectors, int dimensions) {
        validateInputs(query, database, numVectors, dimensions);
        if (numVectors == 0) {
            return new float[0];
        }

        float[] results = new float[numVectors];
        int vectorLen = SPECIES.length();
        int simdBound = dimensions - (dimensions % vectorLen);

        for (int i = 0; i < numVectors; i++) {
            int offset = i * dimensions;
            FloatVector sumVec = FloatVector.zero(SPECIES);
            int d = 0;

            for (; d < simdBound; d += vectorLen) {
                FloatVector qVec = FloatVector.fromArray(SPECIES, query, d);
                FloatVector dbVec = FloatVector.fromArray(SPECIES, database, offset + d);
                sumVec = qVec.fma(dbVec, sumVec);
            }

            float dot = sumVec.reduceLanes(VectorOperators.ADD);
            for (; d < dimensions; d++) {
                dot += query[d] * database[offset + d];
            }
            results[i] = dot;
        }

        return results;
    }

    @Override
    public float[] euclideanDistance(float[] query, float[] database, int numVectors, int dimensions) {
        validateInputs(query, database, numVectors, dimensions);
        if (numVectors == 0) {
            return new float[0];
        }

        float[] results = new float[numVectors];
        int vectorLen = SPECIES.length();
        int simdBound = dimensions - (dimensions % vectorLen);

        for (int i = 0; i < numVectors; i++) {
            int offset = i * dimensions;
            FloatVector sumVec = FloatVector.zero(SPECIES);
            int d = 0;

            for (; d < simdBound; d += vectorLen) {
                FloatVector qVec = FloatVector.fromArray(SPECIES, query, d);
                FloatVector dbVec = FloatVector.fromArray(SPECIES, database, offset + d);
                FloatVector diff = qVec.sub(dbVec);
                sumVec = diff.fma(diff, sumVec);
            }

            float distSq = sumVec.reduceLanes(VectorOperators.ADD);
            for (; d < dimensions; d++) {
                float diff = query[d] - database[offset + d];
                distSq += diff * diff;
            }
            results[i] = (float) Math.sqrt(distSq);
        }

        return results;
    }

    private static void validateInputs(float[] query, float[] database, int numVectors, int dimensions) {
        Objects.requireNonNull(query, "query array must not be null");
        Objects.requireNonNull(database, "database array must not be null");
        if (numVectors < 0) {
            throw new IllegalArgumentException("numVectors must be non-negative: " + numVectors);
        }
        if (dimensions <= 0) {
            throw new IllegalArgumentException("dimensions must be positive: " + dimensions);
        }
        if (query.length < dimensions) {
            throw new IllegalArgumentException("query length (" + query.length + ") < dimensions (" + dimensions + ")");
        }
        long requiredDatabaseLen = (long) numVectors * dimensions;
        if (database.length < requiredDatabaseLen) {
            throw new IllegalArgumentException("database length (" + database.length + ") < numVectors * dimensions (" + requiredDatabaseLen + ")");
        }
    }
}
