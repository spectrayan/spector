/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-synapse/LICENSE
 *
 * Change Date: July 6, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.synapse.cluster.prewarm;

import com.spectrayan.spector.synapse.config.replication.ReplicationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Manages replica namespace pre-warm selection and heat-based prioritization (Req R6.1, R6.2, R6.3).
 *
 * <p>Enforces:
 * <ul>
 *   <li><b>Explicit & Heat-Based Selection (Req R6.1):</b> Selects namespaces either from explicit configuration
 *       or by access frequency.</li>
 *   <li><b>Hot Cap Bounded (Req R6.2):</b> Strictly respects {@code replicaHotCap} so a replica never maps
 *       the union of all its owners' hot sets.</li>
 *   <li><b>Pre-Warm Coverage Tracking (Req R6.3):</b> Exposes pre-warm selection ratio and active count.</li>
 * </ul>
 * </p>
 */
public class ReplicaPreWarmManager {

    private static final Logger log = LoggerFactory.getLogger(ReplicaPreWarmManager.class);

    private final ReplicationProperties replicationProperties;
    private final List<String> explicitFollowList = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final Map<String, LongAdder> namespaceHeat = new ConcurrentHashMap<>();
    private final Set<String> currentlyPreWarmed = ConcurrentHashMap.newKeySet();
    private final ReentrantLock recomputeLock = new ReentrantLock();

    public ReplicaPreWarmManager(ReplicationProperties replicationProperties) {
        this(replicationProperties, List.of());
    }

    public ReplicaPreWarmManager(ReplicationProperties replicationProperties, List<String> initialFollowList) {
        this.replicationProperties = Objects.requireNonNull(replicationProperties, "replicationProperties must not be null");
        if (initialFollowList != null) {
            this.explicitFollowList.addAll(initialFollowList);
        }
        recomputePreWarmSet();
    }

    /**
     * Records access for a namespace to increment its heat score.
     *
     * @param namespaceId accessed namespace
     */
    public void recordAccess(String namespaceId) {
        if (namespaceId != null && !namespaceId.isBlank()) {
            namespaceHeat.computeIfAbsent(namespaceId, k -> new LongAdder()).increment();
        }
    }

    /**
     * Updates the explicit follow list of namespaces (Req R6.1).
     *
     * @param followList explicit namespace list
     */
    public void setExplicitFollowList(List<String> followList) {
        explicitFollowList.clear();
        if (followList != null) {
            explicitFollowList.addAll(followList);
        }
        recomputePreWarmSet();
    }

    /**
     * Recomputes the pre-warmed set respecting {@code replicaHotCap} (Req R6.2).
     *
     * @return unmodifiable set of currently pre-warmed namespaces
     */
    public Set<String> recomputePreWarmSet() {
        recomputeLock.lock();
        try {
            int cap = Math.max(1, replicationProperties.getReplicaHotCap());

            // Gather candidates: explicit followed first, then sort remaining by heat
            Set<String> selected = new HashSet<>();

            // Add explicit follow list up to cap
            for (String explicitNs : explicitFollowList) {
                if (selected.size() < cap) {
                    selected.add(explicitNs);
                }
            }

            // Fill remaining slots with hottest namespaces
            if (selected.size() < cap) {
                List<Map.Entry<String, LongAdder>> heatSorted = new ArrayList<>(namespaceHeat.entrySet());
                heatSorted.sort(Comparator.comparingLong((Map.Entry<String, LongAdder> e) -> e.getValue().sum()).reversed());

                for (var entry : heatSorted) {
                    if (selected.size() >= cap) {
                        break;
                    }
                    selected.add(entry.getKey());
                }
            }

            currentlyPreWarmed.clear();
            currentlyPreWarmed.addAll(selected);
            log.info("[ReplicaPreWarmManager] Pre-warm set updated: {} namespaces (hotCap={})",
                    currentlyPreWarmed.size(), cap);
            return Collections.unmodifiableSet(currentlyPreWarmed);
        } finally {
            recomputeLock.unlock();
        }
    }

    /**
     * Checks if a given namespace is currently selected for pre-warm.
     *
     * @param namespaceId namespace identifier
     * @return {@code true} if pre-warmed; {@code false} otherwise
     */
    public boolean isPreWarmed(String namespaceId) {
        return currentlyPreWarmed.contains(namespaceId);
    }

    /**
     * Returns the count of actively pre-warmed namespaces.
     *
     * @return active pre-warm count
     */
    public int getPreWarmCount() {
        return currentlyPreWarmed.size();
    }

    /**
     * Returns the pre-warm coverage ratio relative to total namespaces (Req R6.3).
     *
     * @param totalNamespaces total namespaces in cell
     * @return coverage ratio between 0.0 and 1.0
     */
    public double getPreWarmCoverageRatio(int totalNamespaces) {
        if (totalNamespaces <= 0) {
            return 1.0;
        }
        return (double) currentlyPreWarmed.size() / totalNamespaces;
    }
}
