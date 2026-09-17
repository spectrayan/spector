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
package com.spectrayan.spector.memory.cortex;

import com.spectrayan.spector.commons.concurrent.ConcurrentExecutionException;
import com.spectrayan.spector.commons.concurrent.ConcurrentTasks;
import com.spectrayan.spector.index.ScoredResult;
import com.spectrayan.spector.index.text.SpladeIndex;
import com.spectrayan.spector.kernel.bundle.BundleManager;
import com.spectrayan.spector.kernel.bundle.RegionRef;
import com.spectrayan.spector.kernel.bundle.RuntimeBundle;
import com.spectrayan.spector.kernel.region.RegionId;

import com.spectrayan.spector.provider.embedding.SparseEmbeddingProvider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Per-partition SPLADE index manager for learned sparse retrieval.
 *
 * <h3>Architecture</h3>
 * <p>Mirrors {@link MemoryBM25Index}  --  maintains an array of {@link SpladeIndex}
 * instances, one per memory partition. On recall, searches all partitions in parallel
 * and merges results by inner-product score. On startup, each partition's SPLADE
 * index is rebuilt from stored sparse vectors.</p>
 *
 * <h3>Relationship to BM25</h3>
 * <p>SPLADE and BM25 are complementary:
 * <ul>
 *   <li><b>BM25</b>: exact keyword matching using corpus TF-IDF statistics</li>
 *   <li><b>SPLADE</b>: learned term expansion capturing synonyms and related concepts</li>
 * </ul>
 * Both produce scored results that are fused via RRF in the recall pipeline.
 * In {@link com.spectrayan.spector.config.model.TextSearchMode#FULL_STACK},
 * both indexes are searched in parallel.</p>
 *
 * <h3>Graceful Degradation</h3>
 * <p>If no {@link com.spectrayan.spector.provider.embedding.SparseEmbeddingProvider} is configured,
 * this index will be empty and all search operations return empty results.
 * The recall pipeline handles this gracefully.</p>
 *
 * <h3>Thread Safety</h3>
 * <p>Same model as {@link MemoryBM25Index}: {@link CopyOnWriteArrayList} for
 * partition list, internal locks in each {@link SpladeIndex}.</p>
 *
 * @see SpladeIndex
 * @see MemoryBM25Index
 */
public final class MemorySpladeIndex extends AbstractMemoryIndex<SpladeIndex> {

    private static final Logger log = LoggerFactory.getLogger(MemorySpladeIndex.class);

    /** Magic header for multi-partition SPLADE bundle container ("SPLM"). */
    public static final int MAGIC_MULTI = 0x53504C4D;

    private final SparseEmbeddingProvider spladeProvider;

    /**
     * A SPLADE search result with its source partition.
     *
     * @param id              memory identifier
     * @param spladeScore     the inner-product SPLADE score
     * @param partitionIndex  which partition this result came from
     */
    public record SpladeCandidate(String id, float spladeScore, int partitionIndex) {}

    /**
     * Creates an empty MemorySpladeIndex with no partitions.
     */
    public MemorySpladeIndex() {
        this(0, null);
    }

    /**
     * Creates a MemorySpladeIndex with the given number of pre-allocated empty partitions.
     *
     * @param partitionCount number of partitions to pre-allocate
     */
    public MemorySpladeIndex(int partitionCount) {
        this(partitionCount, null);
    }

    /**
     * Creates a MemorySpladeIndex with the given partition count and sparse embedding provider.
     *
     * @param partitionCount number of partitions to pre-allocate
     * @param spladeProvider provider for generating sparse embeddings
     */
    public MemorySpladeIndex(int partitionCount, SparseEmbeddingProvider spladeProvider) {
        super("SPLADE", java.util.Set.of("MemoryIndex"), partitionCount);
        this.spladeProvider = spladeProvider;
    }

    /**
     * Returns the configured sparse embedding provider, or null if none.
     */
    public SparseEmbeddingProvider provider() {
        return spladeProvider;
    }

    @Override
    protected SpladeIndex createPartitionIndex() {
        return new SpladeIndex();
    }

    /**
     * Indexes a sparse vector into a specific partition's SPLADE index.
     *
     * @param partitionIndex the target partition
     * @param id             memory identifier
     * @param sparseVec      SPLADE-encoded sparse vector: term  ->  neural weight
     */
    public void index(int partitionIndex, String id, Map<String, Float> sparseVec) {
        ensurePartition(partitionIndex);
        partitions.get(partitionIndex).indexSparse(id, sparseVec);
    }

    /**
     * Searches all partitions in parallel using a SPLADE query sparse vector.
     *
     * @param querySparse SPLADE-encoded query sparse vector
     * @param topK        maximum number of results to return
     * @return list of SPLADE candidates sorted by score descending
     */
    public List<SpladeCandidate> search(Map<String, Float> querySparse, int topK) {
        if (partitions.isEmpty()) {
            return List.of();
        }

        List<SpladeIndex> snapshot = new ArrayList<>(partitions);

        List<Callable<List<SpladeCandidate>>> tasks = new ArrayList<>(snapshot.size());
        for (int i = 0; i < snapshot.size(); i++) {
            final int partIdx = i;
            final SpladeIndex idx = snapshot.get(i);
            if (idx.size() > 0) {
                tasks.add(() -> {
                    ScoredResult[] results = idx.searchSparse(querySparse, topK);
                    List<SpladeCandidate> candidates = new ArrayList<>(results.length);
                    for (ScoredResult sr : results) {
                        candidates.add(new SpladeCandidate(sr.id(), sr.score(), partIdx));
                    }
                    return candidates;
                });
            }
        }

        if (tasks.isEmpty()) {
            return List.of();
        }

        List<SpladeCandidate> merged;
        if (tasks.size() == 1) {
            try {
                merged = tasks.getFirst().call();
            } catch (Exception e) {
                log.error("SPLADE single-partition search failed", e);
                return List.of();
            }
        } else {
            merged = new ArrayList<>();
            try {
                List<List<SpladeCandidate>> partitionResults = ConcurrentTasks.forkJoinAll(tasks);
                for (List<SpladeCandidate> pr : partitionResults) {
                    merged.addAll(pr);
                }
            } catch (ConcurrentExecutionException e) {
                log.error("Parallel SPLADE search failed, falling back to sequential", e);
                merged = searchSequential(snapshot, querySparse, topK);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("SPLADE search interrupted");
                return List.of();
            }
        }

        merged.sort(Comparator.comparingDouble(SpladeCandidate::spladeScore).reversed());
        if (merged.size() > topK) {
            return merged.subList(0, topK);
        }
        return merged;
    }

    /**
     * Rebuilds a single partition's SPLADE index from stored sparse vectors.
     *
     * @param partitionIndex the partition to rebuild
     * @param sparseVecs     map of memory ID  ->  SPLADE sparse vector
     */
    public void rebuildPartition(int partitionIndex, Map<String, Map<String, Float>> sparseVecs) {
        ensurePartition(partitionIndex);

        SpladeIndex newIndex = new SpladeIndex();
        for (Map.Entry<String, Map<String, Float>> entry : sparseVecs.entrySet()) {
            newIndex.indexSparse(entry.getKey(), entry.getValue());
        }

        partitions.set(partitionIndex, newIndex);
        log.debug("Rebuilt SPLADE for partition {} with {} documents", partitionIndex, sparseVecs.size());
    }

    @Override
    public int totalDocuments() {
        int total = 0;
        for (SpladeIndex idx : partitions) {
            total += idx.size();
        }
        return total;
    }

    /**
     * Checks if a document ID is present in any partition.
     *
     * @param id memory identifier
     * @return true if present
     */
    public boolean contains(String id) {
        if (id == null || partitions.isEmpty()) return false;
        for (SpladeIndex idx : partitions) {
            if (idx.contains(id)) return true;
        }
        return false;
    }

    /**
     * Removes a document by ID across all partitions.
     *
     * @param id document identifier
     */
    public void remove(String id) {
        for (SpladeIndex p : partitions) {
            p.remove(id);
        }
    }

    /**
     * Returns an unmodifiable set of all document IDs across all partitions.
     */
    public java.util.Set<String> docIds() {
        java.util.Set<String> all = new java.util.HashSet<>();
        for (SpladeIndex p : partitions) {
            all.addAll(p.docIds());
        }
        return all;
    }

    /**
     * Persists the active SPLADE index into a V4 {@link RuntimeBundle} with dynamic variable-slice growth.
     *
     * <p>If multiple partitions are present, persists a multi-partition container snapshot.</p>
     *
     * @param runtimeBundle the runtime bundle containing the SPLADE region
     * @return number of bytes written to the bundle region, or -1 on error
     */
    public int persistToBundle(RuntimeBundle runtimeBundle) {
        if (runtimeBundle == null || partitions.isEmpty() || totalDocuments() == 0) {
            return 0;
        }

        try {
            RegionRef spladeRef = runtimeBundle.regionRef(RegionId.SPLADE);
            if (spladeRef == null) {
                return -1;
            }

            byte[] data;
            if (partitions.size() == 1) {
                data = partition(0).toByteArray();
            } else {
                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                java.io.DataOutputStream dos = new java.io.DataOutputStream(baos);
                dos.writeInt(MAGIC_MULTI);
                dos.writeInt(SpladeIndex.FORMAT_VERSION);
                dos.writeInt(partitions.size());
                for (SpladeIndex p : partitions) {
                    byte[] pData = p.toByteArray();
                    dos.writeInt(pData.length);
                    dos.write(pData);
                }
                dos.flush();
                data = baos.toByteArray();
            }

            int totalBytes = 4 + data.length;
            if (totalBytes > spladeRef.byteSize()) {
                log.info("SPLADE index exceeded region capacity; ensuring expanded capacity");
                spladeRef.ensureCapacity(totalBytes);
            }

            var segment = spladeRef.resolve();
            segment.set(java.lang.foreign.ValueLayout.JAVA_INT, 0, data.length);
            java.lang.foreign.MemorySegment.copy(
                    java.lang.foreign.MemorySegment.ofArray(data), 0,
                    segment, 4, data.length);

            runtimeBundle.updateRegionUsedSize(RegionId.SPLADE, totalBytes);
            return totalBytes;
        } catch (Exception e) {
            log.warn("Failed to persist SPLADE index to runtime bundle: {}", e.getMessage(), e);
            return -1;
        }
    }

    /**
     * Backwards-compatible overload for persistToBundle.
     */
    public int persistToBundle(RuntimeBundle runtimeBundle, BundleManager bundleManager) {
        return persistToBundle(runtimeBundle);
    }

    /**
     * Loads a SPLADE index from a V4 {@link RuntimeBundle}.
     *
     * @param runtimeBundle the runtime bundle containing the SPLADE region
     * @return the loaded SpladeIndex, or null if no valid data is found
     */
    public static SpladeIndex loadFromBundle(RuntimeBundle runtimeBundle) {
        if (runtimeBundle == null) {
            return null;
        }
        try {
            RegionRef spladeRef = runtimeBundle.regionRef(RegionId.SPLADE);
            if (spladeRef != null) {
                var segment = spladeRef.resolve();
                if (segment != null && segment.byteSize() >= 28) {
                    int payloadLen = segment.get(java.lang.foreign.ValueLayout.JAVA_INT, 0);
                    if (payloadLen > 0 && 4 + (long) payloadLen <= segment.byteSize()) {
                        byte[] data = new byte[payloadLen];
                        java.lang.foreign.MemorySegment.copy(segment, 4,
                                java.lang.foreign.MemorySegment.ofArray(data), 0, payloadLen);
                        java.nio.ByteBuffer buf = java.nio.ByteBuffer.wrap(data);
                        int magic = buf.getInt();
                        if (magic == MAGIC_MULTI) {
                            int ver = buf.getInt();
                            int count = buf.getInt();
                            if (count > 0) {
                                int len = buf.getInt();
                                byte[] pData = new byte[len];
                                buf.get(pData);
                                return SpladeIndex.fromByteArray(pData);
                            }
                        } else if (magic == SpladeIndex.MAGIC) {
                            return SpladeIndex.fromByteArray(data);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("SPLADE load from bundle region failed: {}", e.getMessage());
        }
        return null;
    }

    @Override
    public void checkpoint() {
        if (context != null && context.runtimeBundle() != null) {
            persistToBundle(context.runtimeBundle());
        }
    }

    @Override
    public java.util.concurrent.CompletionStage<Void> hydrate() {
        long start = System.currentTimeMillis();
        String modelId = spladeProvider != null ? spladeProvider.modelName() : "splade";
        long expectedGen = computeGeneration(context != null && context.memoryIndex() != null ? context.memoryIndex().size() : 0, modelId, SpladeIndex.FORMAT_VERSION);

        boolean loadedValid = false;
        if (context != null && context.runtimeBundle() != null) {
            try {
                RegionRef spladeRef = context.runtimeBundle().regionRef(RegionId.SPLADE);
                if (spladeRef != null) {
                    var segment = spladeRef.resolve();
                    if (segment != null && segment.byteSize() >= 28) {
                        int payloadLen = segment.get(java.lang.foreign.ValueLayout.JAVA_INT, 0);
                        if (payloadLen > 0 && 4 + (long) payloadLen <= segment.byteSize()) {
                            byte[] data = new byte[payloadLen];
                            java.lang.foreign.MemorySegment.copy(segment, 4,
                                    java.lang.foreign.MemorySegment.ofArray(data), 0, payloadLen);
                            java.nio.ByteBuffer buf = java.nio.ByteBuffer.wrap(data);
                            int magic = buf.getInt();
                            if (magic == MAGIC_MULTI) {
                                int ver = buf.getInt();
                                int count = buf.getInt();
                                for (int i = 0; i < count; i++) {
                                    int len = buf.getInt();
                                    byte[] pData = new byte[len];
                                    buf.get(pData);
                                    SpladeIndex pIdx = SpladeIndex.fromByteArray(pData);
                                    if (pIdx != null) {
                                        setPartition(i, pIdx);
                                    }
                                }
                                if (!partitions.isEmpty() && partition(0).generation() == expectedGen
                                        && java.util.Objects.equals(partition(0).modelId(), modelId)) {
                                    loadedValid = true;
                                    log.info("SPLADE hydrated multi-partition from bundle ({} partitions, gen={})", count, expectedGen);
                                }
                            } else if (magic == SpladeIndex.MAGIC) {
                                SpladeIndex single = SpladeIndex.fromByteArray(data);
                                if (single != null && single.generation() == expectedGen
                                        && java.util.Objects.equals(single.modelId(), modelId)) {
                                    setPartition(0, single);
                                    loadedValid = true;
                                    log.info("SPLADE hydrated from bundle region ({} docs, gen={})", single.size(), expectedGen);
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("SPLADE load from bundle failed: {}", e.getMessage());
            }
        }

        if (!loadedValid) {
            log.info("SPLADE index not present or generation mismatched (expected gen={}), rebuilding from primary memory", expectedGen);
            rebuildInternal(expectedGen, modelId);
        }

        this.lastHydrateMs = System.currentTimeMillis() - start;
        this.generation = expectedGen;
        return java.util.concurrent.CompletableFuture.completedFuture(null);
    }

    @Override
    public java.util.concurrent.CompletionStage<Void> rebuild() {
        long start = System.currentTimeMillis();
        String modelId = spladeProvider != null ? spladeProvider.modelName() : "splade";
        long expectedGen = computeGeneration(context != null && context.memoryIndex() != null ? context.memoryIndex().size() : 0, modelId, SpladeIndex.FORMAT_VERSION);
        rebuildInternal(expectedGen, modelId);
        this.lastHydrateMs = System.currentTimeMillis() - start;
        this.generation = expectedGen;
        return java.util.concurrent.CompletableFuture.completedFuture(null);
    }

    private void rebuildInternal(long newGen, String modelId) {
        SpladeIndex newIdx = createPartitionIndex();
        newIdx.setGeneration(newGen);
        newIdx.setModelId(modelId);
        if (spladeProvider != null && context != null && context.memoryIndex() != null) {
            for (var entry : context.memoryIndex().locationMap().entrySet()) {
                String text = context.memoryIndex().text(entry.getKey());
                if (text != null && !text.isEmpty()) {
                    var res = spladeProvider.encode(text);
                    if (res != null && res.weights() != null) {
                        newIdx.indexSparse(entry.getKey(), res.weights());
                    }
                }
            }
        }
        setPartition(0, newIdx);
        if (context != null && context.runtimeBundle() != null && newIdx.size() > 0) {
            persistToBundle(context.runtimeBundle());
        }
    }

    private List<SpladeCandidate> searchSequential(List<SpladeIndex> snapshot,
                                                    Map<String, Float> querySparse, int topK) {
        List<SpladeCandidate> results = new ArrayList<>();
        for (int i = 0; i < snapshot.size(); i++) {
            SpladeIndex idx = snapshot.get(i);
            if (idx.size() > 0) {
                ScoredResult[] sr = idx.searchSparse(querySparse, topK);
                for (ScoredResult r : sr) {
                    results.add(new SpladeCandidate(r.id(), r.score(), i));
                }
            }
        }
        return results;
    }
}
