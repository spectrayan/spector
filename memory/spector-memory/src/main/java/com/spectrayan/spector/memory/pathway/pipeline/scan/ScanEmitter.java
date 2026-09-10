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
package com.spectrayan.spector.memory.pathway.pipeline.scan;

import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.kernel.layout.FixedEngramLayout;
import com.spectrayan.spector.kernel.store.EpisodicMemory;

import java.util.function.IntSupplier;

/**
 * Turns a strategy's per-tier scan decision into actual work: either a deferred
 * parallel task or an immediate synchronous scan.
 */
public interface ScanEmitter {
    /** Emits a full-record slab scan of the given store slice without exposing MemorySegment. */
    void emitSlabScan(int partitionSeq, MemoryType type, FixedEngramLayout layout,
                      IntSupplier visibleCount, long baseOffset);

    /** Emits the semantic HNSW fast-path recall across all partitions (ADR-0009). */
    void emitSemanticHnsw();

    /** Emits an episodic log scan of the given store. */
    void emitEpisodicScan(EpisodicMemory episodic, int partitionSeq);
}
