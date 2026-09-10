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
package com.spectrayan.spector.kernel.shape;

import com.spectrayan.spector.kernel.shape.Memory;
import com.spectrayan.spector.kernel.layout.RegionLayout;
import java.lang.foreign.MemorySegment;

/**
 * Shape interface for adjacency (node+edge) graph structures.
 * Backs EntityGraph, HebbianGraphMemory, HyperEntityGraph, etc. Concrete graphs own their
 * segment layout on top of the {@link AbstractGraphMemory} kernel substrate; the bundled
 * {@link AdjacencyListGraphMemory} is a linked-list reference implementation.
 *
 * @param <L> the memory layout type
 */
public interface GraphMemory<L extends RegionLayout> extends Memory<L> {
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
