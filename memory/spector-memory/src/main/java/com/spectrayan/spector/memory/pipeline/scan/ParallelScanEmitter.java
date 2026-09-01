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
package com.spectrayan.spector.memory.pipeline.scan;

import com.spectrayan.spector.commons.concurrent.NativeOsMemory;
import com.spectrayan.spector.memory.cortex.SemanticRecallStrategy;
import com.spectrayan.spector.memory.kernel.layout.CognitiveRecordLayout;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.RecallOptions;
import java.lang.foreign.MemorySegment;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Parallel emitter — each scan becomes an {@code madvise}-wrapped {@link Callable}.
 */
public final class ParallelScanEmitter implements ScanEmitter {
    private final List<Callable<List<CognitiveResult>>> tasks;
    private final float[] queryVector;
    private final RecallOptions options;
    private final long nowMs;
    private final SlabScoreFunction scoreFunc;
    private final SemanticRecallStrategy semanticRecallStrategy;

    public ParallelScanEmitter(List<Callable<List<CognitiveResult>>> tasks,
                               float[] queryVector, RecallOptions options, long nowMs,
                               SlabScoreFunction scoreFunc,
                               SemanticRecallStrategy semanticRecallStrategy) {
        this.tasks = tasks;
        this.queryVector = queryVector;
        this.options = options;
        this.nowMs = nowMs;
        this.scoreFunc = scoreFunc;
        this.semanticRecallStrategy = semanticRecallStrategy;
    }

    @Override
    public void emitSlabScan(Supplier<MemorySegment> segment, IntSupplier visibleCount,
                             CognitiveRecordLayout layout, MemoryType type,
                             long baseOffset, int partitionSeq) {
        tasks.add(() -> {
            MemorySegment seg = segment.get();
            NativeOsMemory.advise(seg, NativeOsMemory.MADV_SEQUENTIAL);
            try {
                return scoreFunc.score(seg, visibleCount.getAsInt(), layout,
                        queryVector, options, nowMs, type, baseOffset, partitionSeq);
            } finally {
                NativeOsMemory.advise(seg, NativeOsMemory.MADV_NORMAL);
            }
        });
    }

    @Override
    public void emitSemanticHnsw() {
        if (semanticRecallStrategy != null) {
            tasks.add(() -> semanticRecallStrategy.recall(queryVector, options, nowMs));
        }
    }
}
