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

import com.spectrayan.spector.memory.kernel.layout.CognitiveRecordLayout;
import com.spectrayan.spector.memory.model.MemoryType;

import java.lang.foreign.MemorySegment;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Turns a strategy's per-tier scan decision into actual work: either a deferred
 * parallel task or an immediate synchronous scan.
 */
public interface ScanEmitter {
    /** Emits a full-record slab scan of the given store slice. */
    void emitSlabScan(Supplier<MemorySegment> segment, IntSupplier visibleCount,
                      CognitiveRecordLayout layout, MemoryType type,
                      long baseOffset, int partitionSeq);

    /** Emits the semantic HNSW fast-path recall across all partitions (ADR-0009). */
    void emitSemanticHnsw();
}
