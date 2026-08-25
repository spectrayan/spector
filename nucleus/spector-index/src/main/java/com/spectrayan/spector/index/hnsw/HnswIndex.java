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
package com.spectrayan.spector.index;

import com.spectrayan.spector.config.properties.HnswProperties;
import com.spectrayan.spector.core.similarity.SimilarityFunction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

/**
 * HNSW (Hierarchical Navigable Small World) vector index.
 *
 * <p>Implements approximate nearest-neighbor search using a multi-layer
 * navigable small world graph. Distance computations delegate to the
 * SIMD-accelerated kernels in {@code spector-core}.</p>
 *
 * @see AbstractHnswIndex
 * @see QuantizedHnswIndex
 */
public class HnswIndex extends AbstractHnswIndex {

    private static final Logger log = LoggerFactory.getLogger(HnswIndex.class);

    // ── Vector storage ──
    private final float[][] vectors;

    /**
     * Creates a new HNSW index with inline vector storage.
     *
     * @param dimensions         vector dimensionality
     * @param capacity           max number of vectors
     * @param similarityFunction distance/similarity metric
     * @param params             HNSW tuning parameters
     */
    public HnswIndex(int dimensions, int capacity, SimilarityFunction similarityFunction, HnswProperties params) {
        super(dimensions, capacity, similarityFunction, params);
        this.vectors = new float[capacity][];

        log.info("HnswIndex created: dims={}, capacity={}, M={}, efC={}, efS={}, similarity={}",
                dimensions, capacity, params.m(), params.efConstruction(), params.efSearch(),
                similarityFunction);
    }

    /** Creates with default params. */
    public HnswIndex(int dimensions, int capacity, SimilarityFunction similarityFunction) {
        this(dimensions, capacity, similarityFunction, HnswProperties.DEFAULT);
    }

    // ─────────────── Template method implementations ───────────────

    @Override
    protected float computeDistance(float[] query, int nodeIdx) {
        return similarityFunction.compute(query, vectors[nodeIdx]);
    }

    @Override
    protected float[] getNodeVector(int nodeIdx) {
        return vectors[nodeIdx];
    }

    @Override
    protected void storeVector(int nodeIdx, float[] vector) {
        vectors[nodeIdx] = Arrays.copyOf(vector, vector.length);
    }

    // ─────────────── Accessor ───────────────

    /**
     * Returns the vector copy for the given node.
     */
    public float[] getVector(int nodeIdx) {
        return vectors[nodeIdx];
    }
}
