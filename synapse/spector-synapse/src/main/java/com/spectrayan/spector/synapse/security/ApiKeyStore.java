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
package com.spectrayan.spector.synapse.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.spectrayan.spector.kernel.id.TsidGenerator;
import com.spectrayan.spector.synapse.catalog.exception.CrossTenantAccessException;

/**
 * JDBC-backed store for per-user API keys.
 *
 * <p>Only the SHA-256 hex hash (64 lowercase hex chars) of a raw key is ever persisted; the raw
 * key value is returned to the caller exactly once at creation time and is not recoverable from
 * storage. Backed by the {@code api_keys} table created by Flyway {@code V3__multi_user_auth.sql}.</p>
 *
 * <p><b>Deprecation Notice:</b> This store is deprecated in favor of the universal
 * {@link com.spectrayan.spector.synapse.connector.repository.JdbcEncryptedCredentialProvider}
 * backed by the {@code credentials} table, which provides AES-256-GCM envelope encryption,
 * BYOK support, and multi-tenant credential isolation. Use {@code /api/v1/credentials}
 * for all outbound secrets, channel tokens, and provider keys.</p>
 *
 * @deprecated since 0.1.0-alpha in favor of the universal {@code credentials} vault
 */
@Deprecated(since = "0.1.0-alpha")
@Repository
@org.springframework.context.annotation.DependsOn("flyway")
public class ApiKeyStore {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyStore.class);

    /** Number of random bytes in a generated raw API key (256 bits of entropy). */
    private static final int RAW_KEY_BYTES = 32;

    private static final TsidGenerator TSID = new TsidGenerator(0);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder KEY_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final HexFormat HEX = HexFormat.of();

    private final JdbcClient jdbc;

    public ApiKeyStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Result of creating an API key. Carries the persisted {@code keyId} and the raw key value,
     * which is exposed to the caller exactly once and never stored.
     *
     * @param keyId  the 13-char TSID identifying the persisted key row
     * @param rawKey the raw API key value (returned once; not recoverable from storage)
     */
    public record ApiKeyCreation(String keyId, String rawKey) {}

    /**
     * A persisted API key row (never carries the raw key value).
     *
     * @param keyId      the 13-char TSID primary key
     * @param userId     the owning user's TSID
     * @param keyHash    the SHA-256 hex hash of the raw key (64 lowercase hex chars)
     * @param scopes     the key's granted scopes
     * @param expiresAt  expiry instant, or {@code null} if the key never expires
     * @param revoked    whether the key has been revoked
     * @param createdAt  creation instant
     * @param name       optional key name or label
     * @param keyPrefix  display prefix (e.g. "spk_abc12345")
     * @param lastUsedAt last-used instant, or {@code null} if never used
     */
    public record ApiKeyRow(String keyId, String userId, String keyHash, Set<String> scopes,
                            Instant expiresAt, boolean revoked, Instant createdAt,
                            String name, String keyPrefix, Instant lastUsedAt) {

        public ApiKeyRow(String keyId, String userId, String keyHash, Set<String> scopes,
                         Instant expiresAt, boolean revoked, Instant createdAt) {
            this(keyId, userId, keyHash, scopes, expiresAt, revoked, createdAt, null, null, null);
        }
    }

    /**
     * Creates a new API key for the given user.
     *
     * @param userId    the owning user's TSID
     * @param scopes    the scopes to grant (may be empty; persisted as CSV)
     * @param expiresAt expiry instant, or {@code null} for a non-expiring key
     * @return the generated {@code keyId} and raw key value
     */
    public ApiKeyCreation create(String userId, Set<String> scopes, Instant expiresAt) {
        return create(userId, null, scopes, expiresAt);
    }

    /**
     * Creates a new API key with a name label for the given user (Issue #1050).
     *
     * @param userId    the owning user's TSID
     * @param name      optional label/name for the key
     * @param scopes    the scopes to grant (may be empty; persisted as CSV)
     * @param expiresAt expiry instant, or {@code null} for a non-expiring key
     * @return the generated {@code keyId} and raw key value
     */
    public ApiKeyCreation create(String userId, String name, Set<String> scopes, Instant expiresAt) {
        String rawKey = generateRawKey();
        String keyHash = sha256Hex(rawKey);
        String keyId = TSID.generate();
        String scopesCsv = toCsv(scopes);
        String keyPrefix = "spk_" + rawKey.substring(0, Math.min(8, rawKey.length()));
        Timestamp now = Timestamp.from(Instant.now());
        Timestamp exp = expiresAt != null ? Timestamp.from(expiresAt) : null;

        try {
            jdbc.sql("""
                    INSERT INTO api_keys (key_id, user_id, key_hash, scopes, expires_at, revoked, created_at, name, key_prefix)
                    VALUES (:keyId, :userId, :keyHash, :scopes, :expiresAt, FALSE, :createdAt, :name, :keyPrefix)
                    """)
                    .param("keyId", keyId)
                    .param("userId", userId)
                    .param("keyHash", keyHash)
                    .param("scopes", scopesCsv)
                    .param("expiresAt", exp)
                    .param("createdAt", now)
                    .param("name", name)
                    .param("keyPrefix", keyPrefix)
                    .update();
        } catch (DataAccessException e) {
            // Fallback for minimal schemas lacking metadata columns (e.g. ad-hoc unit tests)
            jdbc.sql("""
                    INSERT INTO api_keys (key_id, user_id, key_hash, scopes, expires_at, revoked, created_at)
                    VALUES (:keyId, :userId, :keyHash, :scopes, :expiresAt, FALSE, :createdAt)
                    """)
                    .param("keyId", keyId)
                    .param("userId", userId)
                    .param("keyHash", keyHash)
                    .param("scopes", scopesCsv)
                    .param("expiresAt", exp)
                    .param("createdAt", now)
                    .update();
        }

        log.debug("[Auth] Created API key {} for user {}", keyId, userId);
        return new ApiKeyCreation(keyId, rawKey);
    }

    /** Throttle window for last_used_at database writes to prevent lock contention under high RPS. */
    private static final long LAST_USED_THROTTLE_SECONDS = 60;
    private final ConcurrentHashMap<String, Instant> lastUsedThrottle = new ConcurrentHashMap<>();

    /**
     * Updates the {@code last_used_at} timestamp for an authenticated API key.
     *
     * <p>Throttled to at most once per 60 seconds per key to eliminate database row-lock contention
     * and write amplification under high request concurrency.</p>
     *
     * @param keyId the key TSID
     */
    public void recordLastUsed(String keyId) {
        if (keyId == null || keyId.isBlank()) {
            return;
        }
        Instant now = Instant.now();
        Instant last = lastUsedThrottle.get(keyId);
        if (last != null && last.plusSeconds(LAST_USED_THROTTLE_SECONDS).isAfter(now)) {
            return;
        }
        if (lastUsedThrottle.size() > 10_000) {
            lastUsedThrottle.entrySet().removeIf(e -> e.getValue().plusSeconds(300).isBefore(now));
        }
        lastUsedThrottle.put(keyId, now);
        try {
            jdbc.sql("UPDATE api_keys SET last_used_at = :now WHERE key_id = :keyId")
                    .param("now", Timestamp.from(now))
                    .param("keyId", keyId)
                    .update();
        } catch (DataAccessException ignored) {}
    }

    /**
     * Marks the API key with the given id as revoked. Revoked keys never authenticate.
     *
     * @param keyId the 13-char TSID of the key to revoke
     * @return {@code true} if a row was updated, {@code false} if no such key exists
     */
    public boolean revoke(String keyId) {
        int rows = jdbc.sql("UPDATE api_keys SET revoked = TRUE WHERE key_id = :keyId")
                .param("keyId", keyId)
                .update();
        if (rows > 0) {
            log.debug("[Auth] Revoked API key {}", keyId);
        }
        return rows > 0;
    }

    /**
     * Revokes an API key after enforcing ownership and tenant boundaries (Requirement R1, Issue #1050).
     *
     * @param keyId        the 13-char TSID of the key to revoke
     * @param callerUserId the authenticated caller TSID
     * @param callerTenant the caller tenant ID
     * @param targetTenant the owning user tenant ID
     * @param isSuperAdmin whether caller is super-admin
     * @param isAdmin      whether caller is tenant admin
     * @return {@code true} if revoked, {@code false} if not found
     * @throws CrossTenantAccessException if unauthorized
     */
    public boolean revokeWithAuthorization(String keyId, String callerUserId, String callerTenant,
                                          String targetTenant, boolean isSuperAdmin, boolean isAdmin) {
        Optional<ApiKeyRow> keyOpt = findById(keyId);
        if (keyOpt.isEmpty()) {
            return false;
        }
        ApiKeyRow key = keyOpt.get();
        String ownerUserId = key.userId();

        // 1. Account owner can revoke their own key
        if (callerUserId != null && callerUserId.equals(ownerUserId)) {
            return revoke(keyId);
        }

        // 2. Platform operator (super-admin) can revoke any key fleet-wide
        if (isSuperAdmin) {
            return revoke(keyId);
        }

        // 3. Tenant admin can revoke keys within their tenant
        String effectiveCallerTenant = (callerTenant != null && !callerTenant.isBlank()) ? callerTenant : "default";
        String effectiveTargetTenant = (targetTenant != null && !targetTenant.isBlank()) ? targetTenant : "default";

        if (isAdmin && effectiveCallerTenant.equalsIgnoreCase(effectiveTargetTenant)) {
            return revoke(keyId);
        }

        // 4. Unauthorized cross-user or cross-tenant revocation
        throw CrossTenantAccessException.forApiKey(callerUserId, keyId, effectiveTargetTenant);
    }

    /**
     * Finds an API key row by its 13-character TSID.
     *
     * @param keyId the key TSID
     * @return the row if found, or empty
     */
    public Optional<ApiKeyRow> findById(String keyId) {
        try {
            return jdbc.sql("""
                    SELECT key_id, user_id, key_hash, scopes, expires_at, revoked, created_at, name, key_prefix, last_used_at
                    FROM api_keys
                    WHERE key_id = :keyId
                    """)
                    .param("keyId", keyId)
                    .query(ApiKeyStore::mapRow)
                    .optional();
        } catch (DataAccessException e) {
            return jdbc.sql("""
                    SELECT key_id, user_id, key_hash, scopes, expires_at, revoked, created_at
                    FROM api_keys
                    WHERE key_id = :keyId
                    """)
                    .param("keyId", keyId)
                    .query(ApiKeyStore::mapRow)
                    .optional();
        }
    }

    /**
     * Finds all API keys owned by a specific user (Requirement R2).
     *
     * @param userId the user's TSID
     * @return list of API key rows
     */
    public List<ApiKeyRow> findByUserId(String userId) {
        try {
            return jdbc.sql("""
                    SELECT key_id, user_id, key_hash, scopes, expires_at, revoked, created_at, name, key_prefix, last_used_at
                    FROM api_keys
                    WHERE user_id = :userId
                    ORDER BY created_at DESC
                    """)
                    .param("userId", userId)
                    .query(ApiKeyStore::mapRow)
                    .list();
        } catch (DataAccessException e) {
            return jdbc.sql("""
                    SELECT key_id, user_id, key_hash, scopes, expires_at, revoked, created_at
                    FROM api_keys
                    WHERE user_id = :userId
                    ORDER BY created_at DESC
                    """)
                    .param("userId", userId)
                    .query(ApiKeyStore::mapRow)
                    .list();
        }
    }

    /**
     * Lists all API keys across all users (for administrative oversight, Requirement R3).
     *
     * @return list of all API key rows
     */
    public List<ApiKeyRow> findAll() {
        try {
            return jdbc.sql("""
                    SELECT key_id, user_id, key_hash, scopes, expires_at, revoked, created_at, name, key_prefix, last_used_at
                    FROM api_keys
                    ORDER BY created_at DESC
                    """)
                    .query(ApiKeyStore::mapRow)
                    .list();
        } catch (DataAccessException e) {
            return jdbc.sql("""
                    SELECT key_id, user_id, key_hash, scopes, expires_at, revoked, created_at
                    FROM api_keys
                    ORDER BY created_at DESC
                    """)
                    .query(ApiKeyStore::mapRow)
                    .list();
        }
    }

    /**
     * Finds the active row owning the given SHA-256 hex hash, if any.
     *
     * @param sha256HexHash the SHA-256 hex hash of a presented key (64 lowercase hex chars)
     * @return the owning active row, or empty
     */
    public Optional<ApiKeyRow> findActiveByHash(String sha256HexHash) {
        try {
            return jdbc.sql("""
                    SELECT key_id, user_id, key_hash, scopes, expires_at, revoked, created_at, name, key_prefix, last_used_at
                    FROM api_keys
                    WHERE key_hash = :keyHash
                      AND revoked = FALSE
                      AND (expires_at IS NULL OR expires_at > :now)
                    """)
                    .param("keyHash", sha256HexHash)
                    .param("now", Timestamp.from(Instant.now()))
                    .query(ApiKeyStore::mapRow)
                    .optional();
        } catch (DataAccessException e) {
            return jdbc.sql("""
                    SELECT key_id, user_id, key_hash, scopes, expires_at, revoked, created_at
                    FROM api_keys
                    WHERE key_hash = :keyHash
                      AND revoked = FALSE
                      AND (expires_at IS NULL OR expires_at > :now)
                    """)
                    .param("keyHash", sha256HexHash)
                    .param("now", Timestamp.from(Instant.now()))
                    .query(ApiKeyStore::mapRow)
                    .optional();
        }
    }

    private static ApiKeyRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        String name = null;
        String keyPrefix = null;
        Instant lastUsedAt = null;
        try {
            name = rs.getString("name");
        } catch (SQLException ignored) {}
        try {
            keyPrefix = rs.getString("key_prefix");
        } catch (SQLException ignored) {}
        try {
            Timestamp ts = rs.getTimestamp("last_used_at");
            if (ts != null) {
                lastUsedAt = ts.toInstant();
            }
        } catch (SQLException ignored) {}

        String keyId = rs.getString("key_id");
        if (keyPrefix == null || keyPrefix.isBlank()) {
            keyPrefix = "spk_" + (keyId != null && keyId.length() >= 8 ? keyId.substring(0, 8) : (keyId != null ? keyId : ""));
        }

        return new ApiKeyRow(
                keyId,
                rs.getString("user_id"),
                rs.getString("key_hash"),
                fromCsv(rs.getString("scopes")),
                rs.getTimestamp("expires_at") != null
                        ? rs.getTimestamp("expires_at").toInstant() : null,
                rs.getBoolean("revoked"),
                rs.getTimestamp("created_at") != null
                        ? rs.getTimestamp("created_at").toInstant() : Instant.now(),
                name,
                keyPrefix,
                lastUsedAt);
    }

    /**
     * Computes the SHA-256 hex hash of a presented key.
     *
     * @param presentedKey the raw key to hash
     * @return the lowercase 64-character hex SHA-256 digest
     */
    public static String sha256Hex(String presentedKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(presentedKey.getBytes(StandardCharsets.UTF_8));
            return HEX.formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is a required algorithm on every JVM; this is unreachable.
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }

    private static String generateRawKey() {
        byte[] bytes = new byte[RAW_KEY_BYTES];
        RANDOM.nextBytes(bytes);
        return KEY_ENCODER.encodeToString(bytes);
    }

    private static String toCsv(Set<String> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            return "";
        }
        return String.join(",", scopes);
    }

    private static Set<String> fromCsv(String csv) {
        Set<String> scopes = new LinkedHashSet<>();
        if (csv == null || csv.isBlank()) {
            return scopes;
        }
        for (String scope : csv.split(",")) {
            String trimmed = scope.trim();
            if (!trimmed.isEmpty()) {
                scopes.add(trimmed);
            }
        }
        return scopes;
    }
}
