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
import java.util.function.IntSupplier;

/**
 * Sequential emitter — each scan runs immediately on the caller thread.
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
    public void emitSlabScan(int partitionSeq, MemoryType type, FixedEngramLayout layout,
                             IntSupplier visibleCount, long baseOffset) {
        results.addAll(scoreFunc.score(partitionSeq, type, layout, visibleCount.getAsInt(),
                baseOffset, queryVector, options, nowMs));
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
