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
package com.spectrayan.spector.memory.bootstrap;

import com.spectrayan.spector.index.text.BM25Index;
import com.spectrayan.spector.memory.SpectorMemoryBuilder;
import com.spectrayan.spector.memory.cortex.MemoryBM25Index;
import com.spectrayan.spector.memory.cortex.MemorySpladeIndex;
import com.spectrayan.spector.kernel.store.TextBlobMemory;
import com.spectrayan.spector.memory.cortex.index.MemoryIndex;
import com.spectrayan.spector.kernel.storage.StoragePaths;
import com.spectrayan.spector.memory.persist.PartitionManager;
import com.spectrayan.spector.memory.pathway.pipeline.reranker.ColBERTReranker;
import com.spectrayan.spector.memory.pathway.pipeline.reranker.ColBERTTokenCache;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Assembles the lexical / sparse / late-interaction retrieval indices that back
 * the recall pipeline: the BM25 index (+ its backing text store), the optional
 * SPLADE index, and the optional ColBERT reranker.
 *
 * <p>Extracted verbatim from {@code SpectorMemoryFactory.assemble} as part of the
 * #437 god-class decomposition. This includes the side effects on the shared
 * {@link MemoryIndex} that the BM25 bootstrap performs (setting the active-partition
 * text store and rebuilding BM25 from the persisted index when no binary index is
 * present), preserved in place.</p>
 *
 * @since 1.1.0
 */
public final class RetrievalIndexBuilder {

    private static final Logger log = LoggerFactory.getLogger(RetrievalIndexBuilder.class);

    private RetrievalIndexBuilder() {}

    /** Immutable holder for the assembled retrieval indices. */
    public record RetrievalIndices(
            MemoryBM25Index bm25Index,
            TextBlobMemory textDataStore,
            MemorySpladeIndex memorySpladeIndex,
            ColBERTReranker colbertReranker
    ) {}

    public static RetrievalIndices build(SpectorMemoryBuilder builder,
                                  CognitiveCortexBuilder.CortexFoundation cortex,
                                  MemoryIndex index) {
        boolean isDisk = cortex.isDisk();
        var basePath = cortex.basePath();
        var resolvedPartitionDir = cortex.resolvedPartitionDir();

        // ── BM25 Text Search ──
        MemoryBM25Index bm25Index = new MemoryBM25Index(1);
        TextBlobMemory textDataStore = cortex.textStore();
        if (isDisk && basePath != null && resolvedPartitionDir != null && textDataStore != null) {
            textDataStore.readAll();
            index.setTextDataStore(textDataStore);
        } else {
            textDataStore = null;
        }

        // ── SPLADE Index ──
        MemorySpladeIndex memorySpladeIndex = null;
        if (builder.SparseEmbeddingProvider() != null) {
            memorySpladeIndex = new MemorySpladeIndex(1, builder.SparseEmbeddingProvider());
            log.info("SPLADE index enabled: provider={}", builder.SparseEmbeddingProvider().modelName());
        }

        //  ColBERT Reranker 
        ColBERTReranker colbertReranker = null;
        if (builder.tokenEmbeddingProvider() != null) {
            ColBERTTokenCache tokenCache = new ColBERTTokenCache(
                    builder.tokenEmbeddingProvider().tokenDimensions(), 10_000);
            colbertReranker = new ColBERTReranker(builder.tokenEmbeddingProvider(), tokenCache);
            log.info("ColBERT reranker enabled: provider={}, tokenDims={}",
                    builder.tokenEmbeddingProvider().modelName(),
                    builder.tokenEmbeddingProvider().tokenDimensions());
        }

        return new RetrievalIndices(bm25Index, textDataStore, memorySpladeIndex, colbertReranker);
    }
}
