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
 * Kernel for batch vector similarity and distance computation.
 *
 * <p>Computes similarity scores between a single query vector and a batch
 * of N database vectors in a single call, enabling hardware accelerators
 * to amortize setup costs across the entire batch.</p>
 */
public interface SimilarityKernel extends ComputeKernel {

    /**
     * Computes batch cosine similarities between a query and database vectors.
     *
     * @param query      query vector of length {@code dimensions}
     * @param database   database vectors as flat array (numVectors × dimensions)
     * @param numVectors number of database vectors
     * @param dimensions vector dimensionality
     * @return array of {@code numVectors} cosine similarity scores
     */
    float[] batchCosineSimilarity(float[] query, float[] database, int numVectors, int dimensions);

    /**
     * Computes batch dot products between a query and database vectors.
     *
     * @param query      query vector of length {@code dimensions}
     * @param database   database vectors as flat array (numVectors × dimensions)
     * @param numVectors number of database vectors
     * @param dimensions vector dimensionality
     * @return array of {@code numVectors} dot product scores
     */
    float[] batchDotProduct(float[] query, float[] database, int numVectors, int dimensions);

    /**
     * Computes batch Euclidean (L2) distances between a query and database vectors.
     *
     * @param query      query vector of length {@code dimensions}
     * @param database   database vectors as flat array (numVectors × dimensions)
     * @param numVectors number of database vectors
     * @param dimensions vector dimensionality
     * @return array of {@code numVectors} Euclidean distance scores
     */
    float[] batchEuclideanDistance(float[] query, float[] database, int numVectors, int dimensions);
}
