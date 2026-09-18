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
package com.spectrayan.spector.memory.pathway.pipeline.scan;

import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.kernel.layout.FixedEngramLayout;
import com.spectrayan.spector.kernel.store.EpisodicMemory;
import com.spectrayan.spector.memory.cortex.SemanticRecallStrategy;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.RecallOptions;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.IntSupplier;

/**
 * Parallel emitter — each tier scan becomes a {@link Callable}.
 */
public final class ParallelScanEmitter implements ScanEmitter {
    private final List<Callable<List<CognitiveResult>>> tasks;
    private final float[] queryVector;
    private final String rawQuery;
    private final RecallOptions options;
    private final long nowMs;
    private final SlabScoreFunction scoreFunc;
    private final EpisodicScoreFunction episodicScoreFunc;
    private final SemanticRecallStrategy semanticRecallStrategy;

    public ParallelScanEmitter(List<Callable<List<CognitiveResult>>> tasks,
                               float[] queryVector, String rawQuery, RecallOptions options, long nowMs,
                               SlabScoreFunction scoreFunc,
                               EpisodicScoreFunction episodicScoreFunc,
                               SemanticRecallStrategy semanticRecallStrategy) {
        this.tasks = tasks;
        this.queryVector = queryVector;
        this.rawQuery = rawQuery;
        this.options = options;
        this.nowMs = nowMs;
        this.scoreFunc = scoreFunc;
        this.episodicScoreFunc = episodicScoreFunc;
        this.semanticRecallStrategy = semanticRecallStrategy;
    }

    public ParallelScanEmitter(List<Callable<List<CognitiveResult>>> tasks,
                               float[] queryVector, RecallOptions options, long nowMs,
                               SlabScoreFunction scoreFunc,
                               SemanticRecallStrategy semanticRecallStrategy) {
        this(tasks, queryVector, null, options, nowMs, scoreFunc, null, semanticRecallStrategy);
    }

    @Override
    public void emitSlabScan(int partitionSeq, MemoryType type, FixedEngramLayout layout,
                             IntSupplier visibleCount, long baseOffset) {
        tasks.add(() -> scoreFunc.score(partitionSeq, type, layout, visibleCount.getAsInt(),
                baseOffset, queryVector, options, nowMs));
    }

    @Override
    public void emitSemanticHnsw() {
        if (semanticRecallStrategy != null) {
            tasks.add(() -> semanticRecallStrategy.recall(queryVector, options, nowMs));
        }
    }

    @Override
    public void emitEpisodicScan(EpisodicMemory episodic, int partitionSeq) {
        if (episodicScoreFunc != null) {
            tasks.add(() -> episodicScoreFunc.score(episodic, partitionSeq, queryVector, rawQuery, options, nowMs));
        }
    }
}
