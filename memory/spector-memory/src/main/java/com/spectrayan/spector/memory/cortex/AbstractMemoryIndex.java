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
package com.spectrayan.spector.memory.cortex;

import com.spectrayan.spector.memory.index.IndexContext;
import com.spectrayan.spector.memory.index.IndexKind;
import com.spectrayan.spector.memory.index.IndexStats;
import com.spectrayan.spector.memory.index.ManagedIndex;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Common base infrastructure for per-partition derived retrieval indexes in Spector Memory (ADR-0082, Issue #428).
 *
 * <p>Manages multi-partition instances (e.g. {@code BM25Index}, {@code SpladeIndex}) across memory partitions,
 * providing safe concurrent reads via {@link CopyOnWriteArrayList}, dynamic partition allocation,
 * and unified {@link ManagedIndex} lifecycle orchestration.</p>
 *
 * @param <P> the partition-level index type (must be AutoCloseable)
 * @since 1.1.0
 */
public abstract class AbstractMemoryIndex<P extends AutoCloseable> implements ManagedIndex {

    private static final Logger log = LoggerFactory.getLogger(AbstractMemoryIndex.class);

    protected final String name;
    protected final Set<String> dependencies;
    protected final CopyOnWriteArrayList<P> partitions;
    protected volatile IndexContext context;
    protected volatile long generation = 0L;
    protected volatile long lastHydrateMs = 0L;

    protected AbstractMemoryIndex(String name, Set<String> dependencies) {
        this.name = Objects.requireNonNull(name, "name");
        this.dependencies = dependencies != null ? Set.copyOf(dependencies) : Set.of("MemoryIndex");
        this.partitions = new CopyOnWriteArrayList<>();
    }

    protected AbstractMemoryIndex(String name, Set<String> dependencies, int partitionCount) {
        this(name, dependencies);
        for (int i = 0; i < partitionCount; i++) {
            partitions.add(createPartitionIndex());
        }
    }

    /**
     * Factory method to create a new, empty partition-level index instance.
     *
     * @return a new partition index instance
     */
    protected abstract P createPartitionIndex();

    /**
     * Returns the total count of documents indexed across all partitions.
     *
     * @return total document count
     */
    public abstract int totalDocuments();

    // ── Partition Management ──

    /**
     * Returns the number of partitions currently maintained.
     */
    public int partitionCount() {
        return partitions.size();
    }

    /**
     * Returns the partition-level index for the specified partition.
     *
     * @param partitionIndex index of the partition
     * @return partition index instance
     */
    public P partition(int partitionIndex) {
        return partitions.get(partitionIndex);
    }

    /**
     * Replaces or sets the partition-level index at the specified partition index.
     *
     * @param partitionIndex index of the partition
     * @param index partition index instance
     */
    public void setPartition(int partitionIndex, P index) {
        ensurePartition(partitionIndex);
        partitions.set(partitionIndex, index);
    }

    /**
     * Appends a new partition created via {@link #createPartitionIndex()}.
     *
     * @return the 0-based index of the new partition
     */
    public int addPartition() {
        P newIndex = createPartitionIndex();
        partitions.add(newIndex);
        int idx = partitions.size() - 1;
        log.debug("Added {} partition {}", name, idx);
        return idx;
    }

    /**
     * Appends a pre-constructed partition index.
     *
     * @param index the partition index instance to add
     * @return the 0-based index of the new partition
     */
    public int addPartition(P index) {
        partitions.add(Objects.requireNonNull(index, "index"));
        int idx = partitions.size() - 1;
        log.debug("Added {} partition {}", name, idx);
        return idx;
    }

    /**
     * Ensures partitions exist up to the specified partition index.
     *
     * @param partitionIndex target partition index
     */
    protected void ensurePartition(int partitionIndex) {
        while (partitions.size() <= partitionIndex) {
            partitions.add(createPartitionIndex());
        }
    }

    // ── ManagedIndex Lifecycle ──

    @Override
    public String name() {
        return name;
    }

    @Override
    public IndexKind kind() {
        return IndexKind.DERIVED_EXPENSIVE;
    }

    @Override
    public Set<String> dependsOn() {
        return dependencies;
    }

    @Override
    public void attach(IndexContext context) {
        this.context = context;
    }

    @Override
    public IndexStats stats() {
        return new IndexStats(heapBytes(), offHeapBytes(), totalDocuments(), generation, lastHydrateMs);
    }

    /**
     * Computes a deterministic composite generation hash (ADR-0082):
     * hash(MemoryIndex.HWM, Model/Tokenizer ID, SchemaVersion).
     *
     * @param baseHwm high-water mark or document count from primary memory
     * @param modelOrTokenizerId model or tokenizer identifier
     * @param schemaVersion binary schema version
     * @return composite 64-bit generation number
     */
    public static long computeGeneration(long baseHwm, String modelOrTokenizerId, int schemaVersion) {
        long h = 1125899906842624L;
        h = 31 * h + baseHwm;
        h = 31 * h + (modelOrTokenizerId != null ? modelOrTokenizerId.hashCode() : 0);
        h = 31 * h + schemaVersion;
        return h & 0x7FFFFFFFFFFFFFFFL;
    }

    /**
     * Approximate on-heap memory consumed by this index in bytes.
     */
    public long heapBytes() {
        return (long) totalDocuments() * 128L;
    }

    /**
     * Approximate off-heap / mmap memory consumed by this index in bytes.
     */
    public long offHeapBytes() {
        return 0L;
    }

    @Override
    public void close() {
        for (P idx : partitions) {
            try {
                idx.close();
            } catch (Exception e) {
                log.warn("Error closing partition index in {}: {}", name, e.getMessage());
            }
        }
        partitions.clear();
    }
}
