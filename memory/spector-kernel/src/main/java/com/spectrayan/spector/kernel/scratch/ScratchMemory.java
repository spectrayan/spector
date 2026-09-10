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
package com.spectrayan.spector.kernel.scratch;

/**
 * Kernel scratch memory manager for a single cognitive namespace (R8.1, R8.5, R16.10).
 *
 * <p>Provides typed off-heap data structures for intermediate computations, reranking caches,
 * and temporary scratch buffers. Scratch memory is attributable per-namespace and is guaranteed
 * to be fully reclaimed when the namespace kernel closes (R8.4).</p>
 */
public interface ScratchMemory extends AutoCloseable {

    /**
     * Allocates or returns a managed {@link TokenVectorTable} for token-level embeddings.
     *
     * @param maxEntries maximum document entries before LRU eviction
     * @param maxTokens maximum tokens per document
     * @param dims token vector dimension
     * @return typed off-heap token vector table
     */
    TokenVectorTable tokenTable(int maxEntries, int maxTokens, int dims);

    /**
     * Allocates a contiguous off-heap float scratch buffer.
     *
     * @param capacity number of float elements
     * @return typed off-heap float scratch buffer
     */
    FloatScratch floats(int capacity);

    /**
     * Total off-heap bytes currently allocated across all scratch structures in this namespace.
     *
     * @return current allocated bytes for namespace quota and accounting (R8.5)
     */
    long allocatedBytes();

    /**
     * Releases all scratch buffers and tables allocated within this namespace (R8.4).
     */
    void releaseAll();

    @Override
    default void close() {
        releaseAll();
    }
}
