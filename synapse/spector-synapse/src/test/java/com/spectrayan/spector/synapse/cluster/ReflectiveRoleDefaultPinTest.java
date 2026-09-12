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
package com.spectrayan.spector.synapse.cluster;

import com.spectrayan.spector.cluster.OwnershipResolver;
import com.spectrayan.spector.cluster.node.NodeRole;
import com.spectrayan.spector.cluster.routing.RoutingKey;
import com.spectrayan.spector.commons.error.SpectorValidationException;
import com.spectrayan.spector.config.SpectorPropertyConstants;
import com.spectrayan.spector.synapse.config.cell.CellProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReflectiveRoleDefaultPinTest {

    @Test
    @DisplayName("Pins DEFAULT_CELL_ROLE reflectively to prevent javac inlining hiding stale defaults (Req R8.2, R4.2)")
    void testReflectiveDefaultCellRolePin() throws Exception {
        Field field = SpectorPropertyConstants.class.getField("DEFAULT_CELL_ROLE");
        Object rawValue = field.get(null);

        assertThat(rawValue)
                .as("SpectorPropertyConstants.DEFAULT_CELL_ROLE must be reflectively pinned to 'standalone'")
                .isEqualTo("standalone");
    }

    @Test
    @DisplayName("Verifies CellProperties defaults to standalone and short-circuits ring (Invariant J4)")
    void testCellPropertiesDefaults() {
        CellProperties props = new CellProperties();

        assertThat(props.getRole()).isEqualTo("standalone");
        assertThat(props.resolvedRole()).isEqualTo(NodeRole.STANDALONE);
        assertThat(props.getRing().getVersion()).isEqualTo(1);
        assertThat(props.getRing().getMembers()).isEmpty();
        assertThat(props.getRing().getMembersFile()).isNull();

        OwnershipResolver resolver = props.toOwnershipResolver();
        assertThat(resolver.identity().role()).isEqualTo(NodeRole.STANDALONE);
        assertThat(resolver.ownsLocally(new RoutingKey("any-cell", "any-tenant", "any-ns"))).isTrue();
    }

    @Test
    @DisplayName("Non-standalone role requires cellId and fails closed (Req R4.4)")
    void testNonStandaloneRequiresCellId() {
        CellProperties props = new CellProperties();
        props.setRole("owner");
        props.setNodeId("node-1");
        props.getRing().setMembers(List.of("node-1", "node-2"));

        assertThatThrownBy(props::toOwnershipResolver)
                .isInstanceOf(SpectorValidationException.class)
                .hasMessageContaining("cellId is required");
    }

    @Test
    @DisplayName("Non-standalone role fails closed if membership is empty (Req R7.3, L2)")
    void testNonStandaloneFailsClosedWithoutMembers() {
        CellProperties props = new CellProperties();
        props.setRole("owner");
        props.setId("cell-1");
        props.setNodeId("node-1");

        assertThatThrownBy(props::toOwnershipResolver)
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("membershipSource must not be null");
    }
}
