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

import java.time.Instant;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * JDBC-backed blocklist of revoked JWT identifiers ({@code jti}).
 *
 * <p>Backs the {@code jti_blocklist} table introduced by the
 * {@code V2__multi_user_auth.sql} Flyway migration ({@code jti, expires_at}).
 * When a user logs out, the access token's {@code jti} is added here so that
 * any subsequent request presenting that {@code jti} is rejected
 * (Requirement 12.4). Entries carry the original token's expiry so a scheduled
 * purge can drop rows that can no longer match a live token.</p>
 */
@Repository
public class JtiBlocklist {

    private static final Logger log = LoggerFactory.getLogger(JtiBlocklist.class);

    private final JdbcClient jdbc;

    public JtiBlocklist(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    /**
     * Adds a {@code jti} to the blocklist. Idempotent: re-blocking a
     * {@code jti} refreshes its stored expiry rather than failing on the
     * primary-key constraint.
     *
     * @param jti       the JWT identifier to block (never {@code null}/blank)
     * @param expiresAt when the original token expires; may be {@code null}
     */
    public void add(String jti, Instant expiresAt) {
        if (jti == null || jti.isBlank()) {
            throw new IllegalArgumentException("jti must not be null or blank");
        }
        jdbc.sql("MERGE INTO jti_blocklist (jti, expires_at) KEY (jti) VALUES (:jti, :expiresAt)")
                .param("jti", jti)
                .param("expiresAt", expiresAt != null ? java.sql.Timestamp.from(expiresAt) : null)
                .update();
        log.debug("[Auth] Blocked jti {}", jti);
    }

    /**
     * Tests whether the given {@code jti} is present in the blocklist.
     *
     * @param jti the JWT identifier to check
     * @return {@code true} when the {@code jti} is blocked; {@code false} when
     *         it is absent or the argument is {@code null}/blank
     */
    public boolean isBlocked(String jti) {
        if (jti == null || jti.isBlank()) {
            return false;
        }
        int count = jdbc.sql("SELECT COUNT(*) FROM jti_blocklist WHERE jti = :jti")
                .param("jti", jti)
                .query(Integer.class)
                .single();
        return count > 0;
    }
}
