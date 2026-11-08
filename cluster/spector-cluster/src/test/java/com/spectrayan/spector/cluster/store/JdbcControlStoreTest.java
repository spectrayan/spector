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

import com.spectrayan.spector.cluster.coordinator.CoordinatorLeaseManager;
import com.spectrayan.spector.cluster.membership.CellMembership;
import org.h2.jdbcx.JdbcDataSource;
import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    void testCoordinatorLeaseLifecycle() throws InterruptedException {
        assertThat(store.getCoordinatorLease()).isNull();

        Optional<CoordinatorLease> acquired = store.acquireOrRenewCoordinatorLease("node-1", Duration.ofMillis(200));
        assertThat(acquired).isPresent();

        CoordinatorLease lease = acquired.get();
        assertThat(lease.holderNodeId()).isEqualTo("node-1");
        assertThat(lease.leaseVersion()).isEqualTo(1L);
        assertThat(store.isCoordinator("node-1", 1L)).isTrue();
        assertThat(store.isCoordinator("node-2", 1L)).isFalse();

        Optional<CoordinatorLease> node2Acquired = store.acquireOrRenewCoordinatorLease("node-2", Duration.ofMillis(200));
        assertThat(node2Acquired).isEmpty();

        Optional<CoordinatorLease> renewed = store.acquireOrRenewCoordinatorLease("node-1", Duration.ofMillis(150));
        assertThat(renewed).isPresent();
        assertThat(renewed.get().leaseVersion()).isEqualTo(1L);
        assertThat(store.isCoordinator("node-1", 1L)).isTrue();
        assertThat(store.advanceNamespaceEpoch("ns-inflight", 1L)).isEqualTo(1L);

        Thread.sleep(200);
        assertThat(store.getValidCoordinatorLease()).isEmpty();

        Optional<CoordinatorLease> node2AcquiredAfterExpiry = store.acquireOrRenewCoordinatorLease("node-2", Duration.ofSeconds(15));
        assertThat(node2AcquiredAfterExpiry).isPresent();
        assertThat(node2AcquiredAfterExpiry.get().holderNodeId()).isEqualTo("node-2");
        assertThat(node2AcquiredAfterExpiry.get().leaseVersion()).isEqualTo(2L);
        assertThat(store.isCoordinator("node-1", 1L)).isFalse();

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
                "ns-cas", "target-node", 1L, "fence-1", store.now().plusSeconds(60)
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

    @Test
    @DisplayName("Clock skew between two nodes still allows at most one valid lease using database clock (#1196)")
    void testClockSkewBetweenNodesAllowsAtMostOneValidLease() {
        Clock node1ClockSkewedBack = Clock.offset(Clock.systemUTC(), Duration.ofSeconds(-10));
        Clock node2ClockSkewedForward = Clock.offset(Clock.systemUTC(), Duration.ofSeconds(10));

        JdbcControlStore node1Store = new JdbcControlStore(dataSource, node1ClockSkewedBack);
        JdbcControlStore node2Store = new JdbcControlStore(dataSource, node2ClockSkewedForward);

        Optional<CoordinatorLease> node1Lease = node1Store.acquireOrRenewCoordinatorLease("node-1", Duration.ofSeconds(5));
        assertThat(node1Lease).isPresent();
        long v1 = node1Lease.get().leaseVersion();

        // Despite node-2's application clock being 20s ahead of node-1's clock, database clock governs expiry
        Optional<CoordinatorLease> node2Attempt = node2Store.acquireOrRenewCoordinatorLease("node-2", Duration.ofSeconds(5));
        assertThat(node2Attempt)
                .as("Skewed node-2 clock must not steal an active lease before database clock expiry")
                .isEmpty();

        assertThat(node1Store.isCoordinator("node-1", v1)).isTrue();
        assertThat(node2Store.isCoordinator("node-1", v1)).isTrue();
        assertThat(node2Store.getValidCoordinatorLease())
                .isPresent()
                .get()
                .extracting(CoordinatorLease::holderNodeId)
                .isEqualTo("node-1");
    }

    @Test
    @DisplayName("Timezone-aware timestamps preserve exact expiry instant across JVM time zones (#1196)")
    void testTimezoneAwareTimestampsAcrossTimeZones() {
        TimeZone originalTz = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            JdbcControlStore utcStore = new JdbcControlStore(dataSource, Clock.systemUTC());
            CoordinatorLease acquired = utcStore.acquireOrRenewCoordinatorLease("node-tz", Duration.ofSeconds(30)).orElseThrow();
            Instant utcExpiresAt = acquired.expiresAt();

            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
            JdbcControlStore nyStore = new JdbcControlStore(dataSource, Clock.systemUTC());
            CoordinatorLease readInNy = nyStore.getCoordinatorLease();

            assertThat(readInNy).isNotNull();
            assertThat(Duration.between(utcExpiresAt, readInNy.expiresAt()).abs())
                    .as("Expiry timestamp must not shift when read under a different JVM TimeZone")
                    .isLessThan(Duration.ofSeconds(1));
        } finally {
            TimeZone.setDefault(originalTz);
        }
    }

    @Test
    @DisplayName("Statements configure query timeout and updateMembership uses portable SQL (#1196)")
    void testPortableSqlAndQueryTimeoutsConfigured() {
        List<String> executedSql = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger zeroTimeoutStatements = new AtomicInteger(0);
        DataSource inspectingDs = wrapDataSourceInspectingSqlAndTimeout(dataSource, executedSql, zeroTimeoutStatements);

        JdbcControlStore inspectedStore = new JdbcControlStore(inspectingDs, Clock.systemUTC());
        CellMembership m1 = new CellMembership("cell-1", 1, List.of("node-1", "node-2"));
        CellMembership m2 = new CellMembership("cell-1", 2, List.of("node-1", "node-2", "node-3"));

        inspectedStore.updateMembership(m1);
        inspectedStore.updateMembership(m2);
        assertThat(inspectedStore.getMembership()).isEqualTo(m2);

        assertThat(executedSql)
                .as("updateMembership must not use H2-only MERGE INTO ... KEY(...) syntax")
                .noneMatch(sql -> sql.toUpperCase().contains("MERGE INTO"));
        assertThat(zeroTimeoutStatements.get())
                .as("Every PreparedStatement must configure a positive queryTimeout")
                .isZero();
    }

    @Test
    @DisplayName("Blocked row lock fails within configured timeout and coordinator heartbeat recovers (#1196)")
    void testBlockedRowLockFailsWithinTimeoutAndHeartbeatRecovers() throws Exception {
        JdbcDataSource shortLockDs = new JdbcDataSource();
        shortLockDs.setURL("jdbc:h2:mem:lockdb_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=300");
        shortLockDs.setUser("sa");
        shortLockDs.setPassword("");

        JdbcControlStore lockStore = new JdbcControlStore(shortLockDs, Clock.systemUTC());
        CoordinatorLeaseManager leaseManager = new CoordinatorLeaseManager(
                lockStore, "node-1", Duration.ofSeconds(15), Duration.ofSeconds(5));

        leaseManager.heartbeat();
        assertThat(leaseManager.isCoordinator()).isTrue();

        try (Connection blocker = shortLockDs.getConnection()) {
            blocker.setAutoCommit(false);
            try (PreparedStatement lockStmt = blocker.prepareStatement(
                    "SELECT epoch, owner_id, updated_at FROM spector_control_store WHERE namespace_id = '__coordinator__' FOR UPDATE")) {
                lockStmt.executeQuery();

                long startNanos = System.nanoTime();
                assertThatThrownBy(() -> lockStore.acquireOrRenewCoordinatorLease("node-1", Duration.ofSeconds(15)))
                        .isInstanceOf(RuntimeException.class);
                Duration elapsed = Duration.ofNanos(System.nanoTime() - startNanos);
                assertThat(elapsed).isLessThan(Duration.ofSeconds(3));
            } finally {
                blocker.rollback();
            }
        }

        // Once the blocking row lock is released, heartbeat recovers cleanly
        leaseManager.heartbeat();
        assertThat(leaseManager.isCoordinator()).isTrue();
    }

    @Test
    @DisplayName("Takeover grace period enforces strict less-than cutoff before another node takes over (#1196)")
    void testTakeoverGracePeriodEnforcesStrictLessThanCutoff() throws InterruptedException {
        JdbcControlStore graceStore = new JdbcControlStore(
                dataSource,
                Clock.systemUTC(),
                Duration.ofMillis(350),
                Duration.ofSeconds(5));

        Optional<CoordinatorLease> node1 = graceStore.acquireOrRenewCoordinatorLease("node-1", Duration.ofMillis(100));
        assertThat(node1).isPresent();

        // Wait until lease has expired (150ms > 100ms), but still within takeoverGracePeriod (150ms < 100ms + 350ms)
        Thread.sleep(150);
        assertThat(graceStore.getValidCoordinatorLease()).isEmpty();
        assertThat(graceStore.acquireOrRenewCoordinatorLease("node-2", Duration.ofSeconds(5)))
                .as("Takeover must be blocked while within takeoverGracePeriod")
                .isEmpty();

        // Wait past takeoverGracePeriod (total > 450ms)
        Thread.sleep(360);
        Optional<CoordinatorLease> node2 = graceStore.acquireOrRenewCoordinatorLease("node-2", Duration.ofSeconds(5));
        assertThat(node2)
                .as("Takeover must succeed once expires_at < CURRENT_TIMESTAMP - takeoverGracePeriod")
                .isPresent();
        assertThat(node2.get().holderNodeId()).isEqualTo("node-2");
    }

    @Test
    @DisplayName("JdbcControlStore passes full contract against non-H2 database (HSQLDB) and H2 PostgreSQL mode (#1196)")
    void testStorePassesAgainstNonH2DatabaseAndPostgresMode() {
        // 1. Genuine non-H2 database: HSQLDB (MVCC + PostgreSQL syntax mode)
        JDBCDataSource hsqldbDs = new JDBCDataSource();
        hsqldbDs.setUrl("jdbc:hsqldb:mem:hsqldb_" + UUID.randomUUID().toString().replace('-', '_') + ";hsqldb.tx=mvcc;sql.syntax_pgs=true");
        hsqldbDs.setUser("SA");
        hsqldbDs.setPassword("");
        verifyStoreContractAgainstBackend(hsqldbDs);

        // 2. H2 in PostgreSQL compatibility mode
        JdbcDataSource pgModeDs = new JdbcDataSource();
        pgModeDs.setURL("jdbc:h2:mem:pgmode_" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1");
        pgModeDs.setUser("sa");
        pgModeDs.setPassword("");
        verifyStoreContractAgainstBackend(pgModeDs);
    }

    private static void verifyStoreContractAgainstBackend(DataSource backendDs) {
        JdbcControlStore backendStore = new JdbcControlStore(backendDs, Clock.systemUTC());

        CellMembership m1 = new CellMembership("cell-a", 1, List.of("n1", "n2"));
        CellMembership m2 = new CellMembership("cell-a", 2, List.of("n1", "n2", "n3"));
        backendStore.updateMembership(m1);
        assertThat(backendStore.getMembership()).isEqualTo(m1);
        backendStore.updateMembership(m2);
        assertThat(backendStore.getMembership()).isEqualTo(m2);

        Optional<CoordinatorLease> lease = backendStore.acquireOrRenewCoordinatorLease("n1", Duration.ofSeconds(30));
        assertThat(lease).isPresent();
        long v = lease.get().leaseVersion();
        assertThat(backendStore.isCoordinator("n1", v)).isTrue();
        assertThat(backendStore.getValidCoordinatorLease()).isPresent();

        Optional<CoordinatorLease> renewed = backendStore.acquireOrRenewCoordinatorLease("n1", Duration.ofSeconds(30));
        assertThat(renewed).isPresent();
        assertThat(renewed.get().leaseVersion()).isEqualTo(v);
        assertThat(backendStore.isCoordinator("n1", v)).isTrue();

        assertThat(backendStore.advanceNamespaceEpoch("ns-pg", v)).isEqualTo(1L);
        assertThat(backendStore.advanceNamespaceEpoch("ns-pg", v)).isEqualTo(2L);
        assertThat(backendStore.getNamespaceEpoch("ns-pg")).isEqualTo(2L);

        OverrideLeaseRecord override = new OverrideLeaseRecord(
                "ns-pg", "n2", 2L, "fence-2", backendStore.now().plusSeconds(60));
        assertThat(backendStore.setOverride(override, v)).isTrue();
        assertThat(backendStore.getOverride("ns-pg")).isPresent();
        assertThat(backendStore.listOverrides()).hasSize(1);
        assertThat(backendStore.removeOverride("ns-pg", v)).isTrue();
        assertThat(backendStore.getOverride("ns-pg")).isEmpty();

        backendStore.releaseCoordinatorLease("n1");
        assertThat(backendStore.getValidCoordinatorLease()).isEmpty();
    }

    private static DataSource wrapDataSourceInspectingSqlAndTimeout(
            DataSource delegate,
            List<String> executedSql,
            AtomicInteger zeroTimeoutStatements) {
        return new DataSource() {
            @Override
            public Connection getConnection() throws SQLException {
                return wrapConnection(delegate.getConnection());
            }

            @Override
            public Connection getConnection(String username, String password) throws SQLException {
                return wrapConnection(delegate.getConnection(username, password));
            }

            private Connection wrapConnection(Connection conn) {
                return (Connection) Proxy.newProxyInstance(
                        Connection.class.getClassLoader(),
                        new Class<?>[]{Connection.class},
                        (proxy, method, args) -> {
                            if ("prepareStatement".equals(method.getName()) && args != null && args.length > 0 && args[0] instanceof String sql) {
                                executedSql.add(sql);
                                PreparedStatement ps = (PreparedStatement) method.invoke(conn, args);
                                return wrapPreparedStatement(ps);
                            }
                            return method.invoke(conn, args);
                        });
            }

            private PreparedStatement wrapPreparedStatement(PreparedStatement ps) {
                return (PreparedStatement) Proxy.newProxyInstance(
                        PreparedStatement.class.getClassLoader(),
                        new Class<?>[]{PreparedStatement.class},
                        new InvocationHandler() {
                            @Override
                            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                                if (method.getName().startsWith("execute")) {
                                    if (ps.getQueryTimeout() <= 0) {
                                        zeroTimeoutStatements.incrementAndGet();
                                    }
                                }
                                return method.invoke(ps, args);
                            }
                        });
            }

            @Override public PrintWriter getLogWriter() throws SQLException { return delegate.getLogWriter(); }
            @Override public void setLogWriter(PrintWriter out) throws SQLException { delegate.setLogWriter(out); }
            @Override public void setLoginTimeout(int seconds) throws SQLException { delegate.setLoginTimeout(seconds); }
            @Override public int getLoginTimeout() throws SQLException { return delegate.getLoginTimeout(); }
            @Override public Logger getParentLogger() throws SQLFeatureNotSupportedException { return delegate.getParentLogger(); }
            @Override public <T> T unwrap(Class<T> iface) throws SQLException { return delegate.unwrap(iface); }
            @Override public boolean isWrapperFor(Class<?> iface) throws SQLException { return delegate.isWrapperFor(iface); }
        };
    }
}
