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

/**
 * Kernel for asymmetric distance computation against SVASQ (Fast Walsh-Hadamard Transform rotated
 * INT8 / INT4 / INT2 quantized) vectors.
 */
public interface SvasqDistanceKernel extends ComputeKernel {

    /**
     * Computes asymmetric dot-product / distances between a rotated float32 query and
     * a batch of quantized INT8 vectors.
     *
     * @param rotatedQuery          rotated query vector of length {@code dimensions}
     * @param quantizedVectorsFlat  flattened quantized byte vectors (vectorCount × dimensions)
     * @param codebookScales        per-vector reconstruction scale factors
     * @param vectorCount           number of quantized vectors
     * @param dimensions            vector dimensionality
     * @param outDistances          pre-allocated output array of length at least {@code vectorCount}
     */
    void computeDistances(
            float[] rotatedQuery,
            byte[] quantizedVectorsFlat,
            float[] codebookScales,
            int vectorCount,
            int dimensions,
            float[] outDistances
    );
}
