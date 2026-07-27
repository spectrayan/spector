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
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * An in-memory index for temporal range queries using a NavigableMap.
 * 
 * Biological analog: Functioning like episodic memory timelines in the prefrontal cortex,
 * this index temporally orders facts, allowing the agent to retrieve facts valid at a specific
 * moment in time or during a specific timeframe.
 * 
 * NOTE: This structure is NOT thread-safe. Callers must synchronize externally.
 */
public class ValidTimeIndex {

    private final NavigableMap<Long, List<Long>> index;
    private int totalFactsIndexed;

    /**
     * Creates an empty valid time index.
     */
    public ValidTimeIndex() {
        this.index = new TreeMap<>();
        this.totalFactsIndexed = 0;
    }

    /**
     * Adds a fact offset at the given validFrom epoch millis.
     * 
     * @param validFromMs The start of validity timeframe in milliseconds
     * @param factOffset The byte offset of the fact in the append memory log
     */
    public void add(long validFromMs, long factOffset) {
        index.computeIfAbsent(validFromMs, k -> new ArrayList<>()).add(factOffset);
        totalFactsIndexed++;
    }

    /**
     * Returns all fact offsets where validFrom <= instantMs.
     * NOTE: This doesn't check validTo — the caller filters by validTo.
     * 
     * @param instantMs The instant in time to query
     * @return A list of fact offsets valid at or before the instant
     */
    public List<Long> factsValidAt(long instantMs) {
        List<Long> result = new ArrayList<>();
        for (List<Long> offsets : index.headMap(instantMs, true).values()) {
            result.addAll(offsets);
        }
        return result;
    }

    /**
     * Returns all fact offsets where validFrom < toMs.
     * NOTE: Caller still filters by validTo for the overlap test.
     * 
     * @param fromMs The start of the timeframe
     * @param toMs The end of the timeframe
     * @return A list of fact offsets valid during the given timeframe
     */
    public List<Long> factsValidDuring(long fromMs, long toMs) {
        List<Long> result = new ArrayList<>();
        for (List<Long> offsets : index.headMap(toMs, false).values()) {
            result.addAll(offsets);
        }
        return result;
    }

    /**
     * @return Total indexed offsets
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
