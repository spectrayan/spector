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
package com.spectrayan.spector.commons.concurrent;

/**
 * Classification of a unit of work into concurrency execution planes.
 *
 * <p>Chosen by the memory library at the call site and mapped to an appropriate executor by the host environment.</p>
 */
public enum ThreadPlane {

    /**
     * Blocking I/O or event dispatch. Host should provide virtual-thread dispatch (unbounded or lightly bounded).
     *
     * <p>Examples: LLM HTTP inference calls, webhooks, SSE event dispatch, recall listeners, external connector sync.</p>
     */
    VIRTUAL,

    /**
     * CPU-bound work that does not take an exclusive slab or graph write lock.
     * Host should provide a bounded platform thread pool sized to available processors (typically min(4, N_CPU) or N_CPU - 1).
     *
     * <p>Examples: TF-IDF scoring, quantization, vector encoding, BM25 partition scan internals.</p>
     */
    PLATFORM_SHARED,

    /**
     * Exclusive mutation of a {@code StampedLock}-guarded off-heap structure, or {@code MemorySegment.force()} / remap / WAL truncate.
     * Host should provide a single-thread (or 1-per-namespace) platform executor.
     * Parallelism greater than 1 on the same structure is an architectural defect, not a tuning knob.
     *
     * <p>Examples: EntityDirectory linkage, HyperEntityGraph mutations, CSR compaction, EagerConsolidator CADP writes, checkpoint force().</p>
     */
    PLATFORM_WRITER
}
