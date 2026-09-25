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

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcControlStoreTest {

    private JdbcDataSource dataSource;
    private JdbcControlStore store;
    private AtomicReference<Instant> now;
    private Clock mutableClock;

    @BeforeEach
    void setUp() {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:testdb_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");

        now = new AtomicReference<>(Instant.parse("2026-09-12T00:00:00Z"));
        mutableClock = new Clock() {
            @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public Instant instant() { return now.get(); }
        };

        store = new JdbcControlStore(dataSource, mutableClock);
    }

    @AfterEach
    void tearDown() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            conn.createStatement().execute("DROP ALL OBJECTS");
        }
    }

    @Test
    @DisplayName("Coordinator lease election and renewal lifecycle conforms to contract")
    void testCoordinatorLeaseLifecycle() {
        assertThat(store.getCoordinatorLease()).isNull();

        Optional<CoordinatorLease> acquired = store.acquireOrRenewCoordinatorLease("node-1", Duration.ofSeconds(15));
        assertThat(acquired).isPresent();

        CoordinatorLease lease = acquired.get();
        assertThat(lease.holderNodeId()).isEqualTo("node-1");
        assertThat(lease.leaseVersion()).isEqualTo(1L);
        assertThat(store.isCoordinator("node-1", 1L)).isTrue();
        assertThat(store.isCoordinator("node-2", 1L)).isFalse();

        Optional<CoordinatorLease> node2Acquired = store.acquireOrRenewCoordinatorLease("node-2", Duration.ofSeconds(15));
        assertThat(node2Acquired).isEmpty();

        now.set(now.get().plusSeconds(10));
        Optional<CoordinatorLease> renewed = store.acquireOrRenewCoordinatorLease("node-1", Duration.ofSeconds(15));
        assertThat(renewed).isPresent();
        assertThat(renewed.get().leaseVersion()).isEqualTo(2L);
        assertThat(store.isCoordinator("node-1", 2L)).isTrue();

        now.set(now.get().plusSeconds(20));
        assertThat(store.getValidCoordinatorLease()).isEmpty();

        Optional<CoordinatorLease> node2AcquiredAfterExpiry = store.acquireOrRenewCoordinatorLease("node-2", Duration.ofSeconds(15));
        assertThat(node2AcquiredAfterExpiry).isPresent();
        assertThat(node2AcquiredAfterExpiry.get().holderNodeId()).isEqualTo("node-2");
        assertThat(node2AcquiredAfterExpiry.get().leaseVersion()).isEqualTo(3L);

        store.releaseCoordinatorLease("node-2");
        assertThat(store.getValidCoordinatorLease()).isEmpty();
    }

    @Test
    @DisplayName("Namespace epoch advances monotonically per namespace")
    void testNamespaceEpochMonotonicity() {
        store.acquireOrRenewCoordinatorLease("coord-1", Duration.ofSeconds(60));
        assertThat(store.getNamespaceEpoch("ns-1")).isEqualTo(0L);

        long e1 = store.advanceNamespaceEpoch("ns-1", 1L);
        assertThat(e1).isEqualTo(1L);
        assertThat(store.getNamespaceEpoch("ns-1")).isEqualTo(1L);

        long e2 = store.advanceNamespaceEpoch("ns-1", 1L);
        assertThat(e2).isEqualTo(2L);
    }

    @Test
    @DisplayName("Concurrent epoch advancement")
    void testConcurrentEpochAdvancement() throws InterruptedException {
        store.acquireOrRenewCoordinatorLease("coord-1", Duration.ofSeconds(60));
        int threads = 10;
        int incrementsPerThread = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < incrementsPerThread; j++) {
                        store.advanceNamespaceEpoch("ns-concurrent", 1L);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executor.shutdown();

        assertThat(store.getNamespaceEpoch("ns-concurrent")).isEqualTo(threads * incrementsPerThread);
    }

    @Test
    @DisplayName("Single-writer CAS enforces expected lease version on mutations")
    void testSingleWriterCasEnforcement() {
        store.acquireOrRenewCoordinatorLease("coord-1", Duration.ofSeconds(60));

        assertThat(store.advanceNamespaceEpoch("ns-cas", 1L)).isEqualTo(1L);

        OverrideLeaseRecord record = new OverrideLeaseRecord(
                "ns-cas", "target-node", 1L, "fence-1", now.get().plusSeconds(60)
        );
        assertThat(store.setOverride(record, 1L)).isTrue();
        assertThat(store.getOverride("ns-cas")).isPresent();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> store.advanceNamespaceEpoch("ns-cas", 999L))
                .isInstanceOf(IllegalStateException.class);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> store.setOverride(record, 999L))
                .isInstanceOf(IllegalStateException.class);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> store.removeOverride("ns-cas", 999L))
                .isInstanceOf(IllegalStateException.class);

        assertThat(store.removeOverride("ns-cas", 1L)).isTrue();
        assertThat(store.getOverride("ns-cas")).isEmpty();
    }
}
