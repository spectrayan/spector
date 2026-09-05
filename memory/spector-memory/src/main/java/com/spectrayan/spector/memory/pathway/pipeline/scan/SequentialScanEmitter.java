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

import com.spectrayan.spector.memory.cortex.EpisodicMemory;
import com.spectrayan.spector.memory.cortex.SemanticRecallStrategy;
import com.spectrayan.spector.memory.kernel.layout.FixedEngramLayout;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.RecallOptions;
import java.lang.foreign.MemorySegment;
import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Sequential emitter — each scan runs immediately (no {@code madvise}), matching the fallback path.
 */
public final class SequentialScanEmitter implements ScanEmitter {
    private final List<CognitiveResult> results;
    private final float[] queryVector;
    private final String rawQuery;
    private final RecallOptions options;
    private final long nowMs;
    private final SlabScoreFunction scoreFunc;
    private final EpisodicScoreFunction episodicScoreFunc;
    private final SemanticRecallStrategy semanticRecallStrategy;

    public SequentialScanEmitter(List<CognitiveResult> results,
                                 float[] queryVector, String rawQuery, RecallOptions options, long nowMs,
                                 SlabScoreFunction scoreFunc,
                                 EpisodicScoreFunction episodicScoreFunc,
                                 SemanticRecallStrategy semanticRecallStrategy) {
        this.results = results;
        this.queryVector = queryVector;
        this.rawQuery = rawQuery;
        this.options = options;
        this.nowMs = nowMs;
        this.scoreFunc = scoreFunc;
        this.episodicScoreFunc = episodicScoreFunc;
        this.semanticRecallStrategy = semanticRecallStrategy;
    }

    public SequentialScanEmitter(List<CognitiveResult> results,
                                 float[] queryVector, RecallOptions options, long nowMs,
                                 SlabScoreFunction scoreFunc,
                                 SemanticRecallStrategy semanticRecallStrategy) {
        this(results, queryVector, null, options, nowMs, scoreFunc, null, semanticRecallStrategy);
    }

    @Override
    public void emitSlabScan(Supplier<MemorySegment> segment, IntSupplier visibleCount,
                             FixedEngramLayout layout, MemoryType type,
                             long baseOffset, int partitionSeq) {
        results.addAll(scoreFunc.score(segment.get(), visibleCount.getAsInt(), layout,
                queryVector, options, nowMs, type, baseOffset, partitionSeq));
    }

    @Override
    public void emitSemanticHnsw() {
        if (semanticRecallStrategy != null) {
            results.addAll(semanticRecallStrategy.recall(queryVector, options, nowMs));
        }
    }

    @Override
    public void emitEpisodicScan(EpisodicMemory episodic, int partitionSeq) {
        if (episodicScoreFunc != null) {
            results.addAll(episodicScoreFunc.score(episodic, partitionSeq, queryVector, rawQuery, options, nowMs));
        }
    }
}
