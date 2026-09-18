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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe on-heap registry of quarantined hypergraph hyperedges (ADR-0082 Phase 2.1).
 *
 * <p>Maintains a map of hyperedge IDs that have been flagged by the {@link IndexReconcileEngine}
 * for referential integrity violations. Quarantined edges are excluded from graph traversal
 * (spreading activation, co-occurrence, memory collection) via a predicate filter injected
 * into {@code HyperEntityGraphMemory}.</p>
 *
 * <p>This registry is <b>not persisted</b> — quarantine state is recomputed on startup by the
 * first reconciliation cycle. The underlying hyperedge data remains on-disk; only traversal
 * is blocked.</p>
 *
 * @since 1.1.0
 */
public final class QuarantineRegistry {

    private final ConcurrentHashMap<Integer, QuarantinedVertex> quarantined = new ConcurrentHashMap<>();

    /**
     * Quarantines a hyperedge. If the edge is already quarantined, the entry is updated
     * with the new reason and timestamp.
     *
     * @param edgeId the hyperedge ID to quarantine
     * @param reason the integrity violation classification
     */
    public void quarantine(int edgeId, QuarantineReason reason) {
        quarantined.put(edgeId, new QuarantinedVertex(edgeId, reason, System.currentTimeMillis()));
    }

    /**
     * Removes a hyperedge from quarantine (admin un-quarantine).
     *
     * @param edgeId the hyperedge ID to release
     * @return {@code true} if the edge was quarantined and has been released
     */
    public boolean unquarantine(int edgeId) {
        return quarantined.remove(edgeId) != null;
    }

    /**
     * Checks whether a hyperedge is currently quarantined.
     *
     * @param edgeId the hyperedge ID to check
     * @return {@code true} if the edge is quarantined
     */
    public boolean isQuarantined(int edgeId) {
        return quarantined.containsKey(edgeId);
    }

    /**
     * Returns the number of currently quarantined hyperedges.
     * Suitable for binding to a Micrometer gauge.
     */
    public int quarantinedCount() {
        return quarantined.size();
    }

    /**
     * Returns an unmodifiable snapshot of all quarantined vertices for admin inspection.
     */
    public List<QuarantinedVertex> snapshot() {
        return List.copyOf(quarantined.values());
    }

    /**
     * Returns an unmodifiable view of the quarantine map for admin inspection.
     */
    public Map<Integer, QuarantinedVertex> quarantineMap() {
        return Collections.unmodifiableMap(quarantined);
    }

    /**
     * Clears all quarantine entries. Used during full reconciliation resets.
     */
    public void clear() {
        quarantined.clear();
    }
}
