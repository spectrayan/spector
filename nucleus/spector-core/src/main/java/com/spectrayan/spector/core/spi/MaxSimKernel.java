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
 * Kernel for ColBERT late-interaction token scoring (MaxSim).
 *
 * <p>MaxSim calculates token-level interaction relevance:
 * <pre>
 *   score(Q, D) = sum_i  max_j  dot(q_i, d_j)
 * </pre>
 * For each query token \(q_i\), finds the document token \(d_j\) with maximum
 * dot-product similarity, then sums across all query tokens.</p>
 */
public interface MaxSimKernel extends ComputeKernel {

    /**
     * Computes the MaxSim score between query token embeddings and a single document's token embeddings.
     *
     * @param queryTokens query token embeddings [queryLen][dims]
     * @param docTokens   document token embeddings [docLen][dims]
     * @return sum of per-query-token maximum similarities
     */
    float maxSim(float[][] queryTokens, float[][] docTokens);

    /**
     * Computes MaxSim scores between query token embeddings and a batch of documents.
     *
     * @param queryTokens    query token embeddings [queryLen][dims]
     * @param docTokensBatch batch of documents, each having token embeddings [docCount][docLen][dims]
     * @param outScores      pre-allocated output array of length at least {@code docCount}
     */
    void maxSimBatch(float[][] queryTokens, float[][][] docTokensBatch, float[] outScores);
}
