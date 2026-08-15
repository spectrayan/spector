/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.spectrayan.spector.connector.sink;

import java.nio.file.Path;

/**
 * Lightweight metadata-only handle for a namespace workspace.
 *
 * <p>Used in the L2 warm tier of {@link TenantMemoryRegistry}. When a
 * hot {@link LeasedMemory} is evicted from the hot pool, its metadata
 * is preserved as a {@code NamespaceHandle} in the warm pool instead
 * of being fully discarded.</p>
 *
 * <h3>Tier Architecture</h3>
 * <pre>
 *   ┌─────────────────────────────────────────┐
 *   │  HOT POOL (LeasedMemory)                │  mmap'd, Arena open
 *   │  Cap: maxActive (default 10,000)        │  Ready for queries
 *   ├─────────────────────────────────────────┤
 *   │  WARM POOL (NamespaceHandle)            │  Metadata only, ~1KB each
 *   │  Cap: warmPoolCap (proportional to heap)│  Fast promotion: 5-50ms
 *   ├─────────────────────────────────────────┤
 *   │  COLD (filesystem only)                 │  Full load: 50-500ms
 *   │  No in-memory handle                    │  Requires full index rebuild
 *   └─────────────────────────────────────────┘
 * </pre>
 *
 * <h3>Memory Footprint</h3>
 * <p>Each handle is ~200-500 bytes (Path + Strings + longs). At 100K
 * handles, the warm pool uses ~50MB of heap — negligible compared to
 * the hot pool's mmap regions.</p>
 *
 * @param compoundKey  the hot pool key ({@code tenantId:namespaceId})
 * @param directory    the resolved double-sharded filesystem path
 * @param dimensions   embedding dimensions for re-creating the memory instance
 * @param lastAccessMs timestamp of last access (for warm-pool LRU ordering)
 * @param memoryCount  cached count of memories (for metrics without re-loading)
 */
public record NamespaceHandle(
        String compoundKey,
        Path directory,
        int dimensions,
        long lastAccessMs,
        long memoryCount
) {

    /**
     * Creates a handle from an evicted hot pool entry.
     *
     * @param compoundKey the pool key
     * @param directory   the namespace directory
     * @param dimensions  embedding dimensions
     * @param memoryCount cached memory count at eviction time
     * @return a new namespace handle
     */
    public static NamespaceHandle fromEviction(String compoundKey, Path directory,
                                                int dimensions, long memoryCount) {
        return new NamespaceHandle(compoundKey, directory, dimensions,
                System.currentTimeMillis(), memoryCount);
    }
}
