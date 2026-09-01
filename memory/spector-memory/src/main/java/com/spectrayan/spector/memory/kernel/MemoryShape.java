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
package com.spectrayan.spector.memory.kernel;

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

/**
 * Defines the structural shape of a persistent memory.
 * Each shape provides a different fundamental layout and access pattern for data.
 */
public enum MemoryShape {
    /**
     * Backs a flat, contiguous array of fixed-size records.
     * Ideal for simple tabular data or highly structured uniform collections.
     */
    RECORD,

    /**
     * Backs a partitioned array of fixed-size records, physically or logically divided.
     * Useful for time-series, log-structured merges, or highly concurrent partitioned data.
     *
     * <p><b>Retained for on-disk compatibility.</b> This constant has no live code
     * references (the {@code PartitionedRecordMemory} abstraction was removed as dead
     * code under #436), but {@link MemoryHeader} persists the shape by
     * {@link #ordinal()}. Removing this value would shift the ordinals of
     * {@link #GRAPH}, {@link #CHAIN}, {@link #APPEND} and {@link #REGISTRY}, breaking
     * every previously-written header. Do NOT delete or reorder without a versioned
     * on-disk migration.</p>
     */
    PARTITIONED,

    /**
     * Backs a node and edge structure.
     * Optimized for graph traversals, relationships, and property graphs.
     */
    GRAPH,

    /**
     * Backs a linked sequence of records (chains).
     * Used for hash collision resolution, linked lists, or version histories.
     */
    CHAIN,

    /**
     * Backs an append-only log of variable-length records.
     * Used for WALs (Write-Ahead Logs), event streams, or unbounded string/blob storage.
     */
    APPEND,

    /**
     * Backs a key-value registry or dictionary structure.
     * Often used for metadata, schema definitions, or index roots.
     */
    REGISTRY,

    /**
     * Backs a multi-region container that hosts multiple heterogeneous memories in a single
     * mmap file. Each region within the bundle carries its own {@link MemoryHeader} and
     * independently-typed {@link MemoryLayout}. The bundle header and region directory are
     * managed by the {@code kernel.bundle} infrastructure.
     *
     * <p>Introduced as part of ADR-0004 (V4 mmap FD scaling) to consolidate
     * per-user file descriptors from ~23 mmap files down to 2 bundle files.</p>
     *
     * @see com.spectrayan.spector.memory.kernel.bundle.BundleLayout
     */
    BUNDLE,

    /**
     * Backs a single-entry self-model container for one namespace entity.
     * Stores one variable-length JSON blob (identity, salience, persona).
     *
     * <p>Biological analog: the anterior insular cortex integrates
     * self-awareness with salience weighting into a unified self-model.</p>
     *
     * @see com.spectrayan.spector.memory.insula.InsularCortex
     */
    INSULAR,

    /**
     * Backs a compound structure of one or more open-addressing hash tables.
     * Unlike {@link #RECORD}, which stores a uniform array of fixed-stride records,
     * a hashtable memory hosts heterogeneous sub-tables (e.g., pair table + edge table)
     * with independent slot sizes behind a sub-header.
     *
     * <p>Biological analog: the synaptic co-activation tracker stores
     * tag co-occurrence counts and STDP (Spike-Timing Dependent Plasticity)
     * directed edges in two independent hash tables within a single memory region.</p>
     *
     * <p>Introduced as part of ADR-0009 (Cross-Capture Graph &amp; CoActivation Kernel
     * Integration) to give {@code CoActivationRecordMemory} an honest shape instead
     * of the {@code stride=1} hack that abused {@link #RECORD}.</p>
     *
     * @see com.spectrayan.spector.memory.kernel.shape.AbstractHashTableMemory
     */
    HASHTABLE,

    /**
     * Backs a fixed-size off-heap distributed holographic memory tensor.
     * Stores an accumulator vector T in R^Y representing the linear superposition of Positive
     * Random Features across all stored memories for constant-time O(Y) global associative resonance.
     *
     * <p>Biological analog: Pribram's holonomic brain model, where memories exist as distributed
     * interference patterns across wide-field neural ensembles.</p>
     *
     * <p>Introduced as part of ADR-0020 (Log-Sum-ReLU &amp; Positive Random Feature Associative Memory).</p>
     *
     * @see com.spectrayan.spector.memory.kernel.shape.DistributedMemoryTensor
     */
    HOLOGRAPHIC
}
