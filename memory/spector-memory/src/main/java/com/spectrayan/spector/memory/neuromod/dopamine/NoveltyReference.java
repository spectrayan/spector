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
package com.spectrayan.spector.memory.neuromod.dopamine;

import com.spectrayan.spector.core.similarity.SimilarityFunction;
import com.spectrayan.spector.kernel.store.AbstractEngramMemory;
import com.spectrayan.spector.kernel.store.WorkingMemory;
import com.spectrayan.spector.memory.cortex.CognitiveMemoryRouter;

/**
 * Nearest-neighbour distance used for novelty. Working memory alone is the wrong reference:
 * semantic and procedural writes never land there, so a cold store looked empty and every
 * write scored the same. This scans those slabs (most recent records, capped) as well.
 *
 * <p>Episodic turns do not store an embedding. The {@link SurpriseDetector} ring, consulted
 * by the caller, covers those.</p>
 */
public final class NoveltyReference {

    /** How many of the most recent slab records to compare. */
    public static final int RECENT_CAP = 256;

    /** No live neighbour. Callers treat this as fully novel. */
    public static final float EMPTY = Float.POSITIVE_INFINITY;

    private NoveltyReference() {}

    /**
     * Minimum L2 distance from {@code query} to a live working, semantic, or procedural record.
     *
     * @param router  cognitive stores; {@code null} is an empty reference
     * @param query   candidate embedding
     * @param mins    quantizer per-dimension minima
     * @param scales  quantizer per-dimension scales
     * @return the nearest distance, or {@link #EMPTY}
     */
    public static float nearest(CognitiveMemoryRouter router, float[] query, float[] mins, float[] scales) {
        if (router == null || query == null || mins == null || scales == null) {
            return EMPTY;
        }
        float best = EMPTY;
        best = closer(best, working(router.working(), query, mins, scales));
        best = closer(best, slab(router.semantic(), query, mins, scales));
        best = closer(best, slab(router.procedural(), query, mins, scales));
        return best;
    }

    private static float closer(float best, float candidate) {
        return candidate < best ? candidate : best;
    }

    private static float working(WorkingMemory store, float[] query, float[] mins, float[] scales) {
        if (store == null || store.visibleCount() <= 0) {
            return EMPTY;
        }
        float dist = store.nearestDistance(query, mins, scales);
        if (!Float.isFinite(dist) || dist >= Float.MAX_VALUE / 2f) {
            return EMPTY;
        }
        return dist;
    }

    private static float slab(AbstractEngramMemory<?> store, float[] query, float[] mins, float[] scales) {
        if (store == null) {
            return EMPTY;
        }
        int count = store.visibleCount();
        if (count <= 0) {
            return EMPTY;
        }
        int dims = Math.min(query.length, Math.min(mins.length, scales.length));
        if (dims <= 0) {
            return EMPTY;
        }
        float best = EMPTY;
        int start = Math.max(0, count - RECENT_CAP);
        for (int slot = count - 1; slot >= start; slot--) {
            byte[] quantized = store.readQuantizedVector(slot);
            if (quantized == null || quantized.length == 0) {
                continue;
            }
            int len = Math.min(dims, quantized.length);
            float dist = SimilarityFunction.EUCLIDEAN.computeQuantized(query, quantized, mins, scales, len);
            if (dist < best) {
                best = dist;
            }
        }
        return best;
    }
}
