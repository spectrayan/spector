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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class JdbcControlStore implements ControlStore {

    private static final Logger log = LoggerFactory.getLogger(JdbcControlStore.class);

    private static final String NS_MEMBERSHIP = "__membership__";
    private static final String NS_COORDINATOR = "__coordinator__";
    private static final String PREFIX_OVERRIDE = "__override__:";

    private final DataSource dataSource;
    private final Clock clock;

    public JdbcControlStore(DataSource dataSource, Clock clock) {
        this.dataSource = dataSource;
        this.clock = clock;
        initSchema();
    }

    private void initSchema() {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "CREATE TABLE IF NOT EXISTS spector_control_store (" +
                             "namespace_id VARCHAR(255) NOT NULL PRIMARY KEY, " +
                             "epoch BIGINT NOT NULL DEFAULT 0, " +
                             "owner_id VARCHAR(255), " +
                             "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                             ")")) {
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize control store schema", e);
        }
    }

    @Override
    public CellMembership getMembership() {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT epoch, owner_id FROM spector_control_store WHERE namespace_id = ?")) {
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
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "MERGE INTO spector_control_store (namespace_id, epoch, owner_id, updated_at) " +
                             "KEY(namespace_id) VALUES (?, ?, ?, ?)")) {
            ps.setString(1, NS_MEMBERSHIP);
            ps.setLong(2, membership.ringVersion());
            String membersStr = String.join(",", membership.members());
            ps.setString(3, membership.cellId() + "|" + membersStr);
            ps.setTimestamp(4, Timestamp.from(clock.instant()));
            ps.executeUpdate();
        } catch (SQLException e) {
            // MERGE is H2 specific, maybe use standard INSERT ... ON CONFLICT or UPDATE/INSERT logic
            upsertMembership(membership);
        }
    }

    private void upsertMembership(CellMembership membership) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                String membersStr = String.join(",", membership.members());
                String ownerId = membership.cellId() + "|" + membersStr;
                Timestamp now = Timestamp.from(clock.instant());
                
                try (PreparedStatement update = conn.prepareStatement(
                        "UPDATE spector_control_store SET epoch = ?, owner_id = ?, updated_at = ? WHERE namespace_id = ?")) {
                    update.setLong(1, membership.ringVersion());
                    update.setString(2, ownerId);
                    update.setTimestamp(3, now);
                    update.setString(4, NS_MEMBERSHIP);
                    if (update.executeUpdate() == 0) {
                        try (PreparedStatement insert = conn.prepareStatement(
                                "INSERT INTO spector_control_store (namespace_id, epoch, owner_id, updated_at) VALUES (?, ?, ?, ?)")) {
                            insert.setString(1, NS_MEMBERSHIP);
                            insert.setLong(2, membership.ringVersion());
                            insert.setString(3, ownerId);
                            insert.setTimestamp(4, now);
                            insert.executeUpdate();
                        }
                    }
                }
                conn.commit();
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update membership", e);
        }
    }

    @Override
    public CoordinatorLease getCoordinatorLease() {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT epoch, owner_id, updated_at FROM spector_control_store WHERE namespace_id = ?")) {
            ps.setString(1, NS_COORDINATOR);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    long leaseVersion = rs.getLong("epoch");
                    String ownerId = rs.getString("owner_id");
                    Timestamp expiresAt = rs.getTimestamp("updated_at");
                    if (ownerId != null) {
                        String[] parts = ownerId.split("\\|", 2);
                        String holder = parts[0];
                        Instant acquired = parts.length > 1 ? Instant.ofEpochMilli(Long.parseLong(parts[1])) : Instant.EPOCH;
                        return new CoordinatorLease(holder, acquired, expiresAt.toInstant(), leaseVersion);
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
        CoordinatorLease lease = getCoordinatorLease();
        if (lease != null && !lease.isExpired(clock.instant())) {
            return Optional.of(lease);
        }
        return Optional.empty();
    }

    @Override
    public boolean isCoordinator(String nodeId, long expectedLeaseVersion) {
        if (nodeId == null || expectedLeaseVersion <= 0) return false;
        CoordinatorLease lease = getCoordinatorLease();
        return lease != null && !lease.isExpired(clock.instant()) 
                && nodeId.equals(lease.holderNodeId()) 
                && lease.leaseVersion() == expectedLeaseVersion;
    }

    @Override
    public Optional<CoordinatorLease> acquireOrRenewCoordinatorLease(String candidateNodeId, Duration duration) {
        while (true) {
            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    CoordinatorLease result = null;
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT epoch, owner_id, updated_at FROM spector_control_store WHERE namespace_id = ? FOR UPDATE")) {
                        ps.setString(1, NS_COORDINATOR);
                        try (ResultSet rs = ps.executeQuery()) {
                            Instant now = clock.instant();
                            if (rs.next()) {
                                long epoch = rs.getLong("epoch");
                                String ownerIdRaw = rs.getString("owner_id");
                                Timestamp expiresAt = rs.getTimestamp("updated_at");
                                
                                String holder = ownerIdRaw != null ? ownerIdRaw.split("\\|")[0] : null;
                                
                                if (holder == null || expiresAt.toInstant().compareTo(now) <= 0 || candidateNodeId.equals(holder)) {
                                    long nextEpoch = epoch + 1;
                                    Instant newExpiresAt = now.plus(duration);
                                    String newOwnerId = candidateNodeId + "|" + now.toEpochMilli();
                                    
                                    try (PreparedStatement update = conn.prepareStatement(
                                            "UPDATE spector_control_store SET epoch = ?, owner_id = ?, updated_at = ? WHERE namespace_id = ?")) {
                                        update.setLong(1, nextEpoch);
                                        update.setString(2, newOwnerId);
                                        update.setTimestamp(3, Timestamp.from(newExpiresAt));
                                        update.setString(4, NS_COORDINATOR);
                                        update.executeUpdate();
                                    }
                                    result = new CoordinatorLease(candidateNodeId, now, newExpiresAt, nextEpoch);
                                }
                            } else {
                                long nextEpoch = 1L;
                                Instant newExpiresAt = now.plus(duration);
                                String newOwnerId = candidateNodeId + "|" + now.toEpochMilli();
                                
                                try (PreparedStatement insert = conn.prepareStatement(
                                        "INSERT INTO spector_control_store (namespace_id, epoch, owner_id, updated_at) VALUES (?, ?, ?, ?)")) {
                                    insert.setString(1, NS_COORDINATOR);
                                    insert.setLong(2, nextEpoch);
                                    insert.setString(3, newOwnerId);
                                    insert.setTimestamp(4, Timestamp.from(newExpiresAt));
                                    insert.executeUpdate();
                                } catch (SQLException e) {
                                    conn.rollback();
                                    continue;
                                }
                                result = new CoordinatorLease(candidateNodeId, now, newExpiresAt, nextEpoch);
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
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT epoch, owner_id, updated_at FROM spector_control_store WHERE namespace_id = ? FOR UPDATE")) {
                    ps.setString(1, NS_COORDINATOR);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            String ownerIdRaw = rs.getString("owner_id");
                            String holder = ownerIdRaw != null ? ownerIdRaw.split("\\|")[0] : null;
                            if (nodeId.equals(holder)) {
                                long epoch = rs.getLong("epoch");
                                Instant now = clock.instant();
                                try (PreparedStatement update = conn.prepareStatement(
                                        "UPDATE spector_control_store SET epoch = ?, updated_at = ? WHERE namespace_id = ?")) {
                                    update.setLong(1, epoch + 1);
                                    update.setTimestamp(2, Timestamp.from(now)); // Expire immediately
                                    update.setString(3, NS_COORDINATOR);
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
             PreparedStatement ps = conn.prepareStatement("SELECT epoch FROM spector_control_store WHERE namespace_id = ?")) {
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
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT epoch, updated_at FROM spector_control_store WHERE namespace_id = ?")) {
            ps.setString(1, NS_COORDINATOR);
            try (ResultSet rs = ps.executeQuery()) {
                boolean hasActiveLease = false;
                long activeVersion = 0L;
                if (rs.next()) {
                    activeVersion = rs.getLong("epoch");
                    Timestamp expiresAt = rs.getTimestamp("updated_at");
                    if (expiresAt.toInstant().compareTo(clock.instant()) > 0) {
                        hasActiveLease = true;
                    }
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
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT epoch FROM spector_control_store WHERE namespace_id = ? FOR UPDATE")) {
                        ps.setString(1, namespaceId);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                nextEpoch = rs.getLong("epoch") + 1;
                                try (PreparedStatement update = conn.prepareStatement(
                                        "UPDATE spector_control_store SET epoch = ?, updated_at = ? WHERE namespace_id = ?")) {
                                    update.setLong(1, nextEpoch);
                                    update.setTimestamp(2, Timestamp.from(clock.instant()));
                                    update.setString(3, namespaceId);
                                    update.executeUpdate();
                                }
                            } else {
                                try (PreparedStatement insert = conn.prepareStatement(
                                        "INSERT INTO spector_control_store (namespace_id, epoch, updated_at) VALUES (?, ?, ?)")) {
                                    insert.setString(1, namespaceId);
                                    insert.setLong(2, nextEpoch);
                                    insert.setTimestamp(3, Timestamp.from(clock.instant()));
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
             PreparedStatement ps = conn.prepareStatement("SELECT epoch, owner_id, updated_at FROM spector_control_store WHERE namespace_id = ?")) {
            ps.setString(1, PREFIX_OVERRIDE + namespaceId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    long epoch = rs.getLong("epoch");
                    String ownerIdRaw = rs.getString("owner_id");
                    Timestamp expiresAt = rs.getTimestamp("updated_at");
                    
                    if (ownerIdRaw != null && expiresAt.toInstant().compareTo(clock.instant()) > 0) {
                        String[] parts = ownerIdRaw.split("\\|", 3);
                        String targetNodeId = parts[0];
                        String fence = parts.length > 1 ? parts[1] : "";
                        Instant createdAt = parts.length > 2 ? Instant.ofEpochMilli(Long.parseLong(parts[2])) : clock.instant();
                        return Optional.of(new OverrideLeaseRecord(namespaceId, targetNodeId, epoch, fence, expiresAt.toInstant(), createdAt));
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
                    
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT epoch FROM spector_control_store WHERE namespace_id = ? FOR UPDATE")) {
                        ps.setString(1, nsId);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                try (PreparedStatement update = conn.prepareStatement(
                                        "UPDATE spector_control_store SET epoch = ?, owner_id = ?, updated_at = ? WHERE namespace_id = ?")) {
                                    update.setLong(1, override.epoch());
                                    update.setString(2, ownerId);
                                    update.setTimestamp(3, Timestamp.from(override.expiresAt()));
                                    update.setString(4, nsId);
                                    update.executeUpdate();
                                }
                            } else {
                                try (PreparedStatement insert = conn.prepareStatement(
                                        "INSERT INTO spector_control_store (namespace_id, epoch, owner_id, updated_at) VALUES (?, ?, ?, ?)")) {
                                    insert.setString(1, nsId);
                                    insert.setLong(2, override.epoch());
                                    insert.setString(3, ownerId);
                                    insert.setTimestamp(4, Timestamp.from(override.expiresAt()));
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
                
                try (PreparedStatement ps = conn.prepareStatement(
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
             PreparedStatement ps = conn.prepareStatement("SELECT namespace_id, epoch, owner_id, updated_at FROM spector_control_store WHERE namespace_id LIKE ?")) {
            ps.setString(1, PREFIX_OVERRIDE + "%");
            try (ResultSet rs = ps.executeQuery()) {
                Instant now = clock.instant();
                while (rs.next()) {
                    String nsId = rs.getString("namespace_id").substring(PREFIX_OVERRIDE.length());
                    long epoch = rs.getLong("epoch");
                    String ownerIdRaw = rs.getString("owner_id");
                    Timestamp expiresAt = rs.getTimestamp("updated_at");
                    
                    if (ownerIdRaw != null && expiresAt.toInstant().compareTo(now) > 0) {
                        String[] parts = ownerIdRaw.split("\\|", 3);
                        String targetNodeId = parts[0];
                        String fence = parts.length > 1 ? parts[1] : "";
                        Instant createdAt = parts.length > 2 ? Instant.ofEpochMilli(Long.parseLong(parts[2])) : clock.instant();
                        list.add(new OverrideLeaseRecord(nsId, targetNodeId, epoch, fence, expiresAt.toInstant(), createdAt));
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
        return clock.instant();
    }
}
