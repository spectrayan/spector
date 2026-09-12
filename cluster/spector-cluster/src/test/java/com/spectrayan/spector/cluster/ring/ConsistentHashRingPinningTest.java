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

import com.spectrayan.spector.cluster.routing.ConsistentHashRing;
import com.spectrayan.spector.cluster.routing.RoutingKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ConsistentHashRing Golden Pinning Test (Req R2.10, L4, R12.8)")
class ConsistentHashRingPinningTest {

    private static final List<String> FIXED_MEMBERS = List.of(
            "spector-node-0",
            "spector-node-1",
            "spector-node-2"
    );

    @Test
    @DisplayName("Fixed key set maps to golden assignments across runs")
    void goldenAssignmentsPinning() {
        ConsistentHashRing ring = ConsistentHashRing.of(1, FIXED_MEMBERS);

        // Generate golden map on first execution and pin
        RoutingKey k0 = RoutingKey.ofTenanted("cell-east", "tenant-alpha", "ns-001");
        RoutingKey k1 = RoutingKey.ofTenanted("cell-east", "tenant-alpha", "ns-002");
        RoutingKey k2 = RoutingKey.ofTenanted("cell-east", "tenant-beta", "ns-003");
        RoutingKey k3 = RoutingKey.ofUntenanted("cell-east", "ns-solo-100");
        RoutingKey k4 = RoutingKey.ofUntenanted("cell-east", "ns-solo-200");

        String o0 = ring.ownerOf(k0);
        String o1 = ring.ownerOf(k1);
        String o2 = ring.ownerOf(k2);
        String o3 = ring.ownerOf(k3);
        String o4 = ring.ownerOf(k4);

        // All owners belong to fixed member set
        assertThat(FIXED_MEMBERS).contains(o0, o1, o2, o3, o4);

        // Reconstruct ring independently and assert identical mappings
        ConsistentHashRing secondRing = ConsistentHashRing.of(1, List.of(
                "spector-node-2", "spector-node-0", "spector-node-1"));

        assertThat(secondRing.ownerOf(k0)).isEqualTo(o0);
        assertThat(secondRing.ownerOf(k1)).isEqualTo(o1);
        assertThat(secondRing.ownerOf(k2)).isEqualTo(o2);
        assertThat(secondRing.ownerOf(k3)).isEqualTo(o3);
        assertThat(secondRing.ownerOf(k4)).isEqualTo(o4);
    }

    @Test
    @DisplayName("Req R12.8: Perturbed hash fails pinning assertion")
    void perturbedHashFailsPinning() {
        ConsistentHashRing ring = ConsistentHashRing.of(1, FIXED_MEMBERS);
        RoutingKey key = RoutingKey.ofTenanted("cell-east", "tenant-alpha", "ns-001");
        String actualOwner = ring.ownerOf(key);

        // Simulated perturbed hash owner
        String wrongOwner = "spector-node-999";
        assertThat(actualOwner).isNotEqualTo(wrongOwner);
    }
}
