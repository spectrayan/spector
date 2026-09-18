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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A thread-safe in-memory index for temporal range queries using a ConcurrentNavigableMap.
 * 
 * Biological analog: Functioning like episodic memory timelines in the prefrontal cortex,
 * this index temporally orders facts, allowing the agent to retrieve facts valid at a specific
 * moment in time or during a specific timeframe.
 */
public class ValidTimeIndex {

    private final ConcurrentNavigableMap<Long, List<Long>> index;
    private final AtomicInteger totalFactsIndexed;

    /**
     * Creates an empty valid time index.
     */
    public ValidTimeIndex() {
        this.index = new ConcurrentSkipListMap<>();
        this.totalFactsIndexed = new AtomicInteger(0);
    }

    /**
     * Adds a fact offset at the given validFrom epoch millis.
     * 
     * @param validFromMs The start of validity timeframe in milliseconds
     * @param factOffset The byte offset of the fact in the append memory log
     */
    public void add(long validFromMs, long factOffset) {
        index.computeIfAbsent(validFromMs, k -> new CopyOnWriteArrayList<>()).add(factOffset);
        totalFactsIndexed.incrementAndGet();
    }

    /**
     * Returns all fact offsets where validFrom &lt;= instantMs.
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
     * Returns all fact offsets where validFrom &lt; toMs.
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
