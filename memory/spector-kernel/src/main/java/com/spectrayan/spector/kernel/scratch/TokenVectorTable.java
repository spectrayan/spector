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
 * Off-heap storage table for per-token vector embeddings (R8.1, R8.3).
 *
 * <p>Provides typed insertion, retrieval, and LRU eviction for multi-vector models (such as
 * ColBERT) while keeping all Panama {@code Arena} and {@code MemorySegment} references
 * strictly contained inside the kernel.</p>
 */
public interface TokenVectorTable extends AutoCloseable {

    /**
     * Stores the token vectors for the specified document ID.
     *
     * @param docId document identifier
     * @param tokenVectors array of per-token embedding vectors (tokens x dims)
     */
    void put(String docId, float[][] tokenVectors);

    /**
     * Retrieves token vectors into a pre-allocated destination array.
     *
     * @param docId document identifier
     * @param dest caller-provided destination array (tokens x dims)
     * @return true if the document was present and copied, false otherwise
     */
    boolean get(String docId, float[][] dest);

    /**
     * Retrieves token vectors for the specified document ID into a newly allocated array.
     *
     * @param docId document identifier
     * @return token vectors (tokens x dims) or {@code null} if missing
     */
    float[][] get(String docId);

    /**
     * Returns the number of cached documents.
     *
     * @return current entry count
     */
    int size();

    /**
     * Maximum number of documents this table caches.
     *
     * @return maximum entry capacity
     */
    int maxEntries();

    /**
     * Maximum number of tokens permitted per document.
     *
     * @return maximum token count
     */
    int maxTokens();

    /**
     * Vector dimensionality of each token.
     *
     * @return embedding dimensions
     */
    int dims();

    /**
     * Evicts the least-recently accessed entry from the table.
     */
    void evictOldest();

    /**
     * Clears all cached entries and releases their underlying off-heap memory.
     */
    void clear();

    /**
     * Memory footprint currently allocated by this table in bytes.
     *
     * @return total allocated bytes
     */
    long allocatedBytes();

    /**
     * Closes this table and releases all held off-heap memory.
     */
    @Override
    void close();
}
