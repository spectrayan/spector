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
import com.spectrayan.spector.core.spi.SvasqDistanceKernel;

import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

import java.util.Objects;

/**
 * CPU SIMD implementation of {@link SvasqDistanceKernel} using Java 25 Vector API.
 *
 * <p>Performs asymmetric dot-product / distance between a float32 rotated query
 * and quantized INT8 vectors with per-vector codebook scales.</p>
 */
public final class CpuSimdSvasqKernel implements SvasqDistanceKernel {

    /** Singleton instance. */
    public static final CpuSimdSvasqKernel INSTANCE = new CpuSimdSvasqKernel();

    private static final VectorSpecies<Float> FLOAT_SPECIES = SimdCapability.PREFERRED_SPECIES;

    public CpuSimdSvasqKernel() {
    }

    @Override
    public void computeDistances(
            float[] rotatedQuery,
            byte[] quantizedVectorsFlat,
            float[] codebookScales,
            int vectorCount,
            int dimensions,
            float[] outDistances
    ) {
        validateInputs(rotatedQuery, quantizedVectorsFlat, codebookScales, vectorCount, dimensions, outDistances);
        if (vectorCount == 0) {
            return;
        }

        int floatLaneCount = FLOAT_SPECIES.length();
        int simdBound = dimensions - (dimensions % floatLaneCount);

        for (int i = 0; i < vectorCount; i++) {
            int offset = i * dimensions;
            float scale = (codebookScales != null && codebookScales.length > i) ? codebookScales[i] : 1.0f;

            FloatVector sumVec = FloatVector.zero(FLOAT_SPECIES);
            int d = 0;

            for (; d < simdBound; d += floatLaneCount) {
                FloatVector qVec = FloatVector.fromArray(FLOAT_SPECIES, rotatedQuery, d);
                // Load byte array slice and convert to float vector
                float[] tempBuf = new float[floatLaneCount];
                for (int l = 0; l < floatLaneCount; l++) {
                    tempBuf[l] = (float) quantizedVectorsFlat[offset + d + l];
                }
                FloatVector qByteVec = FloatVector.fromArray(FLOAT_SPECIES, tempBuf, 0);
                sumVec = qVec.fma(qByteVec, sumVec);
            }

            float dot = sumVec.reduceLanes(VectorOperators.ADD);
            for (; d < dimensions; d++) {
                dot += rotatedQuery[d] * ((float) quantizedVectorsFlat[offset + d]);
            }

            outDistances[i] = dot * scale;
        }
    }

    private static void validateInputs(
            float[] rotatedQuery,
            byte[] quantizedVectorsFlat,
            float[] codebookScales,
            int vectorCount,
            int dimensions,
            float[] outDistances
    ) {
        Objects.requireNonNull(rotatedQuery, "rotatedQuery must not be null");
        Objects.requireNonNull(quantizedVectorsFlat, "quantizedVectorsFlat must not be null");
        Objects.requireNonNull(outDistances, "outDistances must not be null");
        if (rotatedQuery.length != dimensions) {
            throw new SpectorValidationException(
                    ErrorCode.ARGUMENT_INVALID,
                    "Rotated query length (" + rotatedQuery.length + ") does not match dimensions (" + dimensions + ")"
            );
        }
        if (vectorCount < 0 || dimensions <= 0) {
            throw new SpectorValidationException(
                    ErrorCode.ARGUMENT_INVALID,
                    "Invalid vectorCount (" + vectorCount + ") or dimensions (" + dimensions + ")"
            );
        }
        long expectedLength = (long) vectorCount * dimensions;
        if (quantizedVectorsFlat.length < expectedLength) {
            throw new SpectorValidationException(
                    ErrorCode.ARGUMENT_INVALID,
                    "Quantized byte array length (" + quantizedVectorsFlat.length + ") is smaller than expected (" + expectedLength + ")"
            );
        }
        if (outDistances.length < vectorCount) {
            throw new SpectorValidationException(
                    ErrorCode.ARGUMENT_INVALID,
                    "Output distances array length (" + outDistances.length + ") is smaller than vectorCount (" + vectorCount + ")"
            );
        }
    }
}
