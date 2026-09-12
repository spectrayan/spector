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
package com.spectrayan.spector.synapse.cluster.failover;

import com.spectrayan.spector.cluster.coordinator.CoordinatorLeaseManager;
import com.spectrayan.spector.cluster.fencing.FenceTokenManager;
import com.spectrayan.spector.cluster.membership.CellMembership;
import com.spectrayan.spector.cluster.routing.ConsistentHashRing;
import com.spectrayan.spector.cluster.routing.OverrideLeaseManager;
import com.spectrayan.spector.cluster.routing.RoutingKey;
import com.spectrayan.spector.cluster.store.ControlStore;
import com.spectrayan.spector.synapse.config.failover.FailoverProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.LongAdder;

/**
 * Orchestrates automated cell-level failover, epoch advancement, survivor promotion, and fencing (Req R4.1–R4.9, R9.1, R9.6).
 *
 * <p>Execution Sequence (ADR §11.1, Req R4.1–R4.4):
 * <ol>
 *   <li><b>Readiness Failure Detection (Req R4.1):</b> Declares node death only after continuous readiness probe failures
 *       exceeding {@code failAfterSeconds}, avoiding transient false positives.</li>
 *   <li><b>Coordinator Authority (Req R4.2, R1.3):</b> Only the elected coordinator may execute failover mutations.</li>
 *   <li><b>Flapping Protection (Req R4.9):</b> Cooldown hysteresis prevents thrashing when a node oscillates.</li>
 *   <li><b>Verification Before Promotion (Req R4.4, Q6):</b> Verifies candidate replica data before promotion;
 *       refuses promotion if verification fails.</li>
 *   <li><b>Single-Writer Invariance (Req R4.2, R4.7, Q1):</b> Bumps the namespace epoch in the control store as the serialization
 *       point, mints a monotonic fence token, and pins the namespace to the survivor via an override lease.</li>
 *   <li><b>Observe-Only Mode (Req R10.1, D4):</b> In {@code observe_only} mode, evaluates and logs structured audit actions
 *       without mutating cluster state.</li>
 * </ol>
 * </p>
 *
 * <p><b>Note on In-Flight Requests (Req R4.5, ADR §11.1):</b> Requests in-flight on the dead node holding a
 * {@code RegionLease} die with the process; clients receive connection drop/refusal and retry against the survivor.
 * This is expected system behavior, not a defect.</p>
 */
public class FailoverOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(FailoverOrchestrator.class);

    /**
     * Provides the inventory of known namespace identifiers for failover discovery (G11).
     * <p>Without an inventory, failover cannot determine which namespaces the dead node owned
     * via the hash ring. Implementations may enumerate from a catalog, control store, or disk.</p>
     */
    @FunctionalInterface
    public interface NamespaceInventory {
        /**
         * Returns all known namespace identifiers in the cell.
         *
         * @return collection of namespace identifiers; must not be null
         */
        Collection<String> listNamespaceIds();
    }

    private final ControlStore controlStore;
    private final CoordinatorLeaseManager coordinatorLeaseManager;
    private final OverrideLeaseManager overrideLeaseManager;
    private final FenceTokenManager fenceTokenManager;
    private final FailoverProperties properties;
    private final NodeHealthProbe healthProbe;
    private final CandidateDataVerifier dataVerifier;
    private final NamespaceInventory namespaceInventory;
    private final Clock clock;

    private final Map<String, Instant> failureStartTimes = new ConcurrentHashMap<>();
    private final Map<String, Instant> cooldownTimes = new ConcurrentHashMap<>();
    private final List<FailoverAuditRecord> auditHistory = new CopyOnWriteArrayList<>();

    private final LongAdder failoverCount = new LongAdder();
    private final LongAdder observedFailoverCount = new LongAdder();

    public FailoverOrchestrator(
            ControlStore controlStore,
            CoordinatorLeaseManager coordinatorLeaseManager,
            OverrideLeaseManager overrideLeaseManager,
            FenceTokenManager fenceTokenManager,
            FailoverProperties properties,
            NodeHealthProbe healthProbe,
            CandidateDataVerifier dataVerifier) {
        this(controlStore, coordinatorLeaseManager, overrideLeaseManager, fenceTokenManager,
                properties, healthProbe, dataVerifier, List::of, Clock.systemUTC());
    }

    public FailoverOrchestrator(
            ControlStore controlStore,
            CoordinatorLeaseManager coordinatorLeaseManager,
            OverrideLeaseManager overrideLeaseManager,
            FenceTokenManager fenceTokenManager,
            FailoverProperties properties,
            NodeHealthProbe healthProbe,
            CandidateDataVerifier dataVerifier,
            Clock clock) {
        this(controlStore, coordinatorLeaseManager, overrideLeaseManager, fenceTokenManager,
                properties, healthProbe, dataVerifier, List::of, clock);
    }

    public FailoverOrchestrator(
            ControlStore controlStore,
            CoordinatorLeaseManager coordinatorLeaseManager,
            OverrideLeaseManager overrideLeaseManager,
            FenceTokenManager fenceTokenManager,
            FailoverProperties properties,
            NodeHealthProbe healthProbe,
            CandidateDataVerifier dataVerifier,
            NamespaceInventory namespaceInventory,
            Clock clock) {
        this.controlStore = Objects.requireNonNull(controlStore, "controlStore must not be null");
        this.coordinatorLeaseManager = Objects.requireNonNull(coordinatorLeaseManager, "coordinatorLeaseManager must not be null (G19)");
        this.overrideLeaseManager = Objects.requireNonNull(overrideLeaseManager, "overrideLeaseManager must not be null");
        this.fenceTokenManager = Objects.requireNonNull(fenceTokenManager, "fenceTokenManager must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.healthProbe = Objects.requireNonNull(healthProbe, "healthProbe must not be null");
        this.dataVerifier = Objects.requireNonNull(dataVerifier, "dataVerifier must not be null");
        this.namespaceInventory = Objects.requireNonNull(namespaceInventory, "namespaceInventory must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * Executes a single evaluation cycle of cell health and triggers failover if an owner has failed (Req R4.1).
     */
    public void evaluateCellHealth() {
        if (!properties.isEnabled()) {
            return;
        }
        if (!coordinatorLeaseManager.checkStoreEnforcedLeaseActive()) {
            log.debug("[FailoverOrchestrator] Not the active coordinator; skipping failover evaluation");
            return;
        }

        CellMembership membership = controlStore.getMembership();
        if (membership == null || membership.members().size() <= 1) {
            return;
        }

        Instant now = clock.instant();
        List<String> members = membership.members();

        for (String nodeId : members) {
            boolean isReady = healthProbe.isNodeReady(nodeId);
            if (isReady) {
                failureStartTimes.remove(nodeId);
                continue;
            }

            Instant firstFailure = failureStartTimes.computeIfAbsent(nodeId, k -> now);
            Duration failureDuration = Duration.between(firstFailure, now);

            if (failureDuration.getSeconds() >= properties.getFailAfterSeconds()) {
                handleNodeFailure(nodeId, membership, now);
            }
        }
    }

    private void handleNodeFailure(String deadNodeId, CellMembership membership, Instant now) {
        // Flapping hysteresis / cooldown check (Req R4.9)
        Instant lastFailover = cooldownTimes.get(deadNodeId);
        if (lastFailover != null) {
            Duration elapsedSinceCooldown = Duration.between(lastFailover, now);
            if (elapsedSinceCooldown.getSeconds() < properties.getCooldownSeconds()) {
                log.warn("[FailoverOrchestrator] Node '{}' is in failover cooldown ({}s elapsed < {}s limit); suppressing failover (Req R4.9)",
                        deadNodeId, elapsedSinceCooldown.getSeconds(), properties.getCooldownSeconds());
                return;
            }
        }

        // Find candidate survivor (Req R4.6: uses existing nodes, no new nodes required)
        String survivorNodeId = selectSurvivor(deadNodeId, membership.members());
        if (survivorNodeId == null) {
            log.error("[FailoverOrchestrator] No viable survivor node found in cell to take over from dead node '{}'", deadNodeId);
            return;
        }

        // Find affected namespaces — uses ring + inventory + overrides (G11)
        List<String> affectedNamespaces = findNamespacesForNode(deadNodeId, membership);
        if (affectedNamespaces.isEmpty()) {
            // G11: Never substitute a placeholder — abort and alarm
            log.error("[FailoverOrchestrator] No namespaces found for dead node '{}'. " +
                    "Namespace inventory may be unavailable or node had no assignments. " +
                    "Aborting failover — manual intervention required (G11).", deadNodeId);
            return;
        }

        // G20: Use properties.isObserveOnly() instead of inline string comparison
        boolean isObserveOnly = properties.isObserveOnly();

        for (String ns : affectedNamespaces) {
            executeFailoverForNamespace(ns, deadNodeId, survivorNodeId, isObserveOnly, now);
        }

        cooldownTimes.put(deadNodeId, now);
        failureStartTimes.remove(deadNodeId);
    }

    /**
     * Executes failover promotion for a single namespace.
     *
     * @param namespaceId    namespace identifier
     * @param deadNodeId     failed owner
     * @param survivorNodeId candidate survivor
     * @param isObserveOnly  whether running in observe-only mode
     * @param triggerTime    trigger timestamp
     */
    public boolean executeFailoverForNamespace(
            String namespaceId,
            String deadNodeId,
            String survivorNodeId,
            boolean isObserveOnly,
            Instant triggerTime) {
        Instant start = clock.instant();

        // G19: Verify coordinator authority FIRST before any mutation or verification!
        if (!isObserveOnly) {
            if (!coordinatorLeaseManager.checkStoreEnforcedLeaseActive()) {
                log.error("[FailoverOrchestrator] Refusing failover for namespace '{}': not the active coordinator (G19)",
                        namespaceId);
                return false;
            }
        }

        // 1. Verify candidate replica data before promotion (Req R4.4, Q6)
        boolean dataValid = dataVerifier.verifyCandidateData(namespaceId, survivorNodeId);
        if (!dataValid) {
            log.error("[FailoverOrchestrator] Refusing promotion for namespace '{}' to survivor '{}': data verification failed (Req R4.4, Q6)",
                    namespaceId, survivorNodeId);
            return false;
        }

        if (isObserveOnly) {
            // Observe-only mode (Req R10.1, D4)
            Duration duration = Duration.between(start, clock.instant());
            FailoverAuditRecord record = new FailoverAuditRecord(
                    namespaceId, deadNodeId, survivorNodeId,
                    controlStore.getNamespaceEpoch(namespaceId),
                    "OBSERVE_FENCE", "OBSERVE_ONLY",
                    duration, triggerTime != null ? triggerTime : start
            );
            auditHistory.add(record);
            observedFailoverCount.increment();
            log.info("[FailoverOrchestrator] OBSERVE_ONLY: Would promote namespace '{}' from '{}' to '{}' (Req R10.1)",
                    namespaceId, deadNodeId, survivorNodeId);
            return true;
        }

        // 2. Advance monotonic epoch in control store under coordinator lease version (Req R4.2, R4.7, G16 CAS)
        long leaseVersion = coordinatorLeaseManager.getLeaseVersion();
        long newEpoch = controlStore.advanceNamespaceEpoch(namespaceId, leaseVersion);

        // 3. Mint new fence token (Req R4.3, R2.1)
        String newFence = fenceTokenManager.mintFenceForEpoch(namespaceId, newEpoch).toTokenString();

        // 4. Coordinator writes override lease pinning namespace to survivor (Req R3.1, R4.2)
        Duration ttl = Duration.ofSeconds(properties.getOverrideTtlSeconds());
        overrideLeaseManager.setOverrideWithEpoch(namespaceId, survivorNodeId, newEpoch, newFence, ttl);

        Duration duration = Duration.between(start, clock.instant());
        FailoverAuditRecord audit = new FailoverAuditRecord(
                namespaceId, deadNodeId, survivorNodeId,
                newEpoch, newFence, "READINESS_FAILURE",
                duration, triggerTime != null ? triggerTime : start
        );
        auditHistory.add(audit);
        failoverCount.increment();

        log.info("[FailoverOrchestrator] Failover executed: namespace '{}' promoted from '{}' to '{}' [epoch={}, fence='{}', duration={}ms] (Req R4.2, R9.6)",
                namespaceId, deadNodeId, survivorNodeId, newEpoch, newFence, duration.toMillis());
        return true;
    }

    private String selectSurvivor(String deadNodeId, List<String> members) {
        for (String member : members) {
            if (!member.equals(deadNodeId) && healthProbe.isNodeReady(member)) {
                return member;
            }
        }
        return null;
    }

    /**
     * Finds all namespaces affected by the death of {@code deadNodeId} (G11).
     *
     * <p>Union of:
     * <ol>
     *   <li>Namespaces whose hash-ring owner is the dead node (via {@link NamespaceInventory})</li>
     *   <li>Namespaces with active overrides targeting the dead node</li>
     * </ol>
     * Never returns a placeholder like {@code "default"} — if inventory is empty, returns empty list.</p>
     *
     * @param deadNodeId dead node identifier
     * @param membership current cell membership
     * @return affected namespace identifiers, deduplicated
     */
    private List<String> findNamespacesForNode(String deadNodeId, CellMembership membership) {
        Set<String> affected = new LinkedHashSet<>();
        ConsistentHashRing ring = ConsistentHashRing.of(membership.ringVersion(), membership.members());

        // 1. Enumerate namespaces whose hash-ring owner is the dead node (G11)
        Collection<String> knownNamespaces = namespaceInventory.listNamespaceIds();
        for (String nsId : knownNamespaces) {
            RoutingKey key = RoutingKey.ofUntenanted(null, nsId);
            String owner = ring.ownerOf(key.keyMaterial());
            if (deadNodeId.equals(owner)) {
                affected.add(nsId);
            }
        }

        // 2. Union with overrides targeting the dead node
        for (var override : overrideLeaseManager.listActiveOverrides()) {
            if (override.targetNodeId().equals(deadNodeId)) {
                affected.add(override.namespaceId());
            }
        }

        return List.copyOf(affected);
    }

    public long getFailoverCount() {
        return failoverCount.sum();
    }

    public long getObservedFailoverCount() {
        return observedFailoverCount.sum();
    }

    public List<FailoverAuditRecord> getAuditHistory() {
        return Collections.unmodifiableList(auditHistory);
    }
}

