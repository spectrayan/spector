/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-synapse/LICENSE
 *
 * Change Date: July 6, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.synapse.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.spectrayan.spector.memory.id.TsidGenerator;

/**
 * JDBC-backed store for per-user API keys.
 *
 * <p>Only the SHA-256 hex hash (64 lowercase hex chars) of a raw key is ever persisted; the raw
 * key value is returned to the caller exactly once at creation time and is not recoverable from
 * storage. Backed by the {@code api_keys} table created by Flyway {@code V2__multi_user_auth.sql}.</p>
 *
 * <p>All statements are parameterized. Raw key values are never logged.</p>
 */
@Repository
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
     * @param keyId     the 13-char TSID primary key
     * @param userId    the owning user's TSID
     * @param keyHash   the SHA-256 hex hash of the raw key (64 lowercase hex chars)
     * @param scopes    the key's granted scopes
     * @param expiresAt expiry instant, or {@code null} if the key never expires
     * @param revoked   whether the key has been revoked
     * @param createdAt creation instant
     */
    public record ApiKeyRow(String keyId, String userId, String keyHash, Set<String> scopes,
                            Instant expiresAt, boolean revoked, Instant createdAt) {}

    /**
     * Creates a new API key for the given user.
     *
     * <p>Generates a cryptographically-random raw key and a 13-char TSID {@code key_id}, persists
     * only the SHA-256 hex hash of the raw key, and returns the raw key to the caller exactly
     * once. The raw key is never logged or persisted.</p>
     *
     * @param userId    the owning user's TSID
     * @param scopes    the scopes to grant (may be empty; persisted as CSV)
     * @param expiresAt expiry instant, or {@code null} for a non-expiring key
     * @return the generated {@code keyId} and raw key value
     */
    public ApiKeyCreation create(String userId, Set<String> scopes, Instant expiresAt) {
        String rawKey = generateRawKey();
        String keyHash = sha256Hex(rawKey);
        String keyId = TSID.generate();
        String scopesCsv = toCsv(scopes);

        jdbc.sql("""
                INSERT INTO api_keys (key_id, user_id, key_hash, scopes, expires_at, revoked, created_at)
                VALUES (:keyId, :userId, :keyHash, :scopes, :expiresAt, FALSE, :createdAt)
                """)
                .param("keyId", keyId)
                .param("userId", userId)
                .param("keyHash", keyHash)
                .param("scopes", scopesCsv)
                .param("expiresAt", expiresAt != null ? Timestamp.from(expiresAt) : null)
                .param("createdAt", Timestamp.from(Instant.now()))
                .update();

        log.debug("[Auth] Created API key {} for user {}", keyId, userId);
        return new ApiKeyCreation(keyId, rawKey);
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
     * Finds the active row owning the given SHA-256 hex hash, if any.
     *
     * <p>A row is active only when it is both non-revoked and non-expired (a {@code null}
     * {@code expires_at} means it never expires). Returns {@link Optional#empty()} for an unknown,
     * revoked, or expired hash — the caller cannot distinguish these cases.</p>
     *
     * @param sha256HexHash the SHA-256 hex hash of a presented key (64 lowercase hex chars)
     * @return the owning active row, or empty
     */
    public Optional<ApiKeyRow> findActiveByHash(String sha256HexHash) {
        return jdbc.sql("""
                SELECT key_id, user_id, key_hash, scopes, expires_at, revoked, created_at
                FROM api_keys
                WHERE key_hash = :keyHash
                  AND revoked = FALSE
                  AND (expires_at IS NULL OR expires_at > :now)
                """)
                .param("keyHash", sha256HexHash)
                .param("now", Timestamp.from(Instant.now()))
                .query((rs, rowNum) -> new ApiKeyRow(
                        rs.getString("key_id"),
                        rs.getString("user_id"),
                        rs.getString("key_hash"),
                        fromCsv(rs.getString("scopes")),
                        rs.getTimestamp("expires_at") != null
                                ? rs.getTimestamp("expires_at").toInstant() : null,
                        rs.getBoolean("revoked"),
                        rs.getTimestamp("created_at") != null
                                ? rs.getTimestamp("created_at").toInstant() : Instant.now()))
                .optional();
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
