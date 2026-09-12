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

import com.spectrayan.spector.cluster.node.NodeIdentity;
import com.spectrayan.spector.cluster.node.NodeRole;
import com.spectrayan.spector.cluster.routing.RoutingKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins single-node and standalone behavior when {@code spector-cluster} is on the classpath (Req R11.1, R12.7).
 */
@DisplayName("Task 0.1: Standalone Parity Regression Test")
class StandaloneParityRegressionTest {

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
}
