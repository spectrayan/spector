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
package com.spectrayan.spector.memory.kernel.shape;

import com.spectrayan.spector.memory.kernel.Memory;
import com.spectrayan.spector.memory.kernel.MemoryLayout;
import java.lang.foreign.MemorySegment;

/**
 * Shape interface for CSR/slab node+edge structures.
 * Backs EntityGraph, HebbianGraphCsr, HyperEntityGraph, etc.
 *
 * @param <L> the memory layout type
 */
public interface GraphMemory<L extends MemoryLayout> extends Memory<L> {
    /**
     * Adds an edge between two nodes.
     * @param fromNode source node ID
     * @param toNode target node ID  
     * @param edgeBytes edge payload data
     * @return edge ID, or -1 if graph is full
     */
    int addEdge(int fromNode, int toNode, MemorySegment edgeBytes);
    
    /**
     * Removes an edge by ID (tombstones it).
     * @param edgeId the edge to remove
     */
    void removeEdge(int edgeId);
    
    /**
     * Returns an iterator over neighbour node IDs for the given node.
     * @param nodeId the source node
     * @return iterator of adjacent node IDs
     */
    java.util.PrimitiveIterator.OfInt neighbours(int nodeId);
    
    /**
     * Total number of active edges.
     * @return edge count
     */
    int edgeCount();
    
    /**
     * Total number of nodes with at least one edge.
     * @return node count
     */
    int nodeCount();
}
