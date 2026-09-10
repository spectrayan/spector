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
package com.spectrayan.spector.kernel.scan;

import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.kernel.layout.FixedEngramLayout;
import com.spectrayan.spector.kernel.store.StrengthMemory;

import java.lang.foreign.MemorySegment;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Sequential emitter running scans synchronously on the caller thread.
 */
public final class SequentialScanEmitter implements ScanEmitter {

    private final float[] queryVector;
    private final float[] mins;
    private final float[] scales;
    private final ScanFilter filter;
    private final StrengthMemory strengthMemory;
    private final SlabScoreFunction scoreFunc;
    private final SlotVisitor visitor;

    public SequentialScanEmitter(float[] queryVector, float[] mins, float[] scales,
                                 ScanFilter filter, StrengthMemory strengthMemory,
                                 SlabScoreFunction scoreFunc, SlotVisitor visitor) {
        this.queryVector = queryVector;
        this.mins = mins;
        this.scales = scales;
        this.filter = filter;
        this.strengthMemory = strengthMemory;
        this.scoreFunc = scoreFunc != null ? scoreFunc : SlabScanner::scan;
        this.visitor = visitor;
    }

    @Override
    public void emitSlabScan(Supplier<MemorySegment> segment, IntSupplier visibleCount,
                             FixedEngramLayout layout, MemoryType type,
                             long baseOffset, int partitionSeq) {
        scoreFunc.score(segment.get(), visibleCount.getAsInt(), layout,
                queryVector, mins, scales, filter, strengthMemory, type, baseOffset, partitionSeq, visitor);
    }
}
