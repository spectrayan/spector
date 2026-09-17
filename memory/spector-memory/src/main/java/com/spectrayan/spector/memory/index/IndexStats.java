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
package com.spectrayan.spector.memory.index;

/**
 * Runtime sizing and telemetry snapshot for a managed index (ADR-0082).
 *
 * @param heapBytes Estimated on-heap memory footprint in bytes.
 * @param offHeapBytes Off-heap memory-mapped footprint in bytes.
 * @param entries Total number of items/postings indexed.
 * @param generation Generation or content version stamp.
 * @param hydrateMs Time in milliseconds taken during last hydration.
 *
 * @since 1.1.0
 */
public record IndexStats(
        long heapBytes,
        long offHeapBytes,
        long entries,
        long generation,
        long hydrateMs
) {
    public static IndexStats empty() {
        return new IndexStats(0L, 0L, 0L, 0L, 0L);
    }
}
