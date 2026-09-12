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
package com.spectrayan.spector.synapse.config.replication;

import com.spectrayan.spector.memory.replication.ReplicaApplyEngine;
import com.spectrayan.spector.synapse.config.SynapseProperties;
import com.spectrayan.spector.synapse.config.cell.ClusterControlPlaneConfiguration;
import com.spectrayan.spector.synapse.replication.ReplicationCoordinator;
import com.spectrayan.spector.synapse.replication.ReplicationHintWriter;
import com.spectrayan.spector.synapse.replication.ReplicationMetrics;
import com.spectrayan.spector.synapse.replication.ReplicationServer;
import com.spectrayan.spector.synapse.replication.TenantAllowListFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ClusterReplicationConfiguration Bean Presence Tests (G0)")
class ClusterReplicationConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(
                    ClusterControlPlaneConfiguration.class,
                    ClusterReplicationConfiguration.class,
                    SynapseProperties.class
            );

    @Test
    @DisplayName("Standalone mode produces zero replication beans")
    void testStandaloneModeProducesNoReplicationBeans() {
        runner.withPropertyValues("spector.cell.role=standalone")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(TenantAllowListFilter.class);
                    assertThat(context).doesNotHaveBean(ReplicationMetrics.class);
                    assertThat(context).doesNotHaveBean(ReplicationHintWriter.class);
                    assertThat(context).doesNotHaveBean(ReplicationCoordinator.class);
                    assertThat(context).doesNotHaveBean(ReplicaApplyEngine.class);
                    assertThat(context).doesNotHaveBean(ReplicationServer.class);
                });
    }

    @Test
    @DisplayName("Cluster mode produces replication infrastructure beans")
    void testClusterModeProducesReplicationBeans() {
        runner.withPropertyValues(
                "spector.cluster.enabled=true",
                "spector.cell.role=owner",
                "spector.cell.id=cell-test",
                "spector.cell.node-id=node-1",
                "spector.cell.ring.members[0]=node-1",
                "spector.control-store.type=memory"
        ).run(context -> {
            assertThat(context).hasSingleBean(TenantAllowListFilter.class);
            assertThat(context).hasSingleBean(ReplicationMetrics.class);
            assertThat(context).hasSingleBean(ReplicationHintWriter.class);
            assertThat(context).hasSingleBean(ReplicationCoordinator.class);
            assertThat(context).hasSingleBean(ReplicaApplyEngine.class);
            assertThat(context).doesNotHaveBean(ReplicationServer.class);
        });
    }
}
