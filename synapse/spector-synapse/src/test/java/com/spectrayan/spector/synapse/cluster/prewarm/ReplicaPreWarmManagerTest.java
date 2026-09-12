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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReplicaPreWarmManagerTest {

    @Test
    @DisplayName("Explicit follow list is pre-warmed up to replicaHotCap (Req R6.1, R6.2)")
    void testExplicitFollowListPreWarmed() {
        ReplicationProperties props = new ReplicationProperties();
        props.setReplicaHotCap(2);

        ReplicaPreWarmManager manager = new ReplicaPreWarmManager(props, List.of("ns-1", "ns-2", "ns-3"));

        // Only 2 pre-warmed due to hot cap = 2
        assertThat(manager.getPreWarmCount()).isEqualTo(2);
        assertThat(manager.isPreWarmed("ns-1")).isTrue();
        assertThat(manager.isPreWarmed("ns-2")).isTrue();
        assertThat(manager.isPreWarmed("ns-3")).isFalse();
    }

    @Test
    @DisplayName("Heat-based selection prioritizes highest traffic namespaces (Req R6.1)")
    void testHeatBasedSelection() {
        ReplicationProperties props = new ReplicationProperties();
        props.setReplicaHotCap(2);

        ReplicaPreWarmManager manager = new ReplicaPreWarmManager(props);

        // Record heat
        for (int i = 0; i < 10; i++) {
            manager.recordAccess("ns-cold");
        }
        for (int i = 0; i < 100; i++) {
            manager.recordAccess("ns-hot-1");
        }
        for (int i = 0; i < 200; i++) {
            manager.recordAccess("ns-hot-2");
        }

        manager.recomputePreWarmSet();

        assertThat(manager.getPreWarmCount()).isEqualTo(2);
        assertThat(manager.isPreWarmed("ns-hot-2")).isTrue();
        assertThat(manager.isPreWarmed("ns-hot-1")).isTrue();
        assertThat(manager.isPreWarmed("ns-cold")).isFalse();
    }

    @Test
    @DisplayName("Pre-warm coverage ratio is correctly computed (Req R6.3)")
    void testPreWarmCoverageRatio() {
        ReplicationProperties props = new ReplicationProperties();
        props.setReplicaHotCap(5);

        ReplicaPreWarmManager manager = new ReplicaPreWarmManager(props, List.of("ns-1", "ns-2"));

        // 2 pre-warmed out of 10 total = 0.2
        assertThat(manager.getPreWarmCoverageRatio(10)).isEqualTo(0.2);
        assertThat(manager.getPreWarmCoverageRatio(0)).isEqualTo(1.0);
    }
}
