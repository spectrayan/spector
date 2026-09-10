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

/**
 * Shape interface for sequential linked prev/next structures.
 * Backs TemporalChain, list-like temporal memories, etc.
 *
 * @param <L> the memory layout type
 */
public interface ChainMemory<L extends RegionLayout> extends Memory<L> {
    /**
     * Links a node to a successor in the chain.
     * @param nodeId the node to link from
     * @param nextId the successor node
     */
    void link(int nodeId, int nextId);
    
    /**
     * Returns the successor of the given node.
     * @param nodeId the node to query
     * @return the next node ID, or -1 if none
     */
    int next(int nodeId);
    
    /**
     * Returns the predecessor of the given node.
     * @param nodeId the node to query
     * @return the previous node ID, or -1 if none
     */
    int prev(int nodeId);
    
    /**
     * The first node in the chain, or -1 if empty.
     * @return head node ID
     */
    int head();
    
    /**
     * The last node in the chain, or -1 if empty.
     * @return tail node ID
     */
    int tail();
    
    /**
     * Total number of linked nodes.
     * @return chain length
     */
    int chainLength();
}
