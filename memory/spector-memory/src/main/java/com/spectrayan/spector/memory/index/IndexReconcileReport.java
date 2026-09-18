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
 * Telemetry report produced by an {@link IndexReconcileEngine} execution cycle (ADR-0082).
 *
 * @param scannedEntities total entities scanned in EntityDirectory
 * @param missingReverseMappings count of missing memory-to-entity entries detected
 * @param danglingReverseMappings count of dangling reverse entries detected
 * @param repairedReverseMappings count of missing memory-to-entity entries successfully repaired
 * @param scannedLexicalDocs total documents checked in MemoryIndex
 * @param missingLexicalEntries count of missing documents in BM25Index detected
 * @param staleLexicalEntries count of stale BM25 postings detected
 * @param repairedLexicalEntries count of documents re-indexed into BM25Index
 * @param scannedSpladeDocs total SPLADE documents scanned
 * @param missingSpladeEntries count of missing SPLADE entries detected
 * @param staleSpladeEntries count of stale SPLADE postings detected
 * @param repairedSpladeEntries count of SPLADE postings repaired
 * @param scannedHypergraph total hyperedges scanned in HyperEntityGraphMemory
 * @param quarantinedHypergraph count of hyperedges newly quarantined this cycle
 * @param unquarantinedHypergraph count of hyperedges released from quarantine this cycle
 * @param elapsedMs elapsed time of this reconcile cycle in milliseconds
 * @param truncated true if cycle terminated early due to time-slice or repair budget limits
 *
 * @since 1.1.0
 */
public record IndexReconcileReport(
        long scannedEntities,
        long missingReverseMappings,
        long danglingReverseMappings,
        long repairedReverseMappings,
        long scannedLexicalDocs,
        long missingLexicalEntries,
        long staleLexicalEntries,
        long repairedLexicalEntries,
        long scannedSpladeDocs,
        long missingSpladeEntries,
        long staleSpladeEntries,
        long repairedSpladeEntries,
        long scannedHypergraph,
        long quarantinedHypergraph,
        long unquarantinedHypergraph,
        long elapsedMs,
        boolean truncated
) {
    /** Backwards-compatible constructor without hypergraph metrics. */
    public IndexReconcileReport(
            long scannedEntities,
            long missingReverseMappings,
            long danglingReverseMappings,
            long repairedReverseMappings,
            long scannedLexicalDocs,
            long missingLexicalEntries,
            long staleLexicalEntries,
            long repairedLexicalEntries,
            long scannedSpladeDocs,
            long missingSpladeEntries,
            long staleSpladeEntries,
            long repairedSpladeEntries,
            long elapsedMs,
            boolean truncated
    ) {
        this(scannedEntities, missingReverseMappings, danglingReverseMappings, repairedReverseMappings,
                scannedLexicalDocs, missingLexicalEntries, staleLexicalEntries, repairedLexicalEntries,
                scannedSpladeDocs, missingSpladeEntries, staleSpladeEntries, repairedSpladeEntries,
                0L, 0L, 0L, elapsedMs, truncated);
    }

    /** Backwards-compatible constructor for reports without dangling, stale, SPLADE, or hypergraph metrics. */
    public IndexReconcileReport(
            long scannedEntities,
            long missingReverseMappings,
            long repairedReverseMappings,
            long scannedLexicalDocs,
            long missingLexicalEntries,
            long repairedLexicalEntries,
            long elapsedMs,
            boolean truncated
    ) {
        this(scannedEntities, missingReverseMappings, 0L, repairedReverseMappings,
                scannedLexicalDocs, missingLexicalEntries, 0L, repairedLexicalEntries,
                0L, 0L, 0L, 0L, 0L, 0L, 0L, elapsedMs, truncated);
    }

    public static IndexReconcileReport empty() {
        return new IndexReconcileReport(0, 0, 0, 0, 0, 0, 0, false);
    }

    public boolean hasRepairs() {
        return repairedReverseMappings > 0 || repairedLexicalEntries > 0
                || repairedSpladeEntries > 0 || quarantinedHypergraph > 0;
    }
}
