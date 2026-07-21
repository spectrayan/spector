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
import java.util.Set;

/**
 * Immutable projection of a single row of the {@code users} table.
 *
 * <p>Captures every persisted column so callers can build a Spring Security
 * {@code UserDetails} (see {@code JdbcUserDetailsService}) without re-querying. The
 * {@code user_id} is the 13-character TSID principal (never the login {@code username});
 * {@code passwordHash} is the {@code {pbkdf2}}-format encoding produced by
 * {@link org.springframework.security.crypto.password.Pbkdf2PasswordEncoder}. The raw
 * password is never present in this type.</p>
 *
 * <p>{@code roles} and {@code scopes} are the parsed sets of the comma-separated
 * {@code roles}/{@code scopes} columns; {@code lockedUntil} and {@code lastLoginAt} are
 * {@code null} when the corresponding column is {@code NULL}.</p>
 *
 * @param userId             13-character TSID primary key (Spring Security principal name)
 * @param username           login handle (unique, case-insensitive)
 * @param passwordHash       {@code {pbkdf2}}-prefixed hash from the password encoder
 * @param email              optional contact email (may be {@code null})
 * @param displayName        optional human-readable name (may be {@code null})
 * @param roles              parsed role names (e.g. {@code ADMIN}, {@code USER})
 * @param scopes             parsed scope names (e.g. {@code memory:read})
 * @param mustChangePassword whether the user must change the password on next login
 * @param active             whether the account is enabled
 * @param failedLoginCount   consecutive failed-login counter
 * @param lockedUntil        lockout expiry, or {@code null} when not locked
 * @param lastLoginAt        last successful login instant, or {@code null}
 * @param createdAt          row creation instant
 * @param updatedAt          row last-modification instant
 */
public record UserRow(
        String userId,
        String username,
        String passwordHash,
        String email,
        String displayName,
        Set<String> roles,
        Set<String> scopes,
        boolean mustChangePassword,
        boolean active,
        int failedLoginCount,
        Instant lockedUntil,
        Instant lastLoginAt,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Whether the account is currently locked relative to the supplied instant.
     *
     * @param now the reference instant (typically {@code Instant.now()})
     * @return {@code true} when {@code lockedUntil} is set and later than {@code now}
     */
    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }
}
