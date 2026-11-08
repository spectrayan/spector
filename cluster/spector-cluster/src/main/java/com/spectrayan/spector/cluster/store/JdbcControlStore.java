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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.TimeZone;

/**
 * JDBC-backed {@link ControlStore} implementation for multi-node cluster coordination.
 *
 * <h3>Multi-Node Database Requirements</h3>
 * <ul>
 *   <li><b>Database clock authority:</b> Lease acquisition, renewal, authority verification, and
 *       expiration checks use the database clock ({@code CURRENT_TIMESTAMP}) in SQL {@code WHERE}
 *       clauses with compare-and-set on {@code (owner_id, epoch)} so application clock skew across
 *       nodes cannot cause split-brain lease ownership.</li>
 *   <li><b>Timezone-aware timestamps:</b> The {@code updated_at} column is declared as
 *       {@code TIMESTAMP WITH TIME ZONE} and bound/read as UTC {@link OffsetDateTime} so values
 *       never shift across JVM or session time zones.</li>
 *   <li><b>Statement and lock timeouts:</b> Every JDBC {@link PreparedStatement} configures
 *       {@link PreparedStatement#setQueryTimeout(int)} (default {@link #DEFAULT_QUERY_TIMEOUT},
 *       5 seconds). Multi-node deployments should also configure database-level lock and statement
 *       timeouts shorter than the coordinator heartbeat interval (for example, PostgreSQL
 *       {@code lock_timeout = '2s'} and {@code statement_timeout = '5s'}, MySQL/MariaDB
 *       {@code innodb_lock_wait_timeout = 2}, or H2 {@code LOCK_TIMEOUT=2000}) so a wedged
 *       transaction on one node cannot block lease renewal indefinitely.</li>
 *   <li><b>Takeover grace period:</b> Expired-lease takeover uses strict {@code <} comparison
 *       ({@code updated_at < CURRENT_TIMESTAMP - takeoverGracePeriod}) to provide an additional
 *       safety margin before a new coordinator takes over an expiring lease.</li>
 * </ul>
 */
public class JdbcControlStore implements ControlStore {

    private static final Logger log = LoggerFactory.getLogger(JdbcControlStore.class);

    public static final Duration DEFAULT_TAKEOVER_GRACE_PERIOD = Duration.ZERO;
    public static final Duration DEFAULT_QUERY_TIMEOUT = Duration.ofSeconds(5);

    private static final String NS_MEMBERSHIP = "__membership__";
    private static final String NS_COORDINATOR = "__coordinator__";
    private static final String PREFIX_OVERRIDE = "__override__:";

    private final DataSource dataSource;
    private final Clock clock;
    private final Duration takeoverGracePeriod;
    private final Duration queryTimeout;
    private final int queryTimeoutSeconds;

    public JdbcControlStore(DataSource dataSource) {
        this(dataSource, Clock.systemUTC(), DEFAULT_TAKEOVER_GRACE_PERIOD, DEFAULT_QUERY_TIMEOUT);
    }

    public JdbcControlStore(DataSource dataSource, Clock clock) {
        this(dataSource, clock, DEFAULT_TAKEOVER_GRACE_PERIOD, DEFAULT_QUERY_TIMEOUT);
    }

    public JdbcControlStore(DataSource dataSource, Duration takeoverGracePeriod, Duration queryTimeout) {
        this(dataSource, Clock.systemUTC(), takeoverGracePeriod, queryTimeout);
    }

    public JdbcControlStore(
            DataSource dataSource,
            Clock clock,
            Duration takeoverGracePeriod,
            Duration queryTimeout) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
        this.clock = clock != null ? clock : Clock.systemUTC();
        this.takeoverGracePeriod = takeoverGracePeriod != null && !takeoverGracePeriod.isNegative()
                ? takeoverGracePeriod
                : DEFAULT_TAKEOVER_GRACE_PERIOD;
        this.queryTimeout = queryTimeout != null && !queryTimeout.isNegative() && !queryTimeout.isZero()
                ? queryTimeout
                : DEFAULT_QUERY_TIMEOUT;
        this.queryTimeoutSeconds = (int) Math.max(1L, (this.queryTimeout.toMillis() + 999L) / 1000L);
        initSchema();
    }

    public Duration takeoverGracePeriod() {
        return takeoverGracePeriod;
    }

    public Duration queryTimeout() {
        return queryTimeout;
    }

    public int queryTimeoutSeconds() {
        return queryTimeoutSeconds;
    }

    private PreparedStatement prepareStatement(Connection conn, String sql) throws SQLException {
        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setQueryTimeout(queryTimeoutSeconds);
        return ps;
    }

    private static void setTimestampWithTimeZone(PreparedStatement ps, int index, Instant instant) throws SQLException {
        OffsetDateTime odt = OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
        try {
            ps.setObject(index, odt);
        } catch (SQLException e) {
            Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            ps.setTimestamp(index, Timestamp.from(instant), utc);
        }
    }

    private static Instant getTimestampInstant(ResultSet rs, String column) throws SQLException {
        try {
            OffsetDateTime odt = rs.getObject(column, OffsetDateTime.class);
            if (odt != null) {
                return odt.toInstant();
            }
        } catch (SQLException ignored) {
            // Fallback for drivers that do not support OffsetDateTime conversion
        }
        Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        Timestamp ts = rs.getTimestamp(column, utc);
        return ts != null ? ts.toInstant() : null;
    }

    private Instant queryDatabaseNow(Connection conn) throws SQLException {
        try (PreparedStatement ps = prepareStatement(
                conn,
                "SELECT COALESCE(MAX(updated_at), CURRENT_TIMESTAMP) AS ignored_max, CURRENT_TIMESTAMP AS db_now FROM spector_control_store");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                Instant dbNow = getTimestampInstant(rs, "db_now");
                if (dbNow != null) {
                    return dbNow;
                }
            }
        }
        return clock.instant();
    }

    private void initSchema() {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = prepareStatement(
                     conn,
                     "CREATE TABLE IF NOT EXISTS spector_control_store (" +
                             "namespace_id VARCHAR(255) NOT NULL PRIMARY KEY, " +
                             "epoch BIGINT NOT NULL DEFAULT 0, " +
                             "owner_id VARCHAR(255), " +
                             "updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP" +
                             ")")) {
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize control store schema", e);
        }
    }

    @Override
    public CellMembership getMembership() {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = prepareStatement(conn, "SELECT epoch, owner_id FROM spector_control_store WHERE namespace_id = ?")) {
            ps.setString(1, NS_MEMBERSHIP);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    long ringVersion = rs.getLong("epoch");
                    String ownerId = rs.getString("owner_id");
                    if (ownerId != null && !ownerId.isBlank()) {
                        String[] parts = ownerId.split("\\|", 2);
                        if (parts.length == 2) {
                            String cellId = parts[0];
                            List<String> members = parts[1].isEmpty() ? List.of() : Arrays.asList(parts[1].split(","));
                            return new CellMembership(cellId, (int) ringVersion, members);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get membership", e);
        }
        return null;
    }

    @Override
    public void updateMembership(CellMembership membership) {
        Objects.requireNonNull(membership, "membership must not be null");
        String membersStr = String.join(",", membership.members());
        String ownerId = membership.cellId() + "|" + membersStr;

        while (true) {
            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    int updated;
                    try (PreparedStatement update = prepareStatement(
                            conn,
                            "UPDATE spector_control_store SET epoch = ?, owner_id = ?, updated_at = CURRENT_TIMESTAMP WHERE namespace_id = ?")) {
                        update.setLong(1, membership.ringVersion());
                        update.setString(2, ownerId);
                        update.setString(3, NS_MEMBERSHIP);
                        updated = update.executeUpdate();
                    }
                    if (updated == 0) {
                        try (PreparedStatement insert = prepareStatement(
                                conn,
                                "INSERT INTO spector_control_store (namespace_id, epoch, owner_id, updated_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP)")) {
                            insert.setString(1, NS_MEMBERSHIP);
                            insert.setLong(2, membership.ringVersion());
                            insert.setString(3, ownerId);
                            insert.executeUpdate();
                        } catch (SQLException insertEx) {
                            conn.rollback();
                            continue;
                        }
                    }
                    conn.commit();
                    return;
                } catch (SQLException ex) {
                    conn.rollback();
                    throw ex;
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to update membership", e);
            }
        }
    }

    @Override
    public CoordinatorLease getCoordinatorLease() {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = prepareStatement(
                     conn,
                     "SELECT epoch, owner_id, updated_at FROM spector_control_store WHERE namespace_id = ?")) {
            ps.setString(1, NS_COORDINATOR);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    long leaseVersion = rs.getLong("epoch");
                    String ownerId = rs.getString("owner_id");
                    Instant expiresAt = getTimestampInstant(rs, "updated_at");
                    if (ownerId != null && expiresAt != null) {
                        String[] parts = ownerId.split("\\|", 2);
                        String holder = parts[0];
                        Instant acquired = parts.length > 1 ? Instant.ofEpochMilli(Long.parseLong(parts[1])) : Instant.EPOCH;
                        return new CoordinatorLease(holder, acquired, expiresAt, leaseVersion);
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get coordinator lease", e);
        }
        return null;
    }

    @Override
    public Optional<CoordinatorLease> getValidCoordinatorLease() {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = prepareStatement(
                     conn,
                     "SELECT epoch, owner_id, updated_at FROM spector_control_store " +
                             "WHERE namespace_id = ? AND owner_id IS NOT NULL AND NOT (updated_at < CURRENT_TIMESTAMP)")) {
            ps.setString(1, NS_COORDINATOR);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    long leaseVersion = rs.getLong("epoch");
                    String ownerId = rs.getString("owner_id");
                    Instant expiresAt = getTimestampInstant(rs, "updated_at");
                    if (ownerId != null && expiresAt != null) {
                        String[] parts = ownerId.split("\\|", 2);
                        String holder = parts[0];
                        Instant acquired = parts.length > 1 ? Instant.ofEpochMilli(Long.parseLong(parts[1])) : Instant.EPOCH;
                        return Optional.of(new CoordinatorLease(holder, acquired, expiresAt, leaseVersion));
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get valid coordinator lease", e);
        }
        return Optional.empty();
    }

    @Override
    public boolean isCoordinator(String nodeId, long expectedLeaseVersion) {
        if (nodeId == null || expectedLeaseVersion <= 0) {
            return false;
        }
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = prepareStatement(
                     conn,
                     "SELECT owner_id FROM spector_control_store " +
                             "WHERE namespace_id = ? AND epoch = ? AND owner_id IS NOT NULL AND NOT (updated_at < CURRENT_TIMESTAMP)")) {
            ps.setString(1, NS_COORDINATOR);
            ps.setLong(2, expectedLeaseVersion);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String ownerIdRaw = rs.getString("owner_id");
                    String holder = ownerIdRaw != null ? ownerIdRaw.split("\\|", 2)[0] : null;
                    return nodeId.equals(holder);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to verify coordinator lease", e);
        }
        return false;
    }

    @Override
    public Optional<CoordinatorLease> acquireOrRenewCoordinatorLease(String candidateNodeId, Duration duration) {
        Objects.requireNonNull(candidateNodeId, "candidateNodeId must not be null");
        Objects.requireNonNull(duration, "duration must not be null");
        while (true) {
            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    CoordinatorLease result = null;
                    try (PreparedStatement ps = prepareStatement(
                            conn,
                            "SELECT epoch, owner_id, updated_at, CURRENT_TIMESTAMP AS db_now " +
                                    "FROM spector_control_store WHERE namespace_id = ? FOR UPDATE")) {
                        ps.setString(1, NS_COORDINATOR);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                long epoch = rs.getLong("epoch");
                                String ownerIdRaw = rs.getString("owner_id");
                                Instant expiresAt = getTimestampInstant(rs, "updated_at");
                                Instant dbNow = getTimestampInstant(rs, "db_now");
                                if (dbNow == null) {
                                    dbNow = queryDatabaseNow(conn);
                                }

                                String holder = ownerIdRaw != null ? ownerIdRaw.split("\\|", 2)[0] : null;
                                Instant takeoverCutoff = dbNow.minus(takeoverGracePeriod);
                                boolean sameHolderActive = candidateNodeId.equals(holder)
                                        && expiresAt != null
                                        && !expiresAt.isBefore(dbNow);
                                boolean expiredForTakeover = holder == null
                                        || expiresAt == null
                                        || expiresAt.isBefore(takeoverCutoff);

                                if (sameHolderActive) {
                                    long nextEpoch = epoch + 1;
                                    Instant newExpiresAt = dbNow.plus(duration);
                                    String newOwnerId = candidateNodeId + "|" + dbNow.toEpochMilli();
                                    try (PreparedStatement update = prepareStatement(
                                            conn,
                                            "UPDATE spector_control_store SET epoch = ?, owner_id = ?, updated_at = ? " +
                                                    "WHERE namespace_id = ? AND epoch = ? AND owner_id = ? AND NOT (updated_at < CURRENT_TIMESTAMP)")) {
                                        update.setLong(1, nextEpoch);
                                        update.setString(2, newOwnerId);
                                        setTimestampWithTimeZone(update, 3, newExpiresAt);
                                        update.setString(4, NS_COORDINATOR);
                                        update.setLong(5, epoch);
                                        update.setString(6, ownerIdRaw);
                                        if (update.executeUpdate() == 1) {
                                            result = new CoordinatorLease(candidateNodeId, dbNow, newExpiresAt, nextEpoch);
                                        }
                                    }
                                } else if (expiredForTakeover) {
                                    long nextEpoch = epoch + 1;
                                    Instant newExpiresAt = dbNow.plus(duration);
                                    String newOwnerId = candidateNodeId + "|" + dbNow.toEpochMilli();
                                    int updated;
                                    if (ownerIdRaw == null) {
                                        try (PreparedStatement update = prepareStatement(
                                                conn,
                                                "UPDATE spector_control_store SET epoch = ?, owner_id = ?, updated_at = ? " +
                                                        "WHERE namespace_id = ? AND epoch = ? AND owner_id IS NULL")) {
                                            update.setLong(1, nextEpoch);
                                            update.setString(2, newOwnerId);
                                            setTimestampWithTimeZone(update, 3, newExpiresAt);
                                            update.setString(4, NS_COORDINATOR);
                                            update.setLong(5, epoch);
                                            updated = update.executeUpdate();
                                        }
                                    } else {
                                        try (PreparedStatement update = prepareStatement(
                                                conn,
                                                "UPDATE spector_control_store SET epoch = ?, owner_id = ?, updated_at = ? " +
                                                        "WHERE namespace_id = ? AND epoch = ? AND owner_id = ? AND updated_at < ?")) {
                                            update.setLong(1, nextEpoch);
                                            update.setString(2, newOwnerId);
                                            setTimestampWithTimeZone(update, 3, newExpiresAt);
                                            update.setString(4, NS_COORDINATOR);
                                            update.setLong(5, epoch);
                                            update.setString(6, ownerIdRaw);
                                            setTimestampWithTimeZone(update, 7, takeoverCutoff);
                                            updated = update.executeUpdate();
                                        }
                                    }
                                    if (updated == 1) {
                                        result = new CoordinatorLease(candidateNodeId, dbNow, newExpiresAt, nextEpoch);
                                    }
                                }
                            } else {
                                Instant dbNow = queryDatabaseNow(conn);
                                long nextEpoch = 1L;
                                Instant newExpiresAt = dbNow.plus(duration);
                                String newOwnerId = candidateNodeId + "|" + dbNow.toEpochMilli();

                                try (PreparedStatement insert = prepareStatement(
                                        conn,
                                        "INSERT INTO spector_control_store (namespace_id, epoch, owner_id, updated_at) VALUES (?, ?, ?, ?)")) {
                                    insert.setString(1, NS_COORDINATOR);
                                    insert.setLong(2, nextEpoch);
                                    insert.setString(3, newOwnerId);
                                    setTimestampWithTimeZone(insert, 4, newExpiresAt);
                                    insert.executeUpdate();
                                } catch (SQLException e) {
                                    conn.rollback();
                                    continue;
                                }
                                result = new CoordinatorLease(candidateNodeId, dbNow, newExpiresAt, nextEpoch);
                            }
                        }
                    }
                    conn.commit();
                    return Optional.ofNullable(result);
                } catch (SQLException ex) {
                    conn.rollback();
                    throw ex;
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to acquire/renew coordinator lease", e);
            }
        }
    }

    @Override
    public void releaseCoordinatorLease(String nodeId) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = prepareStatement(
                        conn,
                        "SELECT epoch, owner_id, CURRENT_TIMESTAMP AS db_now FROM spector_control_store WHERE namespace_id = ? FOR UPDATE")) {
                    ps.setString(1, NS_COORDINATOR);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            String ownerIdRaw = rs.getString("owner_id");
                            String holder = ownerIdRaw != null ? ownerIdRaw.split("\\|", 2)[0] : null;
                            if (nodeId.equals(holder)) {
                                long epoch = rs.getLong("epoch");
                                Instant dbNow = getTimestampInstant(rs, "db_now");
                                if (dbNow == null) {
                                    dbNow = queryDatabaseNow(conn);
                                }
                                Instant expiredInstant = dbNow.minus(takeoverGracePeriod).minusMillis(1);
                                try (PreparedStatement update = prepareStatement(
                                        conn,
                                        "UPDATE spector_control_store SET epoch = ?, owner_id = NULL, updated_at = ? " +
                                                "WHERE namespace_id = ? AND epoch = ? AND owner_id = ?")) {
                                    update.setLong(1, epoch + 1);
                                    setTimestampWithTimeZone(update, 2, expiredInstant);
                                    update.setString(3, NS_COORDINATOR);
                                    update.setLong(4, epoch);
                                    update.setString(5, ownerIdRaw);
                                    update.executeUpdate();
                                }
                            }
                        }
                    }
                }
                conn.commit();
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to release coordinator lease", e);
        }
    }

    @Override
    public long getNamespaceEpoch(String namespaceId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = prepareStatement(conn, "SELECT epoch FROM spector_control_store WHERE namespace_id = ?")) {
            ps.setString(1, namespaceId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("epoch");
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get namespace epoch", e);
        }
        return 0L;
    }

    private void assertCoordinatorAuthority(Connection conn, long expectedLeaseVersion) throws SQLException {
        try (PreparedStatement ps = prepareStatement(
                conn,
                "SELECT epoch, CASE WHEN owner_id IS NOT NULL AND NOT (updated_at < CURRENT_TIMESTAMP) THEN 1 ELSE 0 END AS is_active " +
                        "FROM spector_control_store WHERE namespace_id = ?")) {
            ps.setString(1, NS_COORDINATOR);
            try (ResultSet rs = ps.executeQuery()) {
                boolean hasActiveLease = false;
                long activeVersion = 0L;
                if (rs.next()) {
                    activeVersion = rs.getLong("epoch");
                    hasActiveLease = rs.getInt("is_active") == 1;
                }

                if (expectedLeaseVersion > 0) {
                    if (!hasActiveLease || activeVersion != expectedLeaseVersion) {
                        throw new IllegalStateException("Control store CAS failure: expected coordinator lease version "
                                + expectedLeaseVersion + " but active lease is v" + (hasActiveLease ? activeVersion : 0));
                    }
                } else if (hasActiveLease) {
                    throw new IllegalStateException("Control store requires expectedLeaseVersion when active coordinator is present (v"
                            + activeVersion + ")");
                }
            }
        }
    }

    @Override
    public long advanceNamespaceEpoch(String namespaceId, long expectedLeaseVersion) {
        while (true) {
            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    assertCoordinatorAuthority(conn, expectedLeaseVersion);

                    long nextEpoch = 1L;
                    try (PreparedStatement ps = prepareStatement(
                            conn,
                            "SELECT epoch FROM spector_control_store WHERE namespace_id = ? FOR UPDATE")) {
                        ps.setString(1, namespaceId);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                long currentEpoch = rs.getLong("epoch");
                                nextEpoch = currentEpoch + 1;
                                try (PreparedStatement update = prepareStatement(
                                        conn,
                                        "UPDATE spector_control_store SET epoch = ?, updated_at = CURRENT_TIMESTAMP WHERE namespace_id = ? AND epoch = ?")) {
                                    update.setLong(1, nextEpoch);
                                    update.setString(2, namespaceId);
                                    update.setLong(3, currentEpoch);
                                    if (update.executeUpdate() == 0) {
                                        conn.rollback();
                                        continue;
                                    }
                                }
                            } else {
                                try (PreparedStatement insert = prepareStatement(
                                        conn,
                                        "INSERT INTO spector_control_store (namespace_id, epoch, updated_at) VALUES (?, ?, CURRENT_TIMESTAMP)")) {
                                    insert.setString(1, namespaceId);
                                    insert.setLong(2, nextEpoch);
                                    insert.executeUpdate();
                                } catch (SQLException e) {
                                    conn.rollback();
                                    continue; // retry on insert race
                                }
                            }
                        }
                    }
                    conn.commit();
                    return nextEpoch;
                } catch (SQLException ex) {
                    conn.rollback();
                    throw ex;
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to advance namespace epoch", e);
            }
        }
    }

    @Override
    public Optional<OverrideLeaseRecord> getOverride(String namespaceId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = prepareStatement(
                     conn,
                     "SELECT epoch, owner_id, updated_at FROM spector_control_store " +
                             "WHERE namespace_id = ? AND owner_id IS NOT NULL AND NOT (updated_at < CURRENT_TIMESTAMP)")) {
            ps.setString(1, PREFIX_OVERRIDE + namespaceId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    long epoch = rs.getLong("epoch");
                    String ownerIdRaw = rs.getString("owner_id");
                    Instant expiresAt = getTimestampInstant(rs, "updated_at");

                    if (ownerIdRaw != null && expiresAt != null) {
                        String[] parts = ownerIdRaw.split("\\|", 3);
                        String targetNodeId = parts[0];
                        String fence = parts.length > 1 ? parts[1] : "";
                        Instant createdAt = parts.length > 2 ? Instant.ofEpochMilli(Long.parseLong(parts[2])) : expiresAt;
                        return Optional.of(new OverrideLeaseRecord(namespaceId, targetNodeId, epoch, fence, expiresAt, createdAt));
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get override", e);
        }
        return Optional.empty();
    }

    @Override
    public boolean setOverride(OverrideLeaseRecord override, long expectedLeaseVersion) {
        while (true) {
            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    assertCoordinatorAuthority(conn, expectedLeaseVersion);

                    String nsId = PREFIX_OVERRIDE + override.namespaceId();
                    String ownerId = override.targetNodeId() + "|" + override.fence() + "|" + override.createdAt().toEpochMilli();

                    try (PreparedStatement ps = prepareStatement(
                            conn,
                            "SELECT epoch FROM spector_control_store WHERE namespace_id = ? FOR UPDATE")) {
                        ps.setString(1, nsId);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                try (PreparedStatement update = prepareStatement(
                                        conn,
                                        "UPDATE spector_control_store SET epoch = ?, owner_id = ?, updated_at = ? WHERE namespace_id = ?")) {
                                    update.setLong(1, override.epoch());
                                    update.setString(2, ownerId);
                                    setTimestampWithTimeZone(update, 3, override.expiresAt());
                                    update.setString(4, nsId);
                                    update.executeUpdate();
                                }
                            } else {
                                try (PreparedStatement insert = prepareStatement(
                                        conn,
                                        "INSERT INTO spector_control_store (namespace_id, epoch, owner_id, updated_at) VALUES (?, ?, ?, ?)")) {
                                    insert.setString(1, nsId);
                                    insert.setLong(2, override.epoch());
                                    insert.setString(3, ownerId);
                                    setTimestampWithTimeZone(insert, 4, override.expiresAt());
                                    insert.executeUpdate();
                                } catch (SQLException e) {
                                    conn.rollback();
                                    continue;
                                }
                            }
                        }
                    }
                    conn.commit();
                    return true;
                } catch (SQLException ex) {
                    conn.rollback();
                    throw ex;
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to set override", e);
            }
        }
    }

    @Override
    public boolean removeOverride(String namespaceId, long expectedLeaseVersion) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                assertCoordinatorAuthority(conn, expectedLeaseVersion);

                try (PreparedStatement ps = prepareStatement(
                        conn,
                        "DELETE FROM spector_control_store WHERE namespace_id = ?")) {
                    ps.setString(1, PREFIX_OVERRIDE + namespaceId);
                    ps.executeUpdate();
                }
                conn.commit();
                return true;
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to remove override", e);
        }
    }

    @Override
    public List<OverrideLeaseRecord> listOverrides() {
        List<OverrideLeaseRecord> list = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = prepareStatement(
                     conn,
                     "SELECT namespace_id, epoch, owner_id, updated_at FROM spector_control_store " +
                             "WHERE namespace_id LIKE ? AND owner_id IS NOT NULL AND NOT (updated_at < CURRENT_TIMESTAMP)")) {
            ps.setString(1, PREFIX_OVERRIDE + "%");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String nsId = rs.getString("namespace_id").substring(PREFIX_OVERRIDE.length());
                    long epoch = rs.getLong("epoch");
                    String ownerIdRaw = rs.getString("owner_id");
                    Instant expiresAt = getTimestampInstant(rs, "updated_at");

                    if (ownerIdRaw != null && expiresAt != null) {
                        String[] parts = ownerIdRaw.split("\\|", 3);
                        String targetNodeId = parts[0];
                        String fence = parts.length > 1 ? parts[1] : "";
                        Instant createdAt = parts.length > 2 ? Instant.ofEpochMilli(Long.parseLong(parts[2])) : expiresAt;
                        list.add(new OverrideLeaseRecord(nsId, targetNodeId, epoch, fence, expiresAt, createdAt));
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to list overrides", e);
        }
        return list;
    }

    @Override
    public Instant now() {
        try (Connection conn = dataSource.getConnection()) {
            return queryDatabaseNow(conn);
        } catch (SQLException e) {
            return clock.instant();
        }
    }
}
