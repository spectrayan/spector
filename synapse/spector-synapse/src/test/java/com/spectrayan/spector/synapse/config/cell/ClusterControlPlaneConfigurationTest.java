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
package com.spectrayan.spector.synapse.config.cell;

import com.spectrayan.spector.cluster.OwnershipResolver;
import com.spectrayan.spector.cluster.coordinator.CoordinatorLeaseManager;
import com.spectrayan.spector.cluster.fencing.FenceTokenManager;
import com.spectrayan.spector.cluster.routing.OverrideLeaseManager;
import com.spectrayan.spector.cluster.routing.RoutingKey;
import com.spectrayan.spector.cluster.store.ControlStore;
import com.spectrayan.spector.cluster.store.FileControlStore;
import com.spectrayan.spector.cluster.store.InMemoryControlStore;
import com.spectrayan.spector.synapse.config.SynapseProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boundary test verifying bean presence in cluster mode and complete absence in standalone mode (G0, ADR-0034).
 */
@DisplayName("ClusterControlPlaneConfiguration Bean Presence Boundary Tests (G0)")
class ClusterControlPlaneConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ClusterControlPlaneConfiguration.class, SynapseProperties.class);

    @Test
    @DisplayName("G0: Standalone mode produces zero control-plane beans")
    void testStandaloneModeProducesNoControlPlaneBeans() {
        runner.withPropertyValues("spector.cell.role=standalone")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(ControlStore.class);
                    assertThat(context).doesNotHaveBean(CoordinatorLeaseManager.class);
                    assertThat(context).doesNotHaveBean(FenceTokenManager.class);
                    assertThat(context).doesNotHaveBean(OverrideLeaseManager.class);
                    assertThat(context).doesNotHaveBean("ownershipResolver");
                });

        runner.run(context -> {
            assertThat(context).doesNotHaveBean(ControlStore.class);
            assertThat(context).doesNotHaveBean(CoordinatorLeaseManager.class);
            assertThat(context).doesNotHaveBean(FenceTokenManager.class);
            assertThat(context).doesNotHaveBean(OverrideLeaseManager.class);
            assertThat(context).doesNotHaveBean("ownershipResolver");
        });
    }

    @Test
    @DisplayName("G0: Cluster mode with memory control store produces all beans")
    void testClusterModeProducesAllBeansWithMemoryStore() {
        runner.withPropertyValues(
                "spector.cluster.enabled=true",
                "spector.cell.role=owner",
                "spector.cell.id=cell-test",
                "spector.cell.node-id=node-1",
                "spector.cell.ring.members[0]=node-1",
                "spector.control-store.type=memory"
        ).run(context -> {
            assertThat(context).hasSingleBean(ControlStore.class);
            assertThat(context.getBean(ControlStore.class)).isInstanceOf(InMemoryControlStore.class);

            assertThat(context).hasSingleBean(CoordinatorLeaseManager.class);
            assertThat(context).hasSingleBean(FenceTokenManager.class);
            assertThat(context).hasSingleBean(OverrideLeaseManager.class);

            assertThat(context).hasSingleBean(OwnershipResolver.class);
            OwnershipResolver resolver = context.getBean(OwnershipResolver.class);
            assertThat(resolver.ownsLocally(new RoutingKey("cell-test", "tenant-1", "ns-1"))).isTrue();
        });
    }

    @Test
    @DisplayName("G0: Cluster mode with file control store initializes FileControlStore")
    void testClusterModeProducesFileControlStore(@TempDir Path tempDir) {
        Path stateFile = tempDir.resolve("control_state.json");
        runner.withPropertyValues(
                "spector.cluster.enabled=true",
                "spector.cell.role=owner",
                "spector.cell.id=cell-test",
                "spector.cell.node-id=node-1",
                "spector.cell.ring.members[0]=node-1",
                "spector.control-store.type=file",
                "spector.control-store.file-path=" + stateFile.toAbsolutePath()
        ).run(context -> {
            assertThat(context).hasSingleBean(ControlStore.class);
            assertThat(context.getBean(ControlStore.class)).isInstanceOf(FileControlStore.class);
            assertThat(context).hasSingleBean(CoordinatorLeaseManager.class);
            assertThat(context).hasSingleBean(FenceTokenManager.class);
            assertThat(context).hasSingleBean(OverrideLeaseManager.class);
            assertThat(context).hasSingleBean(OwnershipResolver.class);
        });
    }
}
