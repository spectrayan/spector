/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-memory/LICENSE
 *
 * Change Date: May 27, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.memory.bootstrap;

import com.spectrayan.spector.index.BM25Index;
import com.spectrayan.spector.memory.SpectorMemoryBuilder;
import com.spectrayan.spector.memory.cortex.MemoryBM25Index;
import com.spectrayan.spector.memory.cortex.MemorySpladeIndex;
import com.spectrayan.spector.memory.cortex.TextBlobMemory;
import com.spectrayan.spector.memory.cortex.index.MemoryIndex;
import com.spectrayan.spector.memory.kernel.StorageLayout;
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

        //  BM25 Text Search 
        MemoryBM25Index bm25Index;
        TextBlobMemory textDataStore = cortex.textStore();
        if (isDisk && basePath != null && resolvedPartitionDir != null && textDataStore != null) {
            textDataStore.readAll();
            index.setTextDataStore(textDataStore);

            // V4 bundle path: load from BM25 region
            BM25Index loadedBm25 = null;
            if (cortex.useBundleMode() && cortex.runtimeBundle() != null) {
                loadedBm25 = MemoryBM25Index.loadFromBundle(cortex.runtimeBundle());
                if (loadedBm25 != null) {
                    log.info("BM25 loaded from bundle region: {} docs", loadedBm25.size());
                }
            }

            bm25Index = new MemoryBM25Index(1);
            if (loadedBm25 != null) {
                bm25Index.setPartition(0, loadedBm25);
            } else {
                Map<String, String> allTexts = new java.util.HashMap<>();
                for (var entry : index.locationMap().entrySet()) {
                    String text = index.text(entry.getKey());
                    if (text != null && !text.isEmpty()) {
                        allTexts.put(entry.getKey(), text);
                    }
                }
                if (!allTexts.isEmpty()) {
                    bm25Index.rebuildPartition(0, allTexts);
                    log.info("Rebuilt BM25 index with {} documents from memory index", allTexts.size());
                    // Save to bundle region (V4)
                    if (cortex.useBundleMode() && cortex.runtimeBundle() != null) {
                        bm25Index.persistToBundle(cortex.runtimeBundle(), null);
                    }
                }
            }
        } else {
            bm25Index = new MemoryBM25Index(1);
            textDataStore = null;
        }

        //  SPLADE Index 
        MemorySpladeIndex memorySpladeIndex = null;
        if (builder.SparseEmbeddingProvider() != null) {
            memorySpladeIndex = new MemorySpladeIndex(1);
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
