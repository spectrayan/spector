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
package com.spectrayan.spector.synapse.architecture;

import com.spectrayan.spector.cluster.OwnershipResolver;
import com.spectrayan.spector.cluster.node.NodeIdentity;
import com.spectrayan.spector.cluster.node.NodeRole;
import com.spectrayan.spector.cluster.routing.RoutingKey;
import com.spectrayan.spector.cluster.routing.cache.RedisRoutingCache;
import com.spectrayan.spector.cluster.routing.cache.WaterfallRoutingResolver;
import com.spectrayan.spector.synapse.cluster.gateway.GatewayForwarder;
import com.spectrayan.spector.synapse.cluster.gateway.GatewayForwardingFilter;
import com.spectrayan.spector.synapse.config.SynapseProperties;
import com.spectrayan.spector.synapse.config.routing.ClusterRoutingConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins single-node and standalone behavior when {@code spector-cluster} is on the classpath (Req R11.1, R12.7, G43).
 */
@DisplayName("Standalone Parity Regression Test (G43)")
class StandaloneParityRegressionTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ClusterRoutingConfiguration.class, SynapseProperties.class);

    @Test
    @DisplayName("Default node role is STANDALONE and requires no cell/node IDs")
    void standaloneRoleRequiresNoClusterConfiguration() {
        assertThat(NodeRole.DEFAULT).isEqualTo(NodeRole.STANDALONE);

        NodeIdentity identity = NodeIdentity.standalone();
        assertThat(identity.role()).isEqualTo(NodeRole.STANDALONE);
        assertThat(identity.cellId()).isNull();
        assertThat(identity.nodeId()).isNull();
    }

    @Test
    @DisplayName("Routing key material generation is deterministic in standalone")
    void standaloneRoutingKeyMaterialIsDeterministic() {
        RoutingKey key1 = RoutingKey.ofUntenanted(null, "ns-standalone-1");
        RoutingKey key2 = RoutingKey.ofUntenanted(null, "ns-standalone-1");

        assertThat(key1.keyMaterial()).isEqualTo(key2.keyMaterial());
        assertThat(key1.keyMaterial()).isEqualTo(RoutingKey.NULL_TENANT_SENTINEL + "/ns-standalone-1");
    }

    @Test
    @DisplayName("G43: Standalone boots with zero cluster routing beans and no ring")
    void standaloneBootsWithZeroClusterRoutingInfrastructure() {
        // Assert standalone OwnershipResolver has no ring
        OwnershipResolver standaloneResolver = OwnershipResolver.standalone();
        assertThat(standaloneResolver.ring()).isEmpty();

        // Boot context with defaults (no spector.cell.role set, so standalone by default)
        runner.run(context -> {
            assertThat(context.getBeanNamesForType(WaterfallRoutingResolver.class)).isEmpty();
            assertThat(context.getBeanNamesForType(GatewayForwarder.class)).isEmpty();
            assertThat(context.getBeanNamesForType(GatewayForwardingFilter.class)).isEmpty();
            assertThat(context.getBeanNamesForType(RedisRoutingCache.class)).isEmpty();
        });

        // Explicitly set spector.cell.role=standalone
        runner.withPropertyValues("spector.cell.role=standalone").run(context -> {
            assertThat(context.getBeanNamesForType(WaterfallRoutingResolver.class)).isEmpty();
            assertThat(context.getBeanNamesForType(GatewayForwarder.class)).isEmpty();
            assertThat(context.getBeanNamesForType(GatewayForwardingFilter.class)).isEmpty();
            assertThat(context.getBeanNamesForType(RedisRoutingCache.class)).isEmpty();
        });
    }
}
