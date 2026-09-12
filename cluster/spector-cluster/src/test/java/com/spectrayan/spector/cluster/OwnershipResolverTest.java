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
package com.spectrayan.spector.cluster;

import com.spectrayan.spector.cluster.membership.StaticMembershipSource;
import com.spectrayan.spector.cluster.node.NodeIdentity;
import com.spectrayan.spector.cluster.node.NodeRole;
import com.spectrayan.spector.cluster.routing.RouteBinding;
import com.spectrayan.spector.cluster.routing.RouteMode;
import com.spectrayan.spector.cluster.routing.RoutingKey;
import com.spectrayan.spector.commons.error.SpectorValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OwnershipResolverTest {

    private final RoutingKey key1 = new RoutingKey("cell-1", "tenant-1", "ns-1");
    private final RoutingKey key2 = new RoutingKey("cell-1", "tenant-2", "ns-2");

    @Test
    @DisplayName("STANDALONE role short-circuits ring and owns everything locally (Invariant J4, Req R4.3)")
    void testStandaloneRoleOwnsAllLocally() {
        OwnershipResolver resolver = OwnershipResolver.standalone();

        assertThat(resolver.ownsLocally(key1)).isTrue();
        assertThat(resolver.ownsLocally(key2)).isTrue();
        assertThat(resolver.ring()).isEmpty();

        RouteBinding binding = resolver.resolve(key1);
        assertThat(binding.key()).isEqualTo(key1);
        assertThat(binding.ownerId()).isEqualTo("standalone");
        assertThat(binding.epoch()).isEqualTo(0L);
        assertThat(binding.mode()).isEqualTo(RouteMode.HASH);
    }

    @Test
    @DisplayName("STANDALONE role preserves custom nodeId if provided")
    void testStandaloneWithExplicitNodeId() {
        NodeIdentity identity = new NodeIdentity("cell-1", "custom-node", NodeRole.STANDALONE);
        OwnershipResolver resolver = new OwnershipResolver(identity, null);

        assertThat(resolver.ownsLocally(key1)).isTrue();
        RouteBinding binding = resolver.resolve(key1);
        assertThat(binding.ownerId()).isEqualTo("custom-node");
    }

    @Test
    @DisplayName("OWNER role checks Ketama ring and matches against nodeId")
    void testOwnerRoleRingConsultation() {
        List<String> members = List.of("node-a", "node-b", "node-c");
        StaticMembershipSource membership = new StaticMembershipSource("cell-1", 42, members);

        OwnershipResolver resolverA = new OwnershipResolver(new NodeIdentity("cell-1", "node-a", NodeRole.OWNER), membership);
        OwnershipResolver resolverB = new OwnershipResolver(new NodeIdentity("cell-1", "node-b", NodeRole.OWNER), membership);
        OwnershipResolver resolverC = new OwnershipResolver(new NodeIdentity("cell-1", "node-c", NodeRole.OWNER), membership);

        RouteBinding binding = resolverA.resolve(key1);
        String expectedOwner = binding.ownerId();
        assertThat(binding.epoch()).isEqualTo(42L);
        assertThat(members).contains(expectedOwner);

        // Exactly one resolver must own key1
        boolean aOwns = resolverA.ownsLocally(key1);
        boolean bOwns = resolverB.ownsLocally(key1);
        boolean cOwns = resolverC.ownsLocally(key1);

        assertThat(aOwns).isEqualTo("node-a".equals(expectedOwner));
        assertThat(bOwns).isEqualTo("node-b".equals(expectedOwner));
        assertThat(cOwns).isEqualTo("node-c".equals(expectedOwner));

        int ownerCount = (aOwns ? 1 : 0) + (bOwns ? 1 : 0) + (cOwns ? 1 : 0);
        assertThat(ownerCount).isEqualTo(1);
    }

    @Test
    @DisplayName("REPLICA role always refuses local ownership but resolves ring owner (Req R5.3)")
    void testReplicaRoleRefusesLocalOwnership() {
        List<String> members = List.of("node-a", "node-b");
        StaticMembershipSource membership = new StaticMembershipSource("cell-1", 1, members);
        OwnershipResolver replica = new OwnershipResolver(new NodeIdentity("cell-1", "node-a", NodeRole.REPLICA), membership);

        assertThat(replica.ownsLocally(key1)).isFalse();
        RouteBinding binding = replica.resolve(key1);
        assertThat(members).contains(binding.ownerId());
        assertThat(binding.epoch()).isEqualTo(1L);
    }

    @Test
    @DisplayName("GATEWAY role always refuses local ownership but resolves ring owner (Req D3)")
    void testGatewayRoleRefusesLocalOwnership() {
        List<String> members = List.of("node-a", "node-b");
        StaticMembershipSource membership = new StaticMembershipSource("cell-1", 1, members);
        OwnershipResolver gateway = new OwnershipResolver(new NodeIdentity("cell-1", "node-gw", NodeRole.GATEWAY), membership);

        assertThat(gateway.ownsLocally(key1)).isFalse();
        RouteBinding binding = gateway.resolve(key1);
        assertThat(members).contains(binding.ownerId());
        assertThat(binding.epoch()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Non-standalone roles fail closed when membershipSource is null or empty (Req R7.3, L2)")
    void testNonStandaloneFailsClosedWithoutMembership() {
        NodeIdentity ownerIdentity = new NodeIdentity("cell-1", "node-a", NodeRole.OWNER);

        assertThatThrownBy(() -> new OwnershipResolver(ownerIdentity, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("membershipSource must not be null");
    }

    @Test
    @DisplayName("G42: OWNER role fails closed when nodeId is not present in membership members")
    void testOwnerRoleFailsClosedWhenNodeIdAbsentFromMembership() {
        List<String> members = List.of("node-b", "node-c");
        StaticMembershipSource membership = new StaticMembershipSource("cell-1", 1, members);
        NodeIdentity ownerIdentity = new NodeIdentity("cell-1", "node-absent", NodeRole.OWNER);

        assertThatThrownBy(() -> new OwnershipResolver(ownerIdentity, membership))
                .isInstanceOf(SpectorValidationException.class)
                .hasMessageContaining("must be present in membership members");
    }

    @Test
    @DisplayName("G42: OWNER reloadRing fails closed when nodeId is missing from updated membership")
    void testOwnerReloadRingFailsClosedWhenNodeIdMissing() {
        List<String> members = List.of("node-a", "node-b");
        StaticMembershipSource membership = new StaticMembershipSource("cell-1", 1, members);
        OwnershipResolver resolver = new OwnershipResolver(new NodeIdentity("cell-1", "node-a", NodeRole.OWNER), membership);

        com.spectrayan.spector.cluster.membership.CellMembership newMembership =
                new com.spectrayan.spector.cluster.membership.CellMembership("cell-1", 2, List.of("node-b", "node-c"));

        assertThatThrownBy(() -> resolver.reloadRing(newMembership))
                .isInstanceOf(SpectorValidationException.class)
                .hasMessageContaining("must be present in reloaded membership members");
    }
}
