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
package com.spectrayan.spector.cluster.routing;

import com.spectrayan.spector.cluster.OwnershipResolver;
import com.spectrayan.spector.cluster.membership.StaticMembershipSource;
import com.spectrayan.spector.cluster.node.NodeIdentity;
import com.spectrayan.spector.cluster.node.NodeRole;
import com.spectrayan.spector.cluster.routing.cache.WaterfallRoutingResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Architectural guard asserting that exactly one hash implementation exists across the system,
 * ensuring gateway fallback and owner validation resolve identically for any key and member set
 * (ADR-0034 §8.3, Invariant K4, Req R6.2).
 */
class SingleHashImplementationGuardTest {

    @Test
    @DisplayName("Req K4: Gateway fallback and OwnerResolver resolve identically for all namespaces")
    void gatewayAndOwnerMustResolveIdentically() {
        List<String> members = List.of("node-1", "node-2", "node-3");
        ConsistentHashRing ring = ConsistentHashRing.of(1, members);

        // Gateway resolver using ring fallback (no Redis)
        WaterfallRoutingResolver gatewayResolver = new WaterfallRoutingResolver(
                ring,
                null, // degraded / no Redis
                Duration.ofSeconds(5),
                1000,
                30,
                RoutingMetricsListener.NOOP,
                Runnable::run
        );

        // Three owner resolvers representing each cell member
        OwnershipResolver owner1 = new OwnershipResolver(new NodeIdentity("cell-1", "node-1", NodeRole.OWNER), new StaticMembershipSource("cell-1", 1, members));
        OwnershipResolver owner2 = new OwnershipResolver(new NodeIdentity("cell-1", "node-2", NodeRole.OWNER), new StaticMembershipSource("cell-1", 1, members));
        OwnershipResolver owner3 = new OwnershipResolver(new NodeIdentity("cell-1", "node-3", NodeRole.OWNER), new StaticMembershipSource("cell-1", 1, members));

        // Test across 1,000 distinct namespace keys
        for (int i = 0; i < 1000; i++) {
            RoutingKey key = RoutingKey.ofTenanted("cell-1", "tenant-" + (i % 10), "ns-" + i);

            ResolvedRoute resolved = gatewayResolver.resolve(key);
            String chosenOwner = resolved.ownerId();

            // Assert that the exact node selected by gateway fallback claims ownership locally,
            // and the other two refuse (zero divergence, zero NOT_OWNER storm)
            boolean o1Claims = owner1.ownsLocally(key);
            boolean o2Claims = owner2.ownsLocally(key);
            boolean o3Claims = owner3.ownsLocally(key);

            int claimCount = (o1Claims ? 1 : 0) + (o2Claims ? 1 : 0) + (o3Claims ? 1 : 0);
            assertThat(claimCount)
                    .as("Exactly one node must claim ownership for key %s", key.keyMaterial())
                    .isEqualTo(1);

            if ("node-1".equals(chosenOwner)) {
                assertThat(o1Claims).isTrue();
            } else if ("node-2".equals(chosenOwner)) {
                assertThat(o2Claims).isTrue();
            } else if ("node-3".equals(chosenOwner)) {
                assertThat(o3Claims).isTrue();
            } else {
                org.junit.jupiter.api.Assertions.fail("Unexpected owner chosen: " + chosenOwner);
            }
        }
    }
}
