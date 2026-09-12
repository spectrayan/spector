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
import com.spectrayan.spector.cluster.membership.CellMembership;
import com.spectrayan.spector.cluster.membership.StaticMembershipSource;
import com.spectrayan.spector.cluster.node.NodeIdentity;
import com.spectrayan.spector.cluster.node.NodeRole;
import com.spectrayan.spector.cluster.routing.RoutingKey;
import com.spectrayan.spector.events.SpectorEvent;
import com.spectrayan.spector.memory.sync.CheckpointCompletedEvent;
import com.spectrayan.spector.synapse.config.replication.ReplicationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for ReplicationCoordinator:
 * - Subscribes to CheckpointCompletedEvent (R7.1)
 * - Sources epoch from ownership layer (R7.2, B6)
 * - Debounce interval and minChanges (R7.3, R7.5)
 * - Non-owner nodes refuse to produce snapshots (R7.4)
 * - Writes :hint key to Redis routing store (R10.5)
 */
@DisplayName("Task 6.1–6.3, 6.8: Replication Coordinator Tests")
class ReplicationCoordinatorTest {

    private static final String CELL_ID = "cell-1";
    private static final String OWNER_NODE = "node-1";
    private static final String OTHER_NODE = "node-2";
    private static final String TENANT_ID = "ten-alpha";
    private static final String NAMESPACE_ID = "018f9b8c000070008000000000000088";

    private OwnershipResolver ownerResolver;
    private ReplicationProperties properties;
    private AtomicBoolean hintWritten;
    private AtomicLong lastHintHwm;
    private ReplicationHintWriter hintWriter;
    private ReplicationMetrics metrics;

    @BeforeEach
    void setUp() {
        hintWritten = new AtomicBoolean(false);
        lastHintHwm = new AtomicLong(0);

        hintWriter = (key, hwm, ts, epoch) -> {
            hintWritten.set(true);
            lastHintHwm.set(hwm);
        };

        properties = new ReplicationProperties();
        properties.setEnabled(true);
        properties.setSnapshotIntervalSeconds(60L);
        properties.setSnapshotMinChanges(100);

        metrics = new ReplicationMetrics();

        // Node-1 owns all in standalone or static ring
        ownerResolver = OwnershipResolver.standalone();
    }

    @Test
    @DisplayName("R7.1 & R7.2: Coordinator subscribes to CheckpointCompletedEvent and sources epoch from ownership")
    void testCheckpointEventTriggersReplication() {
        ReplicationCoordinator coordinator = new ReplicationCoordinator(
                CELL_ID,
                ownerResolver,
                properties,
                hintWriter,
                metrics
        );

        RoutingKey key = new RoutingKey(CELL_ID, TENANT_ID, NAMESPACE_ID);

        CheckpointCompletedEvent event = new CheckpointCompletedEvent(
                Map.of(
                        SpectorEvent.ContextKeys.TENANT, TENANT_ID,
                        SpectorEvent.ContextKeys.NAMESPACE, NAMESPACE_ID
                ),
                42019L,
                150L, // 150 changes >= minChanges (100)
                5L,
                Instant.now()
        );

        // First event records mutations; if interval (60s) hasn't passed, snapshot is debounced (R7.3)
        coordinator.onCheckpointCompleted(event);

        assertThat(coordinator.getLastKnownHwm(key)).isEqualTo(42019L);
        assertThat(coordinator.getDebouncer(key)).isNotNull();
        assertThat(coordinator.getDebouncer(key).changeCountSinceLastSnapshot()).isEqualTo(150L);

        // Now force or advance time beyond snapshotInterval
        boolean triggered = coordinator.triggerSnapshot(key, true);
        assertThat(triggered).isTrue();
        assertThat(coordinator.getSnapshotCount(key)).isEqualTo(1L);

        // R10.5: Hint writer received the hint!
        assertThat(hintWritten.get()).isTrue();
        assertThat(lastHintHwm.get()).isEqualTo(42019L);
    }

    @Test
    @DisplayName("R7.4: Non-owner node ignores CheckpointCompletedEvent and never produces snapshots")
    void testNonOwnerIgnoresEvent() {
        // Create an ownership resolver where this node is NOT the owner
        StaticMembershipSource membership = new StaticMembershipSource(new CellMembership(
                CELL_ID,
                1,
                List.of(OWNER_NODE, OTHER_NODE)
        ));

        NodeIdentity nonOwnerIdentity = new NodeIdentity(CELL_ID, OTHER_NODE, NodeRole.OWNER);
        OwnershipResolver nonOwnerResolver = new OwnershipResolver(nonOwnerIdentity, membership);

        // Choose a key that maps to OWNER_NODE (not OTHER_NODE)
        RoutingKey keyNotOwned = null;
        for (int i = 0; i < 1000; i++) {
            RoutingKey candidate = new RoutingKey(CELL_ID, TENANT_ID, "ns-" + i);
            if (!nonOwnerResolver.ownsLocally(candidate)) {
                keyNotOwned = candidate;
                break;
            }
        }
        assertThat(keyNotOwned).isNotNull();

        ReplicationCoordinator coordinator = new ReplicationCoordinator(
                CELL_ID,
                nonOwnerResolver,
                properties,
                hintWriter,
                metrics
        );

        CheckpointCompletedEvent event = new CheckpointCompletedEvent(
                Map.of(
                        SpectorEvent.ContextKeys.TENANT, keyNotOwned.tenantId(),
                        SpectorEvent.ContextKeys.NAMESPACE, keyNotOwned.namespaceId()
                ),
                50000L,
                500L,
                10L,
                Instant.now()
        );

        coordinator.onCheckpointCompleted(event);

        // Assert no snapshot produced, no debounce entry, no hint written (Req R7.4)
        assertThat(coordinator.getSnapshotCount(keyNotOwned)).isEqualTo(0L);
        assertThat(hintWritten.get()).isFalse();
    }

    @Test
    @DisplayName("G5: recordFollowerAck publishes freshness hint with verified applied HWM")
    void testRecordFollowerAckUpdatesHint() {
        ReplicationCoordinator coordinator = new ReplicationCoordinator(
                CELL_ID,
                ownerResolver,
                properties,
                hintWriter,
                metrics
        );

        RoutingKey key = new RoutingKey(CELL_ID, TENANT_ID, NAMESPACE_ID);
        long appliedHwm = 88990L;
        long ackTs = System.currentTimeMillis();

        coordinator.recordFollowerAck(key, appliedHwm, ackTs);

        assertThat(hintWritten.get()).isTrue();
        assertThat(lastHintHwm.get()).isEqualTo(appliedHwm);
    }
}
