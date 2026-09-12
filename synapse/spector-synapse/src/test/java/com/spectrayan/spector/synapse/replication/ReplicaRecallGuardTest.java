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

import com.spectrayan.spector.cluster.routing.RoutingKey;
import com.spectrayan.spector.config.SpectorPropertyConstants;
import com.spectrayan.spector.synapse.config.replication.ReplicationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for ReplicaRecallGuard:
 * - Bounded-staleness 3-condition validation (R10.1)
 * - Staleness computed from applied snapshot time (R10.2)
 * - Replica reads default OFF at gateway, reflectively pinned (R10.3, Task 6.6)
 * - Freshness bound exceeded refusal (R10.4, N7)
 * - Unconditional write refusal on replica (R10.6, N2)
 */
@DisplayName("Task 6.4–6.7, 6.9: Replica Recall Guard & Freshness Tests")
class ReplicaRecallGuardTest {

    private static final String CELL_ID = "cell-1";
    private static final String TENANT_ID = "ten-001";
    private static final String NAMESPACE_ID = "018f9b8c000070008000000000000011";

    private ReplicationProperties props;
    private ReplicaRecallGuard guard;
    private RoutingKey key;

    @BeforeEach
    void setUp() {
        props = new ReplicationProperties();
        props.setReplicaReadsEnabled(true);
        props.setMaxReplicaLagSeconds(30L); // 30s max lag
        guard = new ReplicaRecallGuard(props);
        key = new RoutingKey(CELL_ID, TENANT_ID, NAMESPACE_ID);
    }

    @Test
    @DisplayName("R10.3: Replica reads default OFF at the gateway (pinned reflectively)")
    void testReplicaReadsDefaultOffPinnedReflectively() throws Exception {
        Field field = SpectorPropertyConstants.class.getField("DEFAULT_REPLICATION_REPLICA_READS_ENABLED");
        boolean defaultValue = (boolean) field.get(null);
        assertThat(defaultValue)
                .describedAs("DEFAULT_REPLICATION_REPLICA_READS_ENABLED must be false by default")
                .isFalse();

        ReplicationProperties freshProps = new ReplicationProperties();
        assertThat(freshProps.isReplicaReadsEnabled()).isFalse();

        ReplicaRecallGuard defaultGuard = new ReplicaRecallGuard(freshProps);
        // Even with header, if gateway default is OFF, request is NOT permitted
        boolean permitted = defaultGuard.isReplicaReadPermitted(Map.of(ReplicaRecallGuard.HEADER_ALLOW_REPLICA, "true"));
        assertThat(permitted).isFalse();
    }

    @Test
    @DisplayName("R10.1: Replica recall permitted only when mapped, within bound, and request permits")
    void testReplicaRecallPermittedWhenAllConditionsMet() {
        long now = System.currentTimeMillis();
        long snapshotTime = now - 5_000L; // 5 seconds lag <= 30 seconds

        boolean headerPermits = guard.isReplicaReadPermitted(Map.of(ReplicaRecallGuard.HEADER_ALLOW_REPLICA, "true"));
        assertThat(headerPermits).isTrue();

        // All 3 conditions satisfied -> passes without exception
        guard.validateReplicaRecall(key, true, snapshotTime, now, headerPermits);
    }

    @Test
    @DisplayName("R10.1: Refuses recall if request does not permit replica reads")
    void testRefusesIfRequestDoesNotPermit() {
        long now = System.currentTimeMillis();
        long snapshotTime = now - 5_000L;

        assertThatThrownBy(() -> guard.validateReplicaRecall(key, true, snapshotTime, now, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not carry " + ReplicaRecallGuard.HEADER_ALLOW_REPLICA);
    }

    @Test
    @DisplayName("R10.1: Refuses recall if namespace is not mapped locally on replica")
    void testRefusesIfNotMappedLocally() {
        long now = System.currentTimeMillis();
        long snapshotTime = now - 5_000L;

        assertThatThrownBy(() -> guard.validateReplicaRecall(key, false, snapshotTime, now, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not mapped locally");
    }

    @Test
    @DisplayName("R10.4 & N7: Refuses recall with ReplicaStalenessExceededException when lag exceeds bound")
    void testRefusesWhenLagExceedsBound() {
        long now = System.currentTimeMillis();
        long snapshotTime = now - 45_000L; // 45 seconds lag > 30 seconds limit

        assertThatThrownBy(() -> guard.validateReplicaRecall(key, true, snapshotTime, now, true))
                .isInstanceOf(ReplicaStalenessExceededException.class)
                .hasMessageContaining("Replica freshness bound exceeded")
                .satisfies(ex -> {
                    ReplicaStalenessExceededException rse = (ReplicaStalenessExceededException) ex;
                    assertThat(rse.getCurrentLagMs()).isGreaterThan(30_000L);
                    assertThat(rse.getMaxLagMs()).isEqualTo(30_000L);
                });
    }

    @Test
    @DisplayName("R10.6 & N2: Replica unconditionally refuses writes, even if header is present")
    void testReplicaUnconditionallyRefusesWrites() {
        // Even if request carries allow-replica header, writes on replica are refused (Req R10.6)
        assertThatThrownBy(() -> guard.validateWritePermitted(NAMESPACE_ID, true))
                .isInstanceOf(ReplicaWriteRefusedException.class)
                .hasMessageContaining("Replicas unconditionally refuse writes");

        // Owner permits write
        guard.validateWritePermitted(NAMESPACE_ID, false);
    }
}
