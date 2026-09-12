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
package com.spectrayan.spector.synapse.config.failover;

import com.spectrayan.spector.synapse.cluster.failover.CandidateDataVerifier;
import com.spectrayan.spector.synapse.cluster.failover.FailoverOrchestrator;
import com.spectrayan.spector.synapse.cluster.failover.NodeHealthProbe;
import com.spectrayan.spector.synapse.config.SynapseProperties;
import com.spectrayan.spector.synapse.config.cell.ClusterControlPlaneConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ClusterFailoverConfiguration Bean Presence Tests (G0)")
class ClusterFailoverConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(
                    ClusterControlPlaneConfiguration.class,
                    ClusterFailoverConfiguration.class,
                    SynapseProperties.class
            );

    @Test
    @DisplayName("Standalone mode produces zero failover beans")
    void testStandaloneModeProducesNoFailoverBeans() {
        runner.withPropertyValues("spector.cell.role=standalone")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(FailoverOrchestrator.class);
                    assertThat(context).doesNotHaveBean(ClusterFailoverConfiguration.FailoverHealthEvaluationScheduler.class);
                });
    }

    @Test
    @DisplayName("Cluster mode produces failover infrastructure beans")
    void testClusterModeProducesFailoverBeans() {
        runner.withPropertyValues(
                "spector.cluster.enabled=true",
                "spector.cell.role=owner",
                "spector.cell.id=cell-test",
                "spector.cell.node-id=node-1",
                "spector.cell.ring.members[0]=node-1",
                "spector.control-store.type=memory"
        ).run(context -> {
            assertThat(context).hasSingleBean(NodeHealthProbe.class);
            assertThat(context).hasSingleBean(CandidateDataVerifier.class);
            assertThat(context).hasSingleBean(FailoverOrchestrator.NamespaceInventory.class);
            assertThat(context).hasSingleBean(FailoverOrchestrator.class);
            assertThat(context).hasSingleBean(ClusterFailoverConfiguration.FailoverHealthEvaluationScheduler.class);
        });
    }
}
