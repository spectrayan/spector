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
package com.spectrayan.spector.memory.graph;

/**
 * Immutable plan produced by {@link SpectralSparsifier} describing which edges
 * to keep or drop based on leverage (effective resistance) scoring.
 *
 * <p>The plan is a primitive-array structure sized to match the graph's
 * edge layout: {@code keep[node][edgeIdx]} indicates whether each edge
 * should survive sparsification.</p>
 *
 * @param keep                 per-edge keep/drop decisions: {@code keep[node][edgeIdx]}
 * @param totalEdges           total edges in the graph
 * @param candidateCount       number of edges flagged for dropping
 * @param meanLeverageDropped  average leverage of edges flagged for dropping (0 if none)
 * @param meanLeverageKept     average leverage of edges kept (0 if none)
 */
public record SparsificationPlan(
        boolean[][] keep,
        int totalEdges,
        int candidateCount,
        float meanLeverageDropped,
        float meanLeverageKept
) {

    /**
     * Returns {@code true} if no edges would be dropped by this plan.
     */
    public boolean isEmpty() {
        return candidateCount == 0;
    }

    /**
     * Returns the number of edges that would survive.
     */
    public int keptCount() {
        return totalEdges - candidateCount;
    }
}
