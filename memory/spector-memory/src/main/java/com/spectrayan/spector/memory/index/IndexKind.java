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
package com.spectrayan.spector.memory.index;

/**
 * Classifies indexes and graph stores managed by the Spector Index Plane (ADR-0082).
 *
 * @since 1.1.0
 */
public enum IndexKind {
    /**
     * Authoritative primary store directly backed by a memory-mapped bundle region.
     * Hydration is an immediate no-op (already mapped on bundle open).
     */
    PRIMARY,

    /**
     * Cheap derived view reconstructed from primary mmap stores (e.g. {@code memoryToEntities}
     * reverse index reconstructed via sequential adjacency scan).
     */
    DERIVED_CHEAP,

    /**
     * Expensive derived index requiring significant computation, model inference,
     * or tokenization (e.g. BM25, SPLADE inverted postings) with dedicated snapshot persistence.
     */
    DERIVED_EXPENSIVE
}
