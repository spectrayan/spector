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
package com.spectrayan.spector.cluster.coordinator;

import com.spectrayan.spector.cluster.membership.CellMembership;
import com.spectrayan.spector.cluster.store.ControlStore;
import com.spectrayan.spector.cluster.store.CoordinatorLease;
import com.spectrayan.spector.cluster.store.InMemoryControlStore;
import com.spectrayan.spector.cluster.store.OverrideLeaseRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CoordinatorElectionChaosTest {

    @Test
    @DisplayName("Chaos: Kill coordinator degrades to leaderless without wrong owner or dual coordinators (Req R1.4, Q5)")
    void testKillCoordinatorDegradesGracefully() {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-09-12T00:00:00Z"));
        Clock mutableClock = new Clock() {
            @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public Instant instant() { return now.get(); }
        };

        ControlStore store = new InMemoryControlStore(mutableClock);

        CoordinatorLeaseManager node1 = new CoordinatorLeaseManager(
                store, "node-1", Duration.ofSeconds(15), Duration.ofSeconds(10)
        );
        CoordinatorLeaseManager node2 = new CoordinatorLeaseManager(
                store, "node-2", Duration.ofSeconds(15), Duration.ofSeconds(10)
        );

        // 1. Initial election: Node 1 acquires lease
        node1.heartbeat();
        assertThat(node1.isCoordinator()).isTrue();
        assertThat(node1.checkStoreEnforcedLeaseActive()).isTrue();
        assertThat(store.getCoordinatorLease().holderNodeId()).isEqualTo("node-1");

        // Node 2 tries to run heartbeat, fails to acquire active lease
        node2.heartbeat();
        assertThat(node2.isCoordinator()).isFalse();
        assertThat(node2.checkStoreEnforcedLeaseActive()).isFalse();

        // 2. Simulate Node 1 killed: Node 1 ceases heartbeats.
        // During lease validity window (t = 5s), cell is still protected: Node 2 still cannot take over
        now.set(now.get().plusSeconds(5));
        node2.heartbeat();
        assertThat(node2.isCoordinator()).isFalse();

        // 3. Lease expires in store (t = 16s > 15s)
        now.set(now.get().plusSeconds(11)); // total +16s from start
        assertThat(store.getCoordinatorLease().isExpired(now.get())).isTrue();

        // Local self-fence must already report isCoordinator() == false before checkStoreEnforcedLeaseActive() (#1195)
        assertThat(node1.isCoordinator())
                .as("Coordinator must self-fence locally once its lease deadline passes")
                .isFalse();
        assertThat(node1.checkStoreEnforcedLeaseActive()).isFalse();
        assertThat(node1.isCoordinator()).isFalse();

        // 4. Survivor Node 2 runs heartbeat and cleanly assumes coordinator role
        node2.heartbeat();
        assertThat(node2.isCoordinator()).isTrue();
        assertThat(node2.checkStoreEnforcedLeaseActive()).isTrue();
        assertThat(store.getCoordinatorLease().holderNodeId()).isEqualTo("node-2");

        // Clean up
        node1.close();
        node2.close();
    }

    @Test
    @DisplayName("Immediate Stop: Node losing lease ceases coordinator mutations immediately (Req R1.6)")
    void testNodeLosingLeaseCeasesMutationsImmediately() {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-09-12T00:00:00Z"));
        Clock mutableClock = new Clock() {
            @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public Instant instant() { return now.get(); }
        };

        ControlStore store = new InMemoryControlStore(mutableClock);
        CoordinatorLeaseManager manager = new CoordinatorLeaseManager(
                store, "node-1", Duration.ofSeconds(15), Duration.ofSeconds(10)
        );

        manager.heartbeat();
        assertThat(manager.isCoordinator()).isTrue();
        assertThat(manager.checkStoreEnforcedLeaseActive()).isTrue();
        assertThat(manager.getLeaseGeneration()).isEqualTo(1L);

        java.util.concurrent.atomic.AtomicBoolean notified = new java.util.concurrent.atomic.AtomicBoolean(false);
        manager.addLeaseLossListener(reason -> notified.set(true));

        // Expire lease by advancing store clock
        now.set(now.get().plusSeconds(20));

        // Attempting coordinator action checks store and immediately stops (G23)
        boolean active = manager.checkStoreEnforcedLeaseActive();
        assertThat(active).isFalse();
        assertThat(manager.isCoordinator()).isFalse();
        assertThat(manager.getLeaseGeneration()).isEqualTo(2L);
        assertThat(notified.get()).isTrue();

        manager.close();
    }

    @Test
    @DisplayName("Self-Fence (#1195): Stalled store call causes isCoordinator() to turn false before store expiry and discards late renewal")
    void testStalledStoreSelfFencesBeforeStoreExpiryAndDiscardsLateRenewal() {
        AtomicReference<Instant> storeNow = new AtomicReference<>(Instant.parse("2026-09-12T00:00:00Z"));
        AtomicLong nanoNow = new AtomicLong(1_000_000_000L);
        Clock mutableClock = new Clock() {
            @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public Instant instant() { return storeNow.get(); }
        };

        InMemoryControlStore backingStore = new InMemoryControlStore(mutableClock);
        AtomicBoolean stallOnNextRenew = new AtomicBoolean(false);
        AtomicBoolean isCoordDuringStallBeforeStoreExpiry = new AtomicBoolean(true);
        AtomicBoolean storeLeaseStillValidAtSelfFence = new AtomicBoolean(false);
        AtomicReference<CoordinatorLeaseManager> managerRef = new AtomicReference<>();

        ControlStore interceptingStore = new ControlStore() {
            @Override public CellMembership getMembership() { return backingStore.getMembership(); }
            @Override public void updateMembership(CellMembership m) { backingStore.updateMembership(m); }
            @Override public CoordinatorLease getCoordinatorLease() { return backingStore.getCoordinatorLease(); }
            @Override public Optional<CoordinatorLease> getValidCoordinatorLease() { return backingStore.getValidCoordinatorLease(); }
            @Override public boolean isCoordinator(String nodeId, long expectedLeaseVersion) { return backingStore.isCoordinator(nodeId, expectedLeaseVersion); }
            @Override
            public Optional<CoordinatorLease> acquireOrRenewCoordinatorLease(String candidateNodeId, Duration duration) {
                Optional<CoordinatorLease> result = backingStore.acquireOrRenewCoordinatorLease(candidateNodeId, duration);
                if (stallOnNextRenew.get()) {
                    // Advance deterministic clock to t = 14s (equal to leaseDuration 15s - safetyMargin 1s):
                    // The original lease in the store expires at t = 15s, so it has NOT expired in the store yet!
                    storeNow.set(Instant.parse("2026-09-12T00:00:14Z"));
                    nanoNow.addAndGet(Duration.ofSeconds(9).toNanos()); // from t=5s to t=14s
                    storeLeaseStillValidAtSelfFence.set(!backingStore.getCoordinatorLease().isExpired(storeNow.get()));
                    isCoordDuringStallBeforeStoreExpiry.set(managerRef.get().isCoordinator());

                    // Then the stalled renewal response finally arrives at t = 20s (past the renewal's local deadline 5s + 14s = 19s)
                    storeNow.set(Instant.parse("2026-09-12T00:00:20Z"));
                    nanoNow.addAndGet(Duration.ofSeconds(6).toNanos());
                }
                return result;
            }
            @Override public void releaseCoordinatorLease(String nodeId) { backingStore.releaseCoordinatorLease(nodeId); }
            @Override public long getNamespaceEpoch(String ns) { return backingStore.getNamespaceEpoch(ns); }
            @Override public long advanceNamespaceEpoch(String ns, long v) { return backingStore.advanceNamespaceEpoch(ns, v); }
            @Override public Optional<OverrideLeaseRecord> getOverride(String ns) { return backingStore.getOverride(ns); }
            @Override public boolean setOverride(OverrideLeaseRecord o, long v) { return backingStore.setOverride(o, v); }
            @Override public boolean removeOverride(String ns, long v) { return backingStore.removeOverride(ns, v); }
            @Override public List<OverrideLeaseRecord> listOverrides() { return backingStore.listOverrides(); }
            @Override public Instant now() { return backingStore.now(); }
        };

        CoordinatorLeaseManager manager = new CoordinatorLeaseManager(
                interceptingStore,
                "node-1",
                Duration.ofSeconds(15),
                Duration.ofSeconds(5),
                Duration.ofSeconds(1),
                nanoNow::get
        );
        managerRef.set(manager);

        assertThat(manager.storeCallTimeout()).isLessThan(Duration.ofSeconds(5));

        // Initial acquisition at t = 0s -> local deadline is t = 14s (15s - 1s safety margin)
        manager.heartbeat();
        assertThat(manager.isCoordinator()).isTrue();
        assertThat(manager.getLeaseVersion()).isEqualTo(1L);

        // Advance to t = 5s and trigger stalled renewal
        storeNow.set(Instant.parse("2026-09-12T00:00:05Z"));
        nanoNow.addAndGet(Duration.ofSeconds(5).toNanos());
        stallOnNextRenew.set(true);

        manager.heartbeat();

        // At t = 14s (while store lease was still valid until t = 15s), isCoordinator() already turned false!
        assertThat(storeLeaseStillValidAtSelfFence.get())
                .as("Lease in the store must still be valid at t = 14s (before 15s store expiry)")
                .isTrue();
        assertThat(isCoordDuringStallBeforeStoreExpiry.get())
                .as("isCoordinator() must turn false locally at leaseDuration - safetyMargin before store expiry")
                .isFalse();

        // And the late renewal response arriving at t = 20s was discarded
        assertThat(manager.isCoordinator()).isFalse();
        assertThat(manager.getLeaseVersion()).isEqualTo(0L);

        manager.close();
    }

    @Test
    @DisplayName("Stable Lease Version (#1195): Consecutive renewals keep version stable and in-flight operations succeed")
    void testConsecutiveRenewalsKeepLeaseVersionStableAndHolderChangeIncrements() {
        AtomicReference<Instant> storeNow = new AtomicReference<>(Instant.parse("2026-09-12T00:00:00Z"));
        AtomicLong nanoNow = new AtomicLong(1_000_000_000L);
        Clock mutableClock = new Clock() {
            @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public Instant instant() { return storeNow.get(); }
        };

        InMemoryControlStore store = new InMemoryControlStore(mutableClock);
        CoordinatorLeaseManager node1 = new CoordinatorLeaseManager(
                store, "node-1", Duration.ofSeconds(15), Duration.ofSeconds(5), Duration.ofSeconds(1), nanoNow::get);
        CoordinatorLeaseManager node2 = new CoordinatorLeaseManager(
                store, "node-2", Duration.ofSeconds(15), Duration.ofSeconds(5), Duration.ofSeconds(1), nanoNow::get);

        // Node 1 acquires lease v1
        node1.heartbeat();
        long capturedVersion = node1.getLeaseVersion();
        assertThat(capturedVersion).isEqualTo(1L);

        // Consecutive renewals at t = 5s and t = 10s keep lease version at 1L
        storeNow.set(storeNow.get().plusSeconds(5));
        nanoNow.addAndGet(Duration.ofSeconds(5).toNanos());
        node1.heartbeat();
        assertThat(node1.getLeaseVersion()).isEqualTo(1L);

        storeNow.set(storeNow.get().plusSeconds(5));
        nanoNow.addAndGet(Duration.ofSeconds(5).toNanos());
        node1.heartbeat();
        assertThat(node1.getLeaseVersion()).isEqualTo(1L);

        // Operation that captured version 1L before the renewals still passes authority checks
        assertThat(store.advanceNamespaceEpoch("ns-stable", capturedVersion)).isEqualTo(1L);

        // Expire Node 1's lease (advance +20s) and let Node 2 acquire
        storeNow.set(storeNow.get().plusSeconds(20));
        nanoNow.addAndGet(Duration.ofSeconds(20).toNanos());
        assertThat(node1.isCoordinator()).isFalse();

        node2.heartbeat();
        assertThat(node2.isCoordinator()).isTrue();
        assertThat(node2.getLeaseVersion()).isEqualTo(2L);

        // Old holder's captured version 1L now fails authority checks
        assertThatThrownBy(() -> store.advanceNamespaceEpoch("ns-stable", capturedVersion))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Control store CAS failure");

        node1.close();
        node2.close();
    }
}
