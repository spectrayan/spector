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
package com.spectrayan.spector.cluster.store;

import com.spectrayan.spector.cluster.membership.CellMembership;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ControlStoreContractTest {

    interface StoreFactory {
        ControlStore create(Clock clock);
    }

    static Stream<StoreFactory> controlStoreProviders(@TempDir Path tempDir) {
        return Stream.of(
                InMemoryControlStore::new,
                clock -> new FileControlStore(tempDir.resolve("test-control-store.json"), clock)
        );
    }

    @ParameterizedTest
    @MethodSource("controlStoreProviders")
    @DisplayName("Coordinator lease election and renewal lifecycle conforms to contract (Req R1.2, R1.5, G17, G22)")
    void testCoordinatorLeaseLifecycle(StoreFactory factory) {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-09-12T00:00:00Z"));
        Clock mutableClock = new Clock() {
            @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public Instant instant() { return now.get(); }
        };

        ControlStore store = factory.create(mutableClock);

        // Initially no coordinator
        assertThat(store.getCoordinatorLease()).isNull();
        assertThat(store.getValidCoordinatorLease()).isEmpty();

        // Node 1 acquires lease for 15 seconds
        Optional<CoordinatorLease> acquired = store.acquireOrRenewCoordinatorLease("node-1", Duration.ofSeconds(15));
        assertThat(acquired).isPresent();

        CoordinatorLease lease = acquired.get();
        assertThat(lease.holderNodeId()).isEqualTo("node-1");
        assertThat(lease.leaseVersion()).isEqualTo(1L);
        assertThat(store.isCoordinator("node-1", 1L)).isTrue();
        assertThat(store.isCoordinator("node-2", 1L)).isFalse();
        assertThat(store.isCoordinator("node-1", 2L)).isFalse();

        // Node 2 tries to acquire before expiry -> rejected
        Optional<CoordinatorLease> node2Acquired = store.acquireOrRenewCoordinatorLease("node-2", Duration.ofSeconds(15));
        assertThat(node2Acquired).isEmpty();

        // Node 1 renews at t = 10s
        now.set(now.get().plusSeconds(10));
        Optional<CoordinatorLease> renewed = store.acquireOrRenewCoordinatorLease("node-1", Duration.ofSeconds(15));
        assertThat(renewed).isPresent();
        assertThat(renewed.get().leaseVersion()).isEqualTo(2L);
        assertThat(store.isCoordinator("node-1", 2L)).isTrue();

        // Time advances beyond expiration (t = 10 + 20 = 30s)
        now.set(now.get().plusSeconds(20));
        assertThat(store.getValidCoordinatorLease()).isEmpty();
        assertThat(store.isCoordinator("node-1", 2L)).isFalse();

        // Now Node 2 can acquire expired lease
        Optional<CoordinatorLease> node2AcquiredAfterExpiry = store.acquireOrRenewCoordinatorLease("node-2", Duration.ofSeconds(15));
        assertThat(node2AcquiredAfterExpiry).isPresent();
        assertThat(node2AcquiredAfterExpiry.get().holderNodeId()).isEqualTo("node-2");
        assertThat(node2AcquiredAfterExpiry.get().leaseVersion()).isEqualTo(3L);
        assertThat(store.isCoordinator("node-2", 3L)).isTrue();

        // Node 2 releases lease
        store.releaseCoordinatorLease("node-2");
        assertThat(store.getValidCoordinatorLease()).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("controlStoreProviders")
    @DisplayName("Namespace epoch advances monotonically per namespace (Req R2.1, Q3)")
    void testNamespaceEpochMonotonicity(StoreFactory factory) {
        ControlStore store = factory.create(Clock.systemUTC());

        assertThat(store.getNamespaceEpoch("ns-1")).isEqualTo(0L);
        assertThat(store.getNamespaceEpoch("ns-2")).isEqualTo(0L);

        long e1 = store.advanceNamespaceEpoch("ns-1");
        assertThat(e1).isEqualTo(1L);
        assertThat(store.getNamespaceEpoch("ns-1")).isEqualTo(1L);

        long e2 = store.advanceNamespaceEpoch("ns-1");
        assertThat(e2).isEqualTo(2L);
        assertThat(store.getNamespaceEpoch("ns-1")).isEqualTo(2L);

        // Independent namespace
        assertThat(store.getNamespaceEpoch("ns-2")).isEqualTo(0L);
        assertThat(store.advanceNamespaceEpoch("ns-2")).isEqualTo(1L);
    }

    @ParameterizedTest
    @MethodSource("controlStoreProviders")
    @DisplayName("Override leases support pinning, retrieval, expiry, and listing (Req R3.1, R3.3, R3.6)")
    void testOverrideLeases(StoreFactory factory) {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-09-12T00:00:00Z"));
        Clock mutableClock = new Clock() {
            @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public Instant instant() { return now.get(); }
        };

        ControlStore store = factory.create(mutableClock);

        // Set override for ns-alpha to node-2 until t + 60s
        OverrideLeaseRecord record = new OverrideLeaseRecord(
                "ns-alpha",
                "node-2",
                1L,
                "fence-1",
                now.get().plusSeconds(60)
        );
        store.setOverride(record);

        Optional<OverrideLeaseRecord> retrieved = store.getOverride("ns-alpha");
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().targetNodeId()).isEqualTo("node-2");
        assertThat(retrieved.get().fence()).isEqualTo("fence-1");

        List<OverrideLeaseRecord> active = store.listOverrides();
        assertThat(active).hasSize(1);

        // Advance time past expiry
        now.set(now.get().plusSeconds(70));
        assertThat(store.getOverride("ns-alpha")).isEmpty();
        assertThat(store.listOverrides()).isEmpty();

        // Re-set and explicit remove
        OverrideLeaseRecord renewed = new OverrideLeaseRecord(
                "ns-alpha",
                "node-3",
                2L,
                "fence-2",
                now.get().plusSeconds(60)
        );
        store.setOverride(renewed);
        assertThat(store.getOverride("ns-alpha")).isPresent();

        store.removeOverride("ns-alpha");
        assertThat(store.getOverride("ns-alpha")).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("controlStoreProviders")
    @DisplayName("Cell membership updates ring version and member set (Req R1.1)")
    void testMembershipUpdates(StoreFactory factory) {
        ControlStore store = factory.create(Clock.systemUTC());

        assertThat(store.getMembership()).isNull();

        CellMembership initial = new CellMembership("cell-1", 1, List.of("node-1", "node-2"));
        store.updateMembership(initial);

        assertThat(store.getMembership()).isEqualTo(initial);

        CellMembership updated = new CellMembership("cell-1", 2, List.of("node-1", "node-2", "node-3"));
        store.updateMembership(updated);

        assertThat(store.getMembership()).isEqualTo(updated);
        assertThat(store.getMembership().ringVersion()).isEqualTo(2);
    }

    @ParameterizedTest
    @MethodSource("controlStoreProviders")
    @DisplayName("Single-writer CAS enforces expected lease version on mutations (Req R1.1, G16)")
    void testSingleWriterCasEnforcement(StoreFactory factory) {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-09-12T00:00:00Z"));
        Clock mutableClock = new Clock() {
            @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public Instant instant() { return now.get(); }
        };

        ControlStore store = factory.create(mutableClock);

        // Elect coordinator v1
        store.acquireOrRenewCoordinatorLease("coord-1", Duration.ofSeconds(60));

        // CAS with matching version succeeds
        assertThat(store.advanceNamespaceEpoch("ns-cas", 1L)).isEqualTo(1L);

        OverrideLeaseRecord record = new OverrideLeaseRecord(
                "ns-cas", "target-node", 1L, "fence-1", now.get().plusSeconds(60)
        );
        assertThat(store.setOverride(record, 1L)).isTrue();
        assertThat(store.getOverride("ns-cas")).isPresent();

        // CAS with stale or wrong version fails
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> store.advanceNamespaceEpoch("ns-cas", 999L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Control store CAS failure");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> store.setOverride(record, 999L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Control store CAS failure");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> store.removeOverride("ns-cas", 999L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Control store CAS failure");

        // Remove with matching version succeeds
        assertThat(store.removeOverride("ns-cas", 1L)).isTrue();
        assertThat(store.getOverride("ns-cas")).isEmpty();
    }
}
