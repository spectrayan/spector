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

/**
 * Read-only snapshot of structural health, occupancy, and fragmentation metrics for graph components (MR-08).
 *
 * @param structureName            human-readable identifier (e.g. "hebbian-csr", "entity-adjacency", "coactivation-table")
 * @param allocatedBytes          total bytes allocated for this structure
 * @param liveBytes               bytes actively holding live edges/entries
 * @param fragmentationRatio       fragmentation fraction: 1.0 - (liveBytes / (float) allocatedBytes)
 * @param hashLoadFactor           load factor if open-addressing/hash table, or Float.NaN otherwise
 * @param probeLength_p99          99th percentile probe length for open-addressing tables, or 0
 * @param csrOverflowOccupancy     overflow occupancy fraction for CSR structures, or Float.NaN
 * @param lastCompactionEpochMs    timestamp of the last compaction run in epoch milliseconds
 * @param bytesReclaimedLastCycle  bytes reclaimed during the last compaction pass
 */
public record GraphStructureHealthSnapshot(
        String structureName,
        long allocatedBytes,
        long liveBytes,
        float fragmentationRatio,
        float hashLoadFactor,
        int probeLength_p99,
        float csrOverflowOccupancy,
        long lastCompactionEpochMs,
        long bytesReclaimedLastCycle
) {
    public GraphStructureHealthSnapshot {
        if (structureName == null || structureName.isBlank()) {
            throw new IllegalArgumentException("structureName must not be blank");
        }
    }
}
