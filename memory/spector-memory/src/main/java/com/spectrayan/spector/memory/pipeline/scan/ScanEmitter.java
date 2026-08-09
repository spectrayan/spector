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

    /** Emits the semantic HNSW fast-path recall (active single partition only). */
    void emitSemanticHnsw();
}
