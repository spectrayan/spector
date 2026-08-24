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

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;

import java.util.Objects;

/**
 * Pure standard Java SE scalar implementation of {@link SimilarityKernel}.
 *
 * <p>Serves as the zero-dependency, zero-incubator baseline fallback in {@code spector-core}
 * when no specialized hardware accelerator (CPU SIMD or CUDA GPU) is loaded.</p>
 */
public final class ScalarSimilarityKernel implements SimilarityKernel {

    /** Singleton instance. */
    public static final ScalarSimilarityKernel INSTANCE = new ScalarSimilarityKernel();

    public ScalarSimilarityKernel() {
    }

    @Override
    public float[] cosineSimilarity(float[] query, float[] database, int numVectors, int dimensions) {
        validateInputs(query, database, numVectors, dimensions);
        if (numVectors == 0) {
            return new float[0];
        }

        float queryNormSq = 0f;
        for (int d = 0; d < dimensions; d++) {
            queryNormSq += query[d] * query[d];
        }
        float queryNorm = (float) Math.sqrt(queryNormSq);

        float[] results = new float[numVectors];
        if (queryNorm == 0f) {
            return results;
        }

        for (int i = 0; i < numVectors; i++) {
            int offset = i * dimensions;
            float dot = 0f;
            float docNormSq = 0f;
            for (int d = 0; d < dimensions; d++) {
                float q = query[d];
                float db = database[offset + d];
                dot += q * db;
                docNormSq += db * db;
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
        for (int i = 0; i < numVectors; i++) {
            int offset = i * dimensions;
            float dot = 0f;
            for (int d = 0; d < dimensions; d++) {
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
        for (int i = 0; i < numVectors; i++) {
            int offset = i * dimensions;
            float distSq = 0f;
            for (int d = 0; d < dimensions; d++) {
                float diff = query[d] - database[offset + d];
                distSq += diff * diff;
            }
            results[i] = (float) Math.sqrt(distSq);
        }
        return results;
    }

    private static void validateInputs(float[] query, float[] database, int numVectors, int dimensions) {
        Objects.requireNonNull(query, "query must not be null");
        Objects.requireNonNull(database, "database must not be null");
        if (query.length != dimensions) {
            throw new SpectorValidationException(
                    ErrorCode.ARGUMENT_INVALID,
                    "Query vector length (" + query.length + ") does not match dimensions (" + dimensions + ")"
            );
        }
        if (numVectors < 0 || dimensions <= 0) {
            throw new SpectorValidationException(
                    ErrorCode.ARGUMENT_INVALID,
                    "Invalid numVectors (" + numVectors + ") or dimensions (" + dimensions + ")"
            );
        }
        long expectedLength = (long) numVectors * dimensions;
        if (database.length < expectedLength) {
            throw new SpectorValidationException(
                    ErrorCode.ARGUMENT_INVALID,
                    "Database array length (" + database.length + ") is smaller than expected (" + expectedLength + ")"
            );
        }
    }
}
