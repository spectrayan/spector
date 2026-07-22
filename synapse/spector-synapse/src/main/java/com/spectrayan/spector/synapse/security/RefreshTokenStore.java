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
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.spectrayan.spector.memory.id.TsidGenerator;

/**
 * JDBC-backed store for refresh tokens.
 *
 * <p>Backs the {@code refresh_tokens} table introduced by the
 * {@code V2__multi_user_auth.sql} Flyway migration
 * ({@code token_id, user_id, token_hash, expires_at, revoked}).</p>
 *
 * <p>Only the SHA-256 hex hash (64 lowercase hex characters) of a token is
 * persisted. The raw token is generated with a {@link SecureRandom} and
 * returned to the caller exactly once at creation time; it is never stored and
 * never logged, so a database leak cannot expose usable credentials
 * (Requirements 15.2, 15.5, 15.6). A token is only honoured while it is both
 * non-revoked and non-expired.</p>
 */
@Repository
public class RefreshTokenStore {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenStore.class);

    /** Entropy of the raw refresh token (256 bits). */
    private static final int TOKEN_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();

    private final JdbcClient jdbc;
    private final TsidGenerator tsid;

    public RefreshTokenStore(JdbcClient jdbc, TsidGenerator tsid) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.tsid = Objects.requireNonNull(tsid, "tsid");
    }

    /**
     * Creates a new refresh token for the given user, persisting only the
     * SHA-256 hex hash of the raw token value.
     *
     * @param userId    the owning user TSID (never {@code null}/blank)
     * @param expiresAt when the token expires (never {@code null})
     * @return the raw refresh token, returned exactly once and never stored
     */
    public String create(String userId, Instant expiresAt) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId must not be null or blank");
        }
        Objects.requireNonNull(expiresAt, "expiresAt");

        String rawToken = generateRawToken();
        String tokenHash = sha256Hex(rawToken);
        String tokenId = tsid.generate();

        jdbc.sql("""
                        INSERT INTO refresh_tokens (token_id, user_id, token_hash, expires_at, revoked)
                        VALUES (:tokenId, :userId, :tokenHash, :expiresAt, FALSE)
                        """)
                .param("tokenId", tokenId)
                .param("userId", userId)
                .param("tokenHash", tokenHash)
                .param("expiresAt", java.sql.Timestamp.from(expiresAt))
                .update();

        log.debug("[Auth] Issued refresh token {} for user {}", tokenId, userId);
        return rawToken;
    }

    /**
     * Finds the active row owning the given token hash — active meaning both
     * non-revoked and non-expired.
     *
     * @param sha256HexHash the SHA-256 hex hash of the presented raw token
     * @return the owning row when a non-revoked, non-expired match exists;
     *         otherwise {@link Optional#empty()}
     */
    public Optional<RefreshTokenRow> findActive(String sha256HexHash) {
        if (sha256HexHash == null || sha256HexHash.isBlank()) {
            return Optional.empty();
        }
        return jdbc.sql("""
                        SELECT token_id, user_id, expires_at
                        FROM refresh_tokens
                        WHERE token_hash = :tokenHash
                          AND revoked = FALSE
                          AND expires_at > :now
                        """)
                .param("tokenHash", sha256HexHash)
                .param("now", java.sql.Timestamp.from(Instant.now()))
                .query((rs, rowNum) -> new RefreshTokenRow(
                        rs.getString("token_id"),
                        rs.getString("user_id"),
                        rs.getTimestamp("expires_at").toInstant()))
                .optional();
    }

    /**
     * Revokes the token with the given id. Idempotent: revoking an already
     * revoked or unknown token id is a no-op.
     *
     * @param tokenId the token id (TSID) to revoke
     * @return {@code true} when a row transitioned to revoked
     */
    public boolean revoke(String tokenId) {
        if (tokenId == null || tokenId.isBlank()) {
            return false;
        }
        int rows = jdbc.sql("UPDATE refresh_tokens SET revoked = TRUE WHERE token_id = :tokenId AND revoked = FALSE")
                .param("tokenId", tokenId)
                .update();
        return rows > 0;
    }

    /**
     * Revokes the token identified by its SHA-256 hex hash. Useful when only
     * the raw token (and thus its hash) is available at logout/rotation time.
     *
     * @param sha256HexHash the SHA-256 hex hash of the raw token
     * @return {@code true} when a row transitioned to revoked
     */
    public boolean revokeByHash(String sha256HexHash) {
        if (sha256HexHash == null || sha256HexHash.isBlank()) {
            return false;
        }
        int rows = jdbc.sql("UPDATE refresh_tokens SET revoked = TRUE WHERE token_hash = :tokenHash AND revoked = FALSE")
                .param("tokenHash", sha256HexHash)
                .update();
        return rows > 0;
    }

    /**
     * Computes the SHA-256 hex hash (64 lowercase hex characters) of the given
     * value. Exposed so callers holding a raw token (e.g. the refresh endpoint)
     * can hash it for a {@link #findActive(String)} lookup without duplicating
     * the digest logic.
     *
     * @param value the raw value to hash (never {@code null})
     * @return the lowercase hex-encoded SHA-256 digest
     */
    public static String sha256Hex(String value) {
        Objects.requireNonNull(value, "value");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    private String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /**
     * A refresh-token row without its hash or raw value.
     *
     * @param tokenId   the token id (TSID, primary key)
     * @param userId    the owning user TSID
     * @param expiresAt the expiry instant
     */
    public record RefreshTokenRow(String tokenId, String userId, Instant expiresAt) {}
}
