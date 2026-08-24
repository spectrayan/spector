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

import com.spectrayan.spector.core.similarity.SimilarityFunction;

/**
 * Kernel for batched candidate evaluation during HNSW graph search and construction.
 *
 * <p>During HNSW graph traversal (e.g. in {@code searchLayer()}), an unvisited candidate
 * list of size M ($16 \le M \le 64$) is evaluated against the search query. This kernel
 * evaluates all candidates in a single hardware-accelerated batch call.</p>
 */
public interface HnswCandidateKernel extends ComputeKernel {

    /**
     * Evaluates similarity or distance between a query vector and a contiguous batch
     * of candidate vectors.
     *
     * @param query                 query vector of length {@code dimensions}
     * @param candidateVectorsFlat  flattened candidate vectors (candidateCount × dimensions)
     * @param candidateCount        number of candidate vectors
     * @param dimensions            vector dimensionality
     * @param function              similarity metric (e.g., COSINE, DOT_PRODUCT, EUCLIDEAN)
     * @param outScores             pre-allocated output array of length at least {@code candidateCount}
     */
    void evaluateCandidates(
            float[] query,
            float[] candidateVectorsFlat,
            int candidateCount,
            int dimensions,
            SimilarityFunction function,
            float[] outScores
    );
}
