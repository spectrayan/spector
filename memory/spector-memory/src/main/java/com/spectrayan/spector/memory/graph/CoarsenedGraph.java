/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-memory/LICENSE
 *
 * Change Date: May 27, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.memory.graph;

import java.util.Collections;
import java.util.Map;

/**
 * Result of a Kron reduction graph coarsening operation.
 * Holds the reduced Laplacian matrix in CSR sparse format, cluster mappings,
 * and error bounds.
 *
 * @param clusters                   Map of representative entity ID -> EntityCluster
 * @param rowPointers                CSR row pointers array for reduced Laplacian
 * @param columnIndices              CSR column indices array for reduced Laplacian
 * @param values                     CSR values array for reduced Laplacian
 * @param originalNodeCount          Number of nodes in the graph before reduction
 * @param reducedNodeCount           Number of representative nodes in the reduced graph
 * @param maxEffectiveResistanceError Max relative error in effective resistance between kept pairs
 */
public record CoarsenedGraph(
    Map<Integer, EntityCluster> clusters,
    int[] rowPointers,
    int[] columnIndices,
    float[] values,
    int originalNodeCount,
    int reducedNodeCount,
    double maxEffectiveResistanceError
) {
    public CoarsenedGraph {
        clusters = clusters != null ? Map.copyOf(clusters) : Collections.emptyMap();
        rowPointers = rowPointers != null ? rowPointers.clone() : new int[0];
        columnIndices = columnIndices != null ? columnIndices.clone() : new int[0];
        values = values != null ? values.clone() : new float[0];
    }

    @Override
    public int[] rowPointers() {
        return rowPointers.clone();
    }

    @Override
    public int[] columnIndices() {
        return columnIndices.clone();
    }

    @Override
    public float[] values() {
        return values.clone();
    }
}
