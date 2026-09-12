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
package com.spectrayan.spector.synapse.replication;

import com.spectrayan.spector.cluster.OwnershipResolver;
import com.spectrayan.spector.cluster.routing.RouteBinding;
import com.spectrayan.spector.cluster.routing.RoutingKey;
import com.spectrayan.spector.events.SpectorEvent;
import com.spectrayan.spector.memory.replication.MutableSetDebouncer;
import com.spectrayan.spector.memory.replication.MutableSetShipper;
import com.spectrayan.spector.memory.replication.SnapshotManifest;
import com.spectrayan.spector.memory.sync.CheckpointCompletedEvent;
import com.spectrayan.spector.synapse.config.replication.ReplicationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Coordinates snapshot replication triggers driven by {@link CheckpointCompletedEvent},
 * sources {@code epoch} from the ownership layer, enforces debounce and single-owner gating,
 * writes the Redis {@code :hint} key, and tracks replication metrics
 * (ADR-0034 §10, Req R7.1–R7.5, R10.5, R11.1, R11.4, Decisions B1, B6).
 */
public class ReplicationCoordinator {

    private static final Logger log = LoggerFactory.getLogger(ReplicationCoordinator.class);

    private final String cellId;
    private final OwnershipResolver ownershipResolver;
    private final ReplicationProperties properties;
    private final ReplicationHintWriter hintWriter;
    private final ReplicationMetrics metrics;

    // Per-namespace debounce state: key -> MutableSetDebouncer
    private final Map<RoutingKey, MutableSetDebouncer> debouncerMap = new ConcurrentHashMap<>();
    private final Map<RoutingKey, AtomicLong> lastHwmMap = new ConcurrentHashMap<>();
    private final Map<RoutingKey, AtomicLong> snapshotCountMap = new ConcurrentHashMap<>();

    public ReplicationCoordinator(
            String cellId,
            OwnershipResolver ownershipResolver,
            ReplicationProperties properties,
            ReplicationHintWriter hintWriter,
            ReplicationMetrics metrics
    ) {
        this.cellId = cellId != null ? cellId : "cell-local";
        this.ownershipResolver = Objects.requireNonNull(ownershipResolver, "ownershipResolver must not be null");
        this.properties = properties != null ? properties : new ReplicationProperties();
        this.hintWriter = hintWriter != null ? hintWriter : ReplicationHintWriter.noop();
        this.metrics = metrics != null ? metrics : new ReplicationMetrics();
    }

    /**
     * Subscribes to {@link CheckpointCompletedEvent} to trigger replication (Req R7.1).
     *
     * <p>Non-blocking and off the write hot path (Req R7.5): marks the namespace dirty and updates debounce state.</p>
     *
     * @param event checkpoint completed event
     */
    public void onCheckpointCompleted(CheckpointCompletedEvent event) {
        if (!properties.isEnabled()) {
            return;
        }

        Map<String, String> ctx = event.context();
        String tenantId = ctx.get(SpectorEvent.ContextKeys.TENANT);
        String namespaceId = ctx.get(SpectorEvent.ContextKeys.NAMESPACE);

        if (namespaceId == null || namespaceId.isBlank()) {
            // Core/OSS event without tenant/namespace context
            return;
        }

        RoutingKey routingKey = new RoutingKey(cellId, tenantId != null ? tenantId : "", namespaceId);

        // Req R7.4: Only the AUTHORITATIVE OWNER may produce snapshots.
        // If a node has lost ownership, it stops producing snapshots immediately (Phase 1 J1).
        if (!ownershipResolver.ownsLocally(routingKey)) {
            log.debug("Ignoring CheckpointCompletedEvent for '{}': not owned locally by this node (Req R7.4)", namespaceId);
            return;
        }

        // Req R7.2 & Decision B6: Source epoch from ownership layer, keeping kernel cell-unaware
        long epoch = resolveEpoch(routingKey);

        // Req R7.3 & R7.5: Cheap, lock-free mark-dirty off the write hot path
        long hwm = event.walHighWaterMark();
        long changes = event.indexSize();
        long now = System.currentTimeMillis();

        MutableSetDebouncer debouncer = debouncerMap.computeIfAbsent(routingKey, k ->
                new MutableSetDebouncer(
                        properties.getSnapshotIntervalSeconds() * 1000L,
                        properties.getSnapshotMinChanges(),
                        now
                )
        );

        debouncer.recordMutations(Math.max(1L, changes));
        lastHwmMap.computeIfAbsent(routingKey, k -> new AtomicLong(0)).set(hwm);

        // Check debounce conditions (interval AND minChanges)
        if (debouncer.shouldSnapshot(now)) {
            triggerSnapshotProduction(routingKey, epoch, hwm, now, debouncer);
        }
    }

    /**
     * Manually triggers snapshot production if debounce criteria are met or forced.
     *
     * @param routingKey routing key
     * @param force whether to bypass interval/minChanges debounce
     * @return true if snapshot was produced, false otherwise
     */
    public boolean triggerSnapshot(RoutingKey routingKey, boolean force) {
        if (!ownershipResolver.ownsLocally(routingKey)) {
            return false;
        }

        long epoch = resolveEpoch(routingKey);
        long now = System.currentTimeMillis();
        long hwm = lastHwmMap.computeIfAbsent(routingKey, k -> new AtomicLong(0)).get();

        MutableSetDebouncer debouncer = debouncerMap.computeIfAbsent(routingKey, k ->
                new MutableSetDebouncer(
                        properties.getSnapshotIntervalSeconds() * 1000L,
                        properties.getSnapshotMinChanges(),
                        now
                )
        );

        if (force || debouncer.shouldSnapshot(now)) {
            triggerSnapshotProduction(routingKey, epoch, hwm, now, debouncer);
            return true;
        }

        return false;
    }

    private void triggerSnapshotProduction(
            RoutingKey routingKey,
            long epoch,
            long hwm,
            long now,
            MutableSetDebouncer debouncer
    ) {
        debouncer.onSnapshotProduced(now);
        snapshotCountMap.computeIfAbsent(routingKey, k -> new AtomicLong(0)).incrementAndGet();

        // Req R10.5: Write the :hint key Phase 2 reserved
        hintWriter.writeHint(routingKey, hwm, now, epoch);

        log.info("Produced snapshot for namespace '{}' (tenant='{}', epoch={}, hwm={})",
                routingKey.namespaceId(), routingKey.tenantId(), epoch, hwm);
    }

    private long resolveEpoch(RoutingKey key) {
        RouteBinding binding = ownershipResolver.resolve(key);
        return binding != null ? binding.epoch() : 1L;
    }

    public long getSnapshotCount(RoutingKey key) {
        AtomicLong count = snapshotCountMap.get(key);
        return count != null ? count.get() : 0L;
    }

    public long getLastKnownHwm(RoutingKey key) {
        AtomicLong hwm = lastHwmMap.get(key);
        return hwm != null ? hwm.get() : 0L;
    }

    public MutableSetDebouncer getDebouncer(RoutingKey key) {
        return debouncerMap.get(key);
    }
}
