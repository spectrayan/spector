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
package com.spectrayan.spector.memory;

import com.spectrayan.spector.index.BM25Index;
import com.spectrayan.spector.index.ColBERTReranker;
import com.spectrayan.spector.index.ColBERTTokenCache;
import com.spectrayan.spector.memory.cortex.MemoryBM25Index;
import com.spectrayan.spector.memory.cortex.MemorySpladeIndex;
import com.spectrayan.spector.memory.cortex.TextAppendMemory;
import com.spectrayan.spector.memory.index.MemoryIndex;
import com.spectrayan.spector.memory.kernel.StorageLayout;

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
final class RetrievalIndexBuilder {

    private static final Logger log = LoggerFactory.getLogger(RetrievalIndexBuilder.class);

    private RetrievalIndexBuilder() {}

    /** Immutable holder for the assembled retrieval indices. */
    record RetrievalIndices(
            MemoryBM25Index bm25Index,
            TextAppendMemory textDataStore,
            MemorySpladeIndex memorySpladeIndex,
            ColBERTReranker colbertReranker
    ) {}

    static RetrievalIndices build(SpectorMemoryBuilder builder,
                                  CognitiveCortexBuilder.CortexFoundation cortex,
                                  MemoryIndex index) {
        boolean isDisk = cortex.isDisk();
        var basePath = cortex.basePath();
        var resolvedPartitionDir = cortex.resolvedPartitionDir();

        //  BM25 Text Search 
        MemoryBM25Index bm25Index;
        TextAppendMemory textDataStore = cortex.textStore();
        if (isDisk && basePath != null && resolvedPartitionDir != null && textDataStore != null) {
            textDataStore.readAll();
            index.setTextDataStore(textDataStore);

            // V4 bundle path: try loading from BM25 region first
            BM25Index loadedBm25 = null;
            boolean usedBundleRegion = false;
            if (cortex.useBundleMode() && cortex.runtimeBundle() != null) {
                try {
                    java.lang.foreign.MemorySegment bm25Region = cortex.runtimeBundle().regionSegment(
                            com.spectrayan.spector.memory.kernel.bundle.RegionId.BM25);
                    if (bm25Region != null) {
                        loadedBm25 = BM25Index.loadFromRegion(bm25Region);
                        if (loadedBm25 != null) {
                            usedBundleRegion = true;
                            log.info("BM25 loaded from bundle region: {} docs", loadedBm25.size());
                        }
                    }
                } catch (Exception e) {
                    log.debug("BM25 bundle region load failed, falling back to file: {}", e.getMessage());
                }
            }

            // V3 fallback: load from bm25.bidx file
            if (loadedBm25 == null) {
                java.nio.file.Path bm25Path = StorageLayout.bm25BidxRuntime(basePath);
                java.nio.file.Path v2Bm25 = resolvedPartitionDir != null ? resolvedPartitionDir.resolve(StorageLayout.FILE_BM25) : null;
                java.nio.file.Path loadFrom = MigrationPathResolver.getNewerPath(bm25Path, v2Bm25, null);
                if (loadFrom != null) {
                    bm25Path = loadFrom;
                }
                loadedBm25 = BM25Index.load(bm25Path);
                if (loadedBm25 != null) {
                    log.info("BM25 loaded from binary index: {} docs", loadedBm25.size());
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
                    // Save to file (V3 compat) and bundle region (V4)
                    java.nio.file.Path bm25Path = StorageLayout.bm25BidxRuntime(basePath);
                    bm25Index.partition(0).save(bm25Path);
                    if (cortex.useBundleMode() && cortex.runtimeBundle() != null) {
                        try {
                            java.lang.foreign.MemorySegment bm25Region = cortex.runtimeBundle().regionSegment(
                                    com.spectrayan.spector.memory.kernel.bundle.RegionId.BM25);
                            if (bm25Region != null) {
                                bm25Index.partition(0).saveToRegion(bm25Region);
                            }
                        } catch (Exception e) {
                            log.debug("BM25 bundle region save failed: {}", e.getMessage());
                        }
                    }
                }
            }
        } else {
            bm25Index = new MemoryBM25Index(1);
            textDataStore = null;
        }

        //  SPLADE Index 
        MemorySpladeIndex memorySpladeIndex = null;
        if (builder.SparseEmbeddingProvider != null) {
            memorySpladeIndex = new MemorySpladeIndex(1);
            log.info("SPLADE index enabled: provider={}", builder.SparseEmbeddingProvider.modelName());
        }

        //  ColBERT Reranker 
        ColBERTReranker colbertReranker = null;
        if (builder.tokenEmbeddingProvider != null) {
            ColBERTTokenCache tokenCache = new ColBERTTokenCache(
                    builder.tokenEmbeddingProvider.tokenDimensions(), 10_000);
            colbertReranker = new ColBERTReranker(builder.tokenEmbeddingProvider, tokenCache);
            log.info("ColBERT reranker enabled: provider={}, tokenDims={}",
                    builder.tokenEmbeddingProvider.modelName(),
                    builder.tokenEmbeddingProvider.tokenDimensions());
        }

        return new RetrievalIndices(bm25Index, textDataStore, memorySpladeIndex, colbertReranker);
    }
}
