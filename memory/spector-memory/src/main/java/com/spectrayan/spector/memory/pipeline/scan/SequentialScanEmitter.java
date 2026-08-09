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
package com.spectrayan.spector.memory.pipeline.scan;

import com.spectrayan.spector.memory.cortex.SemanticRecallStrategy;
import com.spectrayan.spector.memory.kernel.layout.CognitiveRecordLayout;
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
    private final RecallOptions options;
    private final long nowMs;
    private final SlabScoreFunction scoreFunc;
    private final SemanticRecallStrategy semanticRecallStrategy;

    public SequentialScanEmitter(List<CognitiveResult> results,
                                 float[] queryVector, RecallOptions options, long nowMs,
                                 SlabScoreFunction scoreFunc,
                                 SemanticRecallStrategy semanticRecallStrategy) {
        this.results = results;
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
        results.addAll(scoreFunc.score(segment.get(), visibleCount.getAsInt(), layout,
                queryVector, options, nowMs, type, baseOffset, partitionSeq));
    }

    @Override
    public void emitSemanticHnsw() {
        if (semanticRecallStrategy != null) {
            results.addAll(semanticRecallStrategy.recall(queryVector, options, nowMs));
        }
    }
}
