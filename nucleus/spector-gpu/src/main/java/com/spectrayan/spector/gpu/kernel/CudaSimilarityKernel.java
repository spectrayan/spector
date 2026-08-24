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
package com.spectrayan.spector.gpu.kernel;

import com.spectrayan.spector.core.spi.SimilarityKernel;

/**
 * CUDA GPU implementation of {@link SimilarityKernel}.
 *
 * <p>Dispatches similarity computations (cosine, dot product, Euclidean distance)
 * to CUDA GPU kernels via Panama FFM, with transparent automatic fallback to CPU SIMD.</p>
 */
public final class CudaSimilarityKernel implements SimilarityKernel {

    private final CudaCosineKernel cosineKernel;
    private final CudaDotProductKernel dotKernel;
    private final CudaHnswKernel hnswKernel;

    /**
     * Creates a new CUDA similarity kernel instance.
     */
    public CudaSimilarityKernel() {
        this.cosineKernel = new CudaCosineKernel();
        this.dotKernel = new CudaDotProductKernel();
        this.hnswKernel = new CudaHnswKernel();
    }

    @Override
    public float[] cosineSimilarity(float[] query, float[] database, int numVectors, int dimensions) {
        return cosineKernel.compute(query, database, numVectors, dimensions);
    }

    @Override
    public float[] dotProduct(float[] query, float[] database, int numVectors, int dimensions) {
        return dotKernel.compute(query, database, numVectors, dimensions);
    }

    @Override
    public float[] euclideanDistance(float[] query, float[] database, int numVectors, int dimensions) {
        float[] dists = hnswKernel.computeL2(query, database, numVectors, dimensions);
        for (int i = 0; i < dists.length; i++) {
            dists[i] = (float) Math.sqrt(Math.max(0.0f, dists[i]));
        }
        return dists;
    }
}
