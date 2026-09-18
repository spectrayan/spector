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
package com.spectrayan.spector.memory.graph.temporal.index;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A thread-safe in-memory index mapping entity IDs to lists of fact-log byte offsets.
 * 
 * Biological analog: Similar to the hippocampus rapidly binding neocortical memory traces,
 * this index provides quick associative access to all known facts regarding a specific subject entity,
 * acting as a fast retrieval path for entity-centric recall.
 */
public class SubjectIndex {
    
    private final Map<Integer, List<Long>> index;
    private final AtomicInteger totalFactsIndexed;
    
    /**
     * Creates an empty subject index.
     */
    public SubjectIndex() {
        this.index = new ConcurrentHashMap<>();
        this.totalFactsIndexed = new AtomicInteger(0);
    }
    
    /**
     * Adds a fact offset for the given entity.
     * 
     * @param entityId The subject entity ID
     * @param factOffset The byte offset of the fact in the append memory log
     */
    public void add(int entityId, long factOffset) {
        index.computeIfAbsent(entityId, k -> new CopyOnWriteArrayList<>()).add(factOffset);
        totalFactsIndexed.incrementAndGet();
    }
    
    /**
     * Returns all fact offsets for that entity.
     * 
     * @param entityId The subject entity ID
     * @return A thread-safe list of fact offsets, or an empty list if none exist
     */
    public List<Long> offsetsFor(int entityId) {
        List<Long> list = index.get(entityId);
        return list != null ? list : Collections.emptyList();
    }
    
    /**
     * @return Number of indexed entities
     */
    public int entityCount() {
        return index.size();
    }
    
    /**
     * @return Total number of indexed fact offsets
     */
    public int totalFacts() {
        return totalFactsIndexed.get();
    }
    
    /**
     * Clears all entries from the index.
     */
    public void clear() {
        index.clear();
        totalFactsIndexed.set(0);
    }
}
