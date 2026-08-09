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
package com.spectrayan.spector.memory.pipeline.gatherer;

import com.spectrayan.spector.memory.cortex.MemoryBM25Index;
import com.spectrayan.spector.memory.index.MemoryIndex;

/**
 * Handles candidate retrieval across vector (HNSW/SVASQ), sparse (SPLADE),
 * and keyword (BM25) search indexes.
 */
public class RecallCandidateGatherer {

    private final MemoryIndex index;
    private final MemoryBM25Index bm25Index;

    public RecallCandidateGatherer(MemoryIndex index, MemoryBM25Index bm25Index) {
        this.index = index;
        this.bm25Index = bm25Index;
    }

    public MemoryIndex index() {
        return index;
    }

    public MemoryBM25Index bm25Index() {
        return bm25Index;
    }
}
