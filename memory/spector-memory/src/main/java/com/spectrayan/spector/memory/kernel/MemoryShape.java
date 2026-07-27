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
    REGISTRY
}
