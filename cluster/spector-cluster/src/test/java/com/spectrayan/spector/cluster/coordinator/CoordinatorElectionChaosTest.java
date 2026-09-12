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

import com.spectrayan.spector.cluster.store.ControlStore;
import com.spectrayan.spector.cluster.store.InMemoryControlStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

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

        // Even if dead Node 1 somehow wakes up, store-enforced check immediately stops it (Req R1.5, R1.6)
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
}
