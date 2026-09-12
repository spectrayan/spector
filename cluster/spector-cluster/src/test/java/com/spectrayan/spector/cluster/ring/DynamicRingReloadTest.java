/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.spectrayan.spector.cluster.ring;

import com.spectrayan.spector.cluster.OwnershipResolver;
import com.spectrayan.spector.cluster.membership.CellMembership;
import com.spectrayan.spector.cluster.membership.StaticMembershipSource;
import com.spectrayan.spector.cluster.node.NodeIdentity;
import com.spectrayan.spector.cluster.node.NodeRole;
import com.spectrayan.spector.cluster.routing.RouteBinding;
import com.spectrayan.spector.cluster.routing.RoutingKey;
import com.spectrayan.spector.commons.error.SpectorValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DynamicRingReloadTest {

    @Test
    @DisplayName("Hash ring reloads dynamically and atomically without restarting nodes (Req R5.1, R5.2)")
    void testDynamicRingReload() {
        List<String> initialMembers = List.of("node-a", "node-b");
        StaticMembershipSource membership = new StaticMembershipSource("cell-1", 1, initialMembers);

        NodeIdentity identityA = new NodeIdentity("cell-1", "node-a", NodeRole.OWNER);
        OwnershipResolver resolverA = new OwnershipResolver(identityA, membership);

        RoutingKey key = new RoutingKey("cell-1", "tenant-1", "ns-target");
        RouteBinding initialBinding = resolverA.resolve(key);
        assertThat(initialBinding.epoch()).isEqualTo(1L);

        // Dynamically reload with a new membership snapshot version 2 with node-c
        List<String> updatedMembers = List.of("node-a", "node-b", "node-c");
        CellMembership newMembership = new CellMembership("cell-1", 2, updatedMembers);

        resolverA.reloadRing(newMembership);

        RouteBinding reloadedBinding = resolverA.resolve(key);
        assertThat(reloadedBinding.epoch()).isEqualTo(2L);
        assertThat(updatedMembers).contains(reloadedBinding.ownerId());
        assertThat(resolverA.ring()).isPresent();
        assertThat(resolverA.ring().get().ringVersion()).isEqualTo(2);
        assertThat(resolverA.ring().get().members()).containsExactlyElementsOf(updatedMembers);
    }

    @Test
    @DisplayName("Dynamic reload rejects empty membership (Req R5.2, R7.3)")
    void testRejectEmptyMembershipReload() {
        StaticMembershipSource membership = new StaticMembershipSource("cell-1", 1, List.of("node-a"));
        OwnershipResolver resolver = new OwnershipResolver(new NodeIdentity("cell-1", "node-a", NodeRole.OWNER), membership);

        assertThatThrownBy(() -> resolver.reloadRing(new CellMembership("cell-1", 2, List.of())))
                .isInstanceOf(SpectorValidationException.class)
                .hasMessageContaining("membership must not be empty");
    }

    @Test
    @DisplayName("Standalone role safely ignores ring reload requests")
    void testStandaloneIgnoresRingReload() {
        OwnershipResolver standalone = OwnershipResolver.standalone();
        standalone.reloadRing(new CellMembership("cell-1", 5, List.of("node-1", "node-2")));

        assertThat(standalone.ring()).isEmpty();
        assertThat(standalone.ownsLocally(new RoutingKey("cell-1", "t", "ns"))).isTrue();
    }
}
