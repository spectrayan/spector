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

import com.spectrayan.spector.memory.id.TsidGenerator;
import com.spectrayan.spector.synapse.config.SynapseProperties;
import com.spectrayan.spector.synapse.config.SynapseProperties.AuthProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * JDBC-backed account lifecycle store for the {@code users} table created by
 * {@code V2__multi_user_auth.sql}.
 *
 * <p>All statements are parameterized {@link JdbcClient} calls (no string interpolation).
 * Password hashing is delegated entirely to the injected {@link Pbkdf2PasswordEncoder}
 * (built-in per-encode salt and constant-time {@code matches}); the raw password is never
 * persisted or logged (Requirements 14.1, 14.5). User identity is a 13-character TSID
 * {@code user_id} — the login {@code username} never appears in the id, JWT {@code sub},
 * or any foreign key (Requirements 16.1, 16.2). Usernames are unique under case-insensitive
 * comparison (Requirements 16.3, 16.4).</p>
 *
 * <p>This store owns only account persistence and the lockout counters
 * ({@link #recordFailure(String)} / {@link #recordSuccess(String)}); the Spring Security
 * {@code UserDetails} adapter lives in a separate {@code JdbcUserDetailsService}.</p>
 */
@Component
public class UserAccountStore {

    private static final Logger log = LoggerFactory.getLogger(UserAccountStore.class);

    /** Default administrator login handle seeded on first startup. */
    private static final String DEFAULT_ADMIN_USERNAME = "admin";

    /**
     * Node-local TSID generator for {@code user_id} assignment. Reuses the shared
     * {@link TsidGenerator} implementation from {@code spector-memory}.
     */
    private static final TsidGenerator TSID = new TsidGenerator();

    private final JdbcClient jdbc;
    private final Pbkdf2PasswordEncoder encoder;
    private final AuthProperties auth;

    /**
     * Creates the store.
     *
     * @param jdbc    Spring {@link JdbcClient} bound to the H2 datasource
     * @param encoder shared PBKDF2 password encoder (see {@code SecurityConfig})
     * @param props   bound {@code spector.*} configuration (supplies lockout thresholds)
     */
    public UserAccountStore(JdbcClient jdbc, Pbkdf2PasswordEncoder encoder, SynapseProperties props) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.auth = props.auth();
    }

    /**
     * Creates a new user with a generated 13-character TSID {@code user_id}.
     *
     * <p>The {@code plainPassword} is hashed via the {@link Pbkdf2PasswordEncoder} and stored
     * as the {@code {pbkdf2}} encoding; it is never persisted in the clear or logged. The
     * {@code username} must be unique under case-insensitive comparison, otherwise a
     * {@link DuplicateUsernameException} is thrown and no row is written.</p>
     *
     * @param username           unique login handle (case-insensitive)
     * @param plainPassword      raw password (hashed before persistence, never stored raw)
     * @param email              optional contact email (may be {@code null})
     * @param displayName        optional display name (may be {@code null})
     * @param roles              role names to persist as a CSV column
     * @param scopes             scope names to persist as a CSV column
     * @param mustChangePassword whether the user must change the password on next login
     * @return the generated {@code user_id} (13-char TSID)
     * @throws DuplicateUsernameException if the username already exists (case-insensitive)
     */
    public String createUser(String username, String plainPassword, String email,
                             String displayName, Set<String> roles, Set<String> scopes,
                             boolean mustChangePassword) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username must not be blank");
        }
        if (plainPassword == null || plainPassword.isBlank()) {
            throw new IllegalArgumentException("password must not be blank");
        }
        if (usernameExists(username)) {
            throw new DuplicateUsernameException(username);
        }

        String userId = TSID.generate();
        String passwordHash = encoder.encode(plainPassword);
        Instant now = Instant.now();

        try {
            jdbc.sql("""
                            INSERT INTO users (user_id, username, password_hash, email, display_name,
                                               roles, scopes, must_change_password, active,
                                               failed_login_count, created_at, updated_at)
                            VALUES (:userId, :username, :passwordHash, :email, :displayName,
                                    :roles, :scopes, :mustChange, TRUE, 0, :now, :now)
                            """)
                    .param("userId", userId)
                    .param("username", username)
                    .param("passwordHash", passwordHash)
                    .param("email", email)
                    .param("displayName", displayName)
                    .param("roles", toCsv(roles))
                    .param("scopes", toCsv(scopes))
                    .param("mustChange", mustChangePassword)
                    .param("now", now)
                    .update();
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // Race with a concurrent create under the DB-level UNIQUE(username) constraint.
            throw new DuplicateUsernameException(username);
        }

        log.info("Created user id={} (roles={})", userId, roles);
        return userId;
    }

    /**
     * Changes a user's password after verifying the current password.
     *
     * @param username login handle (case-insensitive)
     * @param oldPw    current password to verify against the stored hash
     * @param newPw    new password to hash and store
     * @return {@code true} when the current password matched and the hash was updated;
     *         {@code false} when the user does not exist or {@code oldPw} did not match
     */
    public boolean changePassword(String username, String oldPw, String newPw) {
        Optional<UserRow> row = findByUsername(username);
        if (row.isEmpty() || !encoder.matches(oldPw, row.get().passwordHash())) {
            return false;
        }
        int rows = jdbc.sql("""
                        UPDATE users SET password_hash = :hash, must_change_password = FALSE,
                               updated_at = :now
                        WHERE user_id = :userId
                        """)
                .param("hash", encoder.encode(newPw))
                .param("now", Instant.now())
                .param("userId", row.get().userId())
                .update();
        if (rows > 0) {
            log.info("Password changed for user id={}", row.get().userId());
        }
        return rows > 0;
    }

    /**
     * Force-resets a user's password without verifying the old one; also clears any lockout
     * and the must-change flag. Intended for administrative reset flows.
     *
     * @param userId 13-char TSID of the target user
     * @param newPw  new password to hash and store
     * @return {@code true} when a row was updated
     */
    public boolean forceResetPassword(String userId, String newPw) {
        int rows = jdbc.sql("""
                        UPDATE users SET password_hash = :hash, must_change_password = FALSE,
                               failed_login_count = 0, locked_until = NULL, updated_at = :now
                        WHERE user_id = :userId
                        """)
                .param("hash", encoder.encode(newPw))
                .param("now", Instant.now())
                .param("userId", userId)
                .update();
        if (rows > 0) {
            log.info("Force-reset password for user id={}", userId);
        }
        return rows > 0;
    }

    /**
     * Finds a user by login handle using case-insensitive comparison.
     *
     * @param username login handle
     * @return the matching {@link UserRow}, or empty when none exists
     */
    public Optional<UserRow> findByUsername(String username) {
        if (username == null) {
            return Optional.empty();
        }
        return jdbc.sql("""
                        SELECT user_id, username, password_hash, email, display_name, roles, scopes,
                               must_change_password, active, failed_login_count, locked_until,
                               last_login_at, created_at, updated_at
                        FROM users WHERE LOWER(username) = LOWER(:username)
                        """)
                .param("username", username)
                .query(UserAccountStore::mapRow)
                .optional();
    }

    /**
     * Finds a user by TSID {@code user_id}.
     *
     * @param userId 13-char TSID
     * @return the matching {@link UserRow}, or empty when none exists
     */
    public Optional<UserRow> findByUserId(String userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return jdbc.sql("""
                        SELECT user_id, username, password_hash, email, display_name, roles, scopes,
                               must_change_password, active, failed_login_count, locked_until,
                               last_login_at, created_at, updated_at
                        FROM users WHERE user_id = :userId
                        """)
                .param("userId", userId)
                .query(UserAccountStore::mapRow)
                .optional();
    }

    /**
     * Lists all users ordered by username.
     *
     * @return every persisted user (active and inactive)
     */
    public List<UserRow> listUsers() {
        return jdbc.sql("""
                        SELECT user_id, username, password_hash, email, display_name, roles, scopes,
                               must_change_password, active, failed_login_count, locked_until,
                               last_login_at, created_at, updated_at
                        FROM users ORDER BY username
                        """)
                .query(UserAccountStore::mapRow)
                .list();
    }

    /**
     * Deactivates a user (sets {@code active = FALSE}); a no-op when the user is absent.
     *
     * @param userId 13-char TSID of the target user
     * @return {@code true} when a row was updated
     */
    public boolean deactivateUser(String userId) {
        int rows = jdbc.sql("UPDATE users SET active = FALSE, updated_at = :now WHERE user_id = :userId")
                .param("now", Instant.now())
                .param("userId", userId)
                .update();
        if (rows > 0) {
            log.info("Deactivated user id={}", userId);
        }
        return rows > 0;
    }

    /**
     * Applies an administrative update to a targeted user account (Requirement 12.11).
     *
     * <p>Only the supplied ({@code non-null}) fields are changed; a {@code null} argument leaves the
     * corresponding column unchanged. Returns the refreshed {@link UserRow} after the update, or
     * {@link Optional#empty()} when no user with the given id exists — in which case no row is
     * modified. Passwords are never touched by this method.</p>
     *
     * @param userId      13-char TSID of the target user
     * @param active      new enabled state, or {@code null} to leave unchanged
     * @param roles       replacement role set, or {@code null} to leave unchanged
     * @param scopes      replacement scope set, or {@code null} to leave unchanged
     * @param displayName replacement display name, or {@code null} to leave unchanged
     * @return the updated {@link UserRow}, or empty when the target user does not exist
     */
    public Optional<UserRow> updateAccount(String userId, Boolean active, Set<String> roles,
                                           Set<String> scopes, String displayName) {
        Optional<UserRow> existing = findByUserId(userId);
        if (existing.isEmpty()) {
            return Optional.empty();
        }
        UserRow row = existing.get();
        boolean newActive = active != null ? active : row.active();
        Set<String> newRoles = roles != null ? roles : row.roles();
        Set<String> newScopes = scopes != null ? scopes : row.scopes();
        String newDisplayName = displayName != null ? displayName : row.displayName();

        jdbc.sql("""
                        UPDATE users SET active = :active, roles = :roles, scopes = :scopes,
                               display_name = :displayName, updated_at = :now
                        WHERE user_id = :userId
                        """)
                .param("active", newActive)
                .param("roles", toCsv(newRoles))
                .param("scopes", toCsv(newScopes))
                .param("displayName", newDisplayName)
                .param("now", Instant.now())
                .param("userId", userId)
                .update();
        log.info("Updated account for user id={}", userId);
        return findByUserId(userId);
    }

    /**
     * Idempotently seeds the default administrator account with its must-change-password flag
     * set to {@code true}. When an {@code admin} account already exists this is a no-op
     * (Requirement 12.7). The raw {@code defaultPassword} is hashed and never logged.
     *
     * @param defaultPassword the seed administrator password
     */
    public void seedDefaultAdmin(String defaultPassword) {
        if (findByUsername(DEFAULT_ADMIN_USERNAME).isPresent()) {
            log.info("Default admin account already present; skipping seed");
            return;
        }
        try {
            createUser(DEFAULT_ADMIN_USERNAME, defaultPassword, "admin@localhost", "Administrator",
                    Set.of("ADMIN"), Set.of("memory:read", "memory:write"), true);
            log.info("Seeded default admin account (must change password on first login)");
        } catch (DuplicateUsernameException e) {
            // Concurrent seed on another node/thread already created it — idempotent success.
            log.info("Default admin account seeded concurrently; skipping");
        }
    }

    /**
     * Records a failed login attempt for a user.
     *
     * <p>Monotonic while locked: when the account is already locked (its {@code locked_until}
     * is later than now), the counter is neither incremented nor is the lock cleared or
     * extended (Requirement 13.4). Otherwise the counter is incremented by one and, when it
     * reaches or exceeds {@code spector.auth.lockout.max-attempts}, {@code locked_until} is set
     * to now plus {@code spector.auth.lockout.minutes} (Requirements 13.1, 13.2).</p>
     *
     * @param userId 13-char TSID of the target user
     */
    public void recordFailure(String userId) {
        Optional<UserRow> current = findByUserId(userId);
        if (current.isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        if (current.get().isLocked(now)) {
            // Already locked — do not clear, extend, or increment (monotonic).
            return;
        }
        int newCount = current.get().failedLoginCount() + 1;
        if (newCount >= auth.lockout().maxAttempts()) {
            Instant lockUntil = now.plus(Duration.ofMinutes(auth.lockout().minutes()));
            jdbc.sql("""
                            UPDATE users SET failed_login_count = :count, locked_until = :lockUntil,
                                   updated_at = :now
                            WHERE user_id = :userId
                            """)
                    .param("count", newCount)
                    .param("lockUntil", lockUntil)
                    .param("now", now)
                    .param("userId", userId)
                    .update();
            log.warn("Account locked for user id={} until {} after {} failed attempts",
                    userId, lockUntil, newCount);
        } else {
            jdbc.sql("""
                            UPDATE users SET failed_login_count = :count, updated_at = :now
                            WHERE user_id = :userId
                            """)
                    .param("count", newCount)
                    .param("now", now)
                    .param("userId", userId)
                    .update();
        }
    }

    /**
     * Records a successful login: resets {@code failed_login_count} to zero, clears
     * {@code locked_until}, and sets {@code last_login_at} to now (Requirement 13.5).
     *
     * @param userId 13-char TSID of the target user
     */
    public void recordSuccess(String userId) {
        Instant now = Instant.now();
        jdbc.sql("""
                        UPDATE users SET failed_login_count = 0, locked_until = NULL,
                               last_login_at = :now, updated_at = :now
                        WHERE user_id = :userId
                        """)
                .param("now", now)
                .param("userId", userId)
                .update();
    }

    // ── Internal helpers ──

    private boolean usernameExists(String username) {
        Integer count = jdbc.sql("SELECT COUNT(*) FROM users WHERE LOWER(username) = LOWER(:username)")
                .param("username", username)
                .query(Integer.class)
                .single();
        return count != null && count > 0;
    }

    /**
     * Joins a set of tokens into the comma-separated form stored in the {@code roles}/{@code scopes}
     * columns. A {@code null} or empty set becomes the empty string (matching the column default).
     */
    private static String toCsv(Set<String> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return "";
        }
        var cleaned = new LinkedHashSet<String>();
        for (String token : tokens) {
            if (token != null && !token.isBlank()) {
                cleaned.add(token.trim());
            }
        }
        return String.join(",", cleaned);
    }

    /**
     * Splits a comma-separated {@code roles}/{@code scopes} column into a set, dropping blanks.
     */
    private static Set<String> fromCsv(String csv) {
        var result = new LinkedHashSet<String>();
        if (csv == null || csv.isBlank()) {
            return result;
        }
        for (String token : csv.split(",")) {
            String trimmed = token.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private static UserRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new UserRow(
                rs.getString("user_id"),
                rs.getString("username"),
                rs.getString("password_hash"),
                rs.getString("email"),
                rs.getString("display_name"),
                fromCsv(rs.getString("roles")),
                fromCsv(rs.getString("scopes")),
                rs.getBoolean("must_change_password"),
                rs.getBoolean("active"),
                rs.getInt("failed_login_count"),
                toInstant(rs, "locked_until"),
                toInstant(rs, "last_login_at"),
                toInstant(rs, "created_at"),
                toInstant(rs, "updated_at"));
    }

    private static Instant toInstant(ResultSet rs, String column) throws SQLException {
        var ts = rs.getTimestamp(column);
        return ts != null ? ts.toInstant() : null;
    }
}
