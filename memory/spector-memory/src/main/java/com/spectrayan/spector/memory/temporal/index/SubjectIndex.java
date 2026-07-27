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
package com.spectrayan.spector.memory.temporal.index;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An in-memory index mapping entity IDs to lists of fact-log byte offsets.
 * 
 * Biological analog: Similar to the hippocampus rapidly binding neocortical memory traces,
 * this index provides quick associative access to all known facts regarding a specific subject entity,
 * acting as a fast retrieval path for entity-centric recall.
 * 
 * NOTE: This structure is NOT thread-safe. Callers must synchronize externally.
 */
public class SubjectIndex {
    
    private final Map<Integer, List<Long>> index;
    private int totalFactsIndexed;
    
    /**
     * Creates an empty subject index.
     */
    public SubjectIndex() {
        this.index = new HashMap<>();
        this.totalFactsIndexed = 0;
    }
    
    /**
     * Adds a fact offset for the given entity.
     * 
     * @param entityId The subject entity ID
     * @param factOffset The byte offset of the fact in the append memory log
     */
    public void add(int entityId, long factOffset) {
        index.computeIfAbsent(entityId, k -> new ArrayList<>()).add(factOffset);
        totalFactsIndexed++;
    }
    
    /**
     * Returns all fact offsets for that entity.
     * 
     * @param entityId The subject entity ID
     * @return A list of fact offsets, or an empty list if none exist
     */
    public List<Long> offsetsFor(int entityId) {
        return index.getOrDefault(entityId, new ArrayList<>());
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
        return totalFactsIndexed;
    }
    
    /**
     * Clears all entries from the index.
     */
    public void clear() {
        index.clear();
        totalFactsIndexed = 0;
    }
}
