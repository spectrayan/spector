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

import com.spectrayan.spector.cluster.coordinator.CoordinatorLeaseManager;
import com.spectrayan.spector.cluster.store.InMemoryControlStore;
import com.spectrayan.spector.cluster.store.OverrideLeaseRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OverrideLeaseManagerTest {

    static final class MutableClock extends Clock {
        private final AtomicReference<Instant> current;

        MutableClock(Instant initial) {
            this.current = new AtomicReference<>(initial);
        }

        void advance(Duration duration) {
            current.updateAndGet(t -> t.plus(duration));
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current.get();
        }
    }

    @Test
    @DisplayName("Coordinator can create, renew, and remove override leases (Req R3.1, R3.2)")
    void testCoordinatorAuthorityAndLifecycle() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-12T00:00:00Z"));
        InMemoryControlStore store = new InMemoryControlStore(clock);
        CoordinatorLeaseManager coordMgr = new CoordinatorLeaseManager(store, "node-coord", Duration.ofSeconds(15), Duration.ofSeconds(10));
        coordMgr.heartbeat();

        OverrideLeaseManager manager = new OverrideLeaseManager(store, coordMgr, Duration.ofSeconds(60));

        OverrideLeaseRecord record = manager.setOverride("ns-alpha", "node-survivor", "fence-100", Duration.ofSeconds(60));
        assertThat(record.namespaceId()).isEqualTo("ns-alpha");
        assertThat(record.targetNodeId()).isEqualTo("node-survivor");
        assertThat(record.fence()).isEqualTo("fence-100");
        assertThat(record.epoch()).isEqualTo(1L);

        Optional<OverrideLeaseRecord> retrieved = manager.getOverride("ns-alpha");
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().targetNodeId()).isEqualTo("node-survivor");

        // Renew override
        OverrideLeaseRecord renewed = manager.renewOverride("ns-alpha", Duration.ofSeconds(120));
        assertThat(renewed.expiresAt()).isEqualTo(clock.instant().plus(Duration.ofSeconds(120)));

        // Remove override
        manager.removeOverride("ns-alpha");
        assertThat(manager.getOverride("ns-alpha")).isEmpty();
    }

    @Test
    @DisplayName("Non-coordinator mutations are rejected (Req R3.2, Q4)")
    void testNonCoordinatorRejected() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-12T00:00:00Z"));
        InMemoryControlStore store = new InMemoryControlStore(clock);
        CoordinatorLeaseManager coordMgr = new CoordinatorLeaseManager(store, "node-follower", Duration.ofSeconds(15), Duration.ofSeconds(10));
        // Node does NOT acquire coordinator lease

        OverrideLeaseManager manager = new OverrideLeaseManager(store, coordMgr, Duration.ofSeconds(60));

        assertThatThrownBy(() -> manager.setOverride("ns-alpha", "node-survivor", "fence-1", Duration.ofSeconds(60)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Only the active cell coordinator may create, update, or remove override leases");

        assertThatThrownBy(() -> manager.removeOverride("ns-alpha"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Only the active cell coordinator may create, update, or remove override leases");
    }

    @Test
    @DisplayName("Override leases expire after TTL (Req R3.3)")
    void testOverrideTtlExpiration() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-12T00:00:00Z"));
        InMemoryControlStore store = new InMemoryControlStore(clock);
        CoordinatorLeaseManager coordMgr = new CoordinatorLeaseManager(store, "node-coord", Duration.ofSeconds(15), Duration.ofSeconds(10));
        coordMgr.heartbeat();

        OverrideLeaseManager manager = new OverrideLeaseManager(store, coordMgr, Duration.ofSeconds(30));
        manager.setOverride("ns-beta", "node-survivor", "fence-1", Duration.ofSeconds(30));

        assertThat(manager.getOverride("ns-beta")).isPresent();
        assertThat(manager.getActiveOverrideCount()).isEqualTo(1);

        // Advance past TTL
        clock.advance(Duration.ofSeconds(31));

        assertThat(manager.getOverride("ns-beta")).isEmpty();
        assertThat(manager.getActiveOverrideCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("Active override count and max age are observable (Req R3.6)")
    void testActiveOverrideMetrics() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-12T00:00:00Z"));
        InMemoryControlStore store = new InMemoryControlStore(clock);
        CoordinatorLeaseManager coordMgr = new CoordinatorLeaseManager(store, "node-coord", Duration.ofSeconds(60), Duration.ofSeconds(10));
        coordMgr.heartbeat();
        OverrideLeaseManager manager = new OverrideLeaseManager(store, coordMgr, Duration.ofSeconds(300));

        assertThat(manager.getActiveOverrideCount()).isEqualTo(0);
        assertThat(manager.getMaxOverrideAge()).isEqualTo(Duration.ZERO);

        store.setOverride(new OverrideLeaseRecord(
                "ns-1", "node-1", 1L, "f-1",
                clock.instant().plusSeconds(60), clock.instant().minusSeconds(10)
        ), coordMgr.getLeaseVersion());
        store.setOverride(new OverrideLeaseRecord(
                "ns-2", "node-2", 1L, "f-2",
                clock.instant().plusSeconds(60), clock.instant().minusSeconds(35)
        ), coordMgr.getLeaseVersion());

        assertThat(manager.getActiveOverrideCount()).isEqualTo(2);
        assertThat(manager.getMaxOverrideAge()).isEqualTo(Duration.ofSeconds(35));
        assertThat(manager.getOverrideAge("ns-1")).contains(Duration.ofSeconds(10));
    }

    @Test
    @DisplayName("Safely checks hash-default owner liveness to prevent black holes (Req R3.4)")
    void testBlackHoleSafetyCheck() {
        InMemoryControlStore store = new InMemoryControlStore();
        CoordinatorLeaseManager coordMgr = new CoordinatorLeaseManager(store, "node-coord", Duration.ofSeconds(60), Duration.ofSeconds(10));
        coordMgr.heartbeat();
        OverrideLeaseManager manager = new OverrideLeaseManager(store, coordMgr, Duration.ofSeconds(300));

        boolean safeWhenDead = manager.canSafelyExpireOrRemove("ns-1", "dead-node", node -> false);
        assertThat(safeWhenDead).isFalse();

        boolean safeWhenAlive = manager.canSafelyExpireOrRemove("ns-1", "live-node", "live-node"::equals);
        assertThat(safeWhenAlive).isTrue();
    }

    @Test
    @DisplayName("G19: Authority check is hoisted before epoch bump — non-coordinator never burns epochs")
    void testAuthorityCheckHoistedBeforeEpochBump() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-12T00:00:00Z"));
        InMemoryControlStore store = new InMemoryControlStore(clock);
        CoordinatorLeaseManager follower = new CoordinatorLeaseManager(store, "follower-node", Duration.ofSeconds(15), Duration.ofSeconds(10));
        // follower does NOT heartbeat/acquire lease

        OverrideLeaseManager manager = new OverrideLeaseManager(store, follower, Duration.ofSeconds(60));

        assertThatThrownBy(() -> manager.setOverride("ns-target", "survivor", "f-1", Duration.ofSeconds(60)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Only the active cell coordinator may create, update, or remove override leases");

        // Monotonic epoch was NEVER burned/incremented!
        assertThat(store.getNamespaceEpoch("ns-target")).isEqualTo(0L);
    }

    @Test
    @DisplayName("G21: Active reaper renews override when hash owner is dead and safely removes when alive and verified")
    void testActiveOverrideReaperRenewsWhenOwnerDeadAndRemovesWhenAlive() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-12T00:00:00Z"));
        InMemoryControlStore store = new InMemoryControlStore(clock);
        CoordinatorLeaseManager coordMgr = new CoordinatorLeaseManager(store, "coord-node", Duration.ofSeconds(60), Duration.ofSeconds(10));
        coordMgr.heartbeat();

        OverrideLeaseManager manager = new OverrideLeaseManager(store, coordMgr, Duration.ofSeconds(60));
        manager.setOverride("ns-active", "survivor-1", "f-1", Duration.ofSeconds(40));

        // Advance 20 seconds: remaining TTL = 20s (< 30s threshold)
        clock.advance(Duration.ofSeconds(20));

        // 1. Hash owner is dead: reaper renews override to hold pin (never snaps back to dead owner)
        manager.reapOrRenewOverrides(ns -> "hash-dead", node -> false, (ns, node) -> false);
        Optional<OverrideLeaseRecord> renewed = manager.getOverride("ns-active");
        assertThat(renewed).isPresent();
        assertThat(renewed.get().expiresAt()).isAfter(clock.instant().plusSeconds(30));

        // 2. Hash owner is alive and verified: reaper safely removes override
        manager.reapOrRenewOverrides(ns -> "hash-alive", node -> true, (ns, node) -> true);
        assertThat(manager.getOverride("ns-active")).isEmpty();
    }
}
