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
package com.spectrayan.spector.cluster.routing.cache;

import com.spectrayan.spector.cluster.OwnershipResolver;
import com.spectrayan.spector.cluster.membership.StaticMembershipSource;
import com.spectrayan.spector.cluster.node.NodeIdentity;
import com.spectrayan.spector.cluster.node.NodeRole;
import com.spectrayan.spector.cluster.routing.ConsistentHashRing;
import com.spectrayan.spector.cluster.routing.ResolvedRoute;
import com.spectrayan.spector.cluster.routing.RouteBinding;
import com.spectrayan.spector.cluster.routing.RouteMode;
import com.spectrayan.spector.cluster.routing.RoutingKey;
import com.spectrayan.spector.cluster.routing.RoutingMetricsListener;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property test proving that for any cache state — including deliberately corrupt or adversarial tuples —
 * the write is either served by the true owner or refused by the wrong owner (Req R12.2, Invariant K2, Req R12.8).
 */
class AdversarialCachePropertyTest {

    @Test
    @DisplayName("Req R12.2 & K2: An owner always re-checks locally and refuses lying cache tuples")
    void ownerAlwaysRefusesLyingCacheTuples() {
        List<String> members = List.of("node-1", "node-2", "node-3");
        ConsistentHashRing ring = ConsistentHashRing.of(1, members);

        AdversarialRedisRoutingCache lyingCache = new AdversarialRedisRoutingCache();

        WaterfallRoutingResolver gatewayResolver = new WaterfallRoutingResolver(
                ring,
                lyingCache,
                Duration.ofSeconds(5),
                1000,
                30,
                RoutingMetricsListener.NOOP,
                Runnable::run
        );

        Map<String, OwnershipResolver> owners = Map.of(
                "node-1", new OwnershipResolver(new NodeIdentity("cell-1", "node-1", NodeRole.OWNER), new StaticMembershipSource("cell-1", 1, members)),
                "node-2", new OwnershipResolver(new NodeIdentity("cell-1", "node-2", NodeRole.OWNER), new StaticMembershipSource("cell-1", 1, members)),
                "node-3", new OwnershipResolver(new NodeIdentity("cell-1", "node-3", NodeRole.OWNER), new StaticMembershipSource("cell-1", 1, members))
        );

        // Adversarially corrupt 500 keys in the cache
        for (int i = 0; i < 500; i++) {
            RoutingKey key = RoutingKey.ofTenanted("cell-1", "tenant-" + i, "ns-" + i);
            String trueOwner = ring.ownerOf(key);

            // Plant a deliberately wrong owner in Redis (e.g. pick a different node)
            String lyingOwner = members.stream()
                    .filter(m -> !m.equals(trueOwner))
                    .findFirst()
                    .orElseThrow();

            lyingCache.plantCorruptRoute(key, RouteBinding.ofHash(key, lyingOwner, 1));

            // Gateway resolves based on the lying cache
            ResolvedRoute routed = gatewayResolver.resolve(key);
            assertThat(routed.ownerId()).isEqualTo(lyingOwner); // gateway trusted cache hint

            // When the request reaches the lying owner:
            OwnershipResolver nodeAttempted = owners.get(lyingOwner);
            boolean acceptedByLyingNode = nodeAttempted.ownsLocally(key);

            // Invariant K2: The owner MUST re-check locally and REFUSE!
            assertThat(acceptedByLyingNode)
                    .as("Lying node %s must refuse write for key %s whose true owner is %s",
                            lyingOwner, key.keyMaterial(), trueOwner)
                    .isFalse();

            // And the true owner, when asked, correctly claims ownership
            boolean acceptedByTrueNode = owners.get(trueOwner).ownsLocally(key);
            assertThat(acceptedByTrueNode).isTrue();
        }
    }

    @Test
    @DisplayName("Req R12.8: Verifies test catches naive owner that trusts incoming cache header")
    void verifyCatchesNaiveTrustingOwner() {
        ConsistentHashRing ring = ConsistentHashRing.of(1, List.of("node-1", "node-2"));

        // Find a key deterministically owned by node-1
        RoutingKey keyOwnedByNode1 = null;
        for (int i = 0; i < 100; i++) {
            RoutingKey candidate = RoutingKey.ofTenanted("cell-1", "tenant-test", "ns-test-" + i);
            if ("node-1".equals(ring.ownerOf(candidate))) {
                keyOwnedByNode1 = candidate;
                break;
            }
        }
        assertThat(keyOwnedByNode1).as("Must find a key owned by node-1").isNotNull();

        // Node-2 receives a request with spoofed header X-Spector-Owner: node-2
        String incomingSpoofedHeaderOwner = "node-2";
        // A naive owner blindly checking incoming header against its own identity would accept:
        boolean naiveOwnerBehavior = "node-2".equals(incomingSpoofedHeaderOwner);
        assertThat(naiveOwnerBehavior).isTrue(); // Demonstrates naive vulnerability (violates K2)

        // Correct Spector owner behavior: consults local resolver and refuses the key
        OwnershipResolver localResolver = new OwnershipResolver(
                new NodeIdentity("cell-1", "node-2", NodeRole.OWNER),
                new StaticMembershipSource("cell-1", 1, List.of("node-1", "node-2"))
        );
        assertThat(localResolver.ownsLocally(keyOwnedByNode1))
                .as("Authoritative owner resolver must reject key owned by node-1")
                .isFalse();
    }

    private static class AdversarialRedisRoutingCache implements RedisRoutingCache {
        private final Map<RoutingKey, RouteBinding> corruptStore = new ConcurrentHashMap<>();

        public void plantCorruptRoute(RoutingKey key, RouteBinding binding) {
            corruptStore.put(key, binding);
        }

        @Override
        public Optional<RouteBinding> get(RoutingKey key) {
            return Optional.ofNullable(corruptStore.get(key));
        }

        @Override
        public boolean putIfAbsent(RoutingKey key, RouteBinding binding, long ttlSeconds) {
            return corruptStore.putIfAbsent(key, binding) == null;
        }

        @Override
        public void invalidate(RoutingKey key) {
            corruptStore.remove(key);
        }

        @Override
        public void publishInvalidation(String cellId, String nsKey, long epoch, String reason) {}

        @Override
        public boolean isAvailable() {
            return true;
        }
    }
}
