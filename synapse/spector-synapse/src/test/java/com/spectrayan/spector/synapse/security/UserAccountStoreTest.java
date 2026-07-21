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

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;

import com.spectrayan.spector.synapse.config.SynapseProperties;

/**
 * Unit tests for {@link UserAccountStore} against an in-memory H2 database whose schema mirrors
 * the {@code users} table of {@code V3__multi_user_auth.sql}.
 *
 * <p>Uses a real (low-iteration) {@link Pbkdf2PasswordEncoder} so hashing/verification behaves
 * exactly as in production while keeping the tests fast. Covers user creation and TSID id shape,
 * {@code {pbkdf2}} hash persistence (never the raw password), case-insensitive uniqueness and
 * lookup, password change semantics, idempotent admin seeding, administrative updates, and
 * deactivation (Requirements 12.1, 12.2, 12.7, 16.1, 16.3, 16.4).</p>
 */
class UserAccountStoreTest {

    private JdbcClient jdbc;
    private Pbkdf2PasswordEncoder encoder;
    private UserAccountStore store;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:useraccount-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        jdbc = JdbcClient.create(dataSource);
        jdbc.sql("""
                CREATE TABLE users (
                    user_id               VARCHAR(13)  NOT NULL,
                    username              VARCHAR(64)  NOT NULL,
                    password_hash         VARCHAR(512) NOT NULL,
                    email                 VARCHAR(320),
                    display_name          VARCHAR(255),
                    roles                 VARCHAR(512) NOT NULL DEFAULT '',
                    scopes                VARCHAR(1024) NOT NULL DEFAULT '',
                    must_change_password  BOOLEAN      NOT NULL DEFAULT FALSE,
                    active                BOOLEAN      NOT NULL DEFAULT TRUE,
                    failed_login_count    INT          NOT NULL DEFAULT 0,
                    locked_until          TIMESTAMP,
                    last_login_at         TIMESTAMP,
                    created_at            TIMESTAMP    NOT NULL,
                    updated_at            TIMESTAMP    NOT NULL,
                    PRIMARY KEY (user_id),
                    CONSTRAINT uq_users_username UNIQUE (username)
                )
                """).update();

        // Real encoder, minimal iterations for fast tests (matches SecurityConfig salt/algorithm).
        encoder = new Pbkdf2PasswordEncoder(
                "", 16, 1, Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256);
        store = new UserAccountStore(jdbc, encoder, defaultProperties());
    }

    private static SynapseProperties defaultProperties() {
        // Compact constructors coerce every null to a documented default (auth disabled,
        // lockout 5 attempts / 15 minutes, PBKDF2 310000 iterations).
        return new SynapseProperties(0, null, null, null, null, null, null);
    }

    // ── createUser ──

    @Test
    void createUserReturnsThirteenCharTsidAndPersistsHashNeverRawPassword() {
        String raw = "s3cret-password";
        String userId = store.createUser("alice", raw, "alice@localhost", "Alice",
                Set.of("USER"), Set.of("memory:read"), false);

        assertThat(userId).isNotBlank().hasSize(13);

        String storedHash = jdbc.sql("SELECT password_hash FROM users WHERE user_id = :id")
                .param("id", userId)
                .query(String.class)
                .single();

        // The raw password is never persisted; the stored value is a verifiable PBKDF2 hash.
        assertThat(storedHash).isNotBlank().isNotEqualTo(raw);
        assertThat(encoder.matches(raw, storedHash)).isTrue();

        // No column anywhere retains the plaintext password.
        long rawMatches = jdbc.sql("SELECT COUNT(*) FROM users WHERE password_hash = :p")
                .param("p", raw)
                .query(Integer.class)
                .single();
        assertThat(rawMatches).isZero();
    }

    @Test
    void createUserPersistsRolesScopesAndFlags() {
        String userId = store.createUser("bob", "password1", null, "Bob",
                Set.of("ADMIN", "USER"), Set.of("memory:read", "memory:write"), true);

        UserRow row = store.findByUserId(userId).orElseThrow();
        assertThat(row.username()).isEqualTo("bob");
        assertThat(row.displayName()).isEqualTo("Bob");
        assertThat(row.roles()).containsExactlyInAnyOrder("ADMIN", "USER");
        assertThat(row.scopes()).containsExactlyInAnyOrder("memory:read", "memory:write");
        assertThat(row.mustChangePassword()).isTrue();
        assertThat(row.active()).isTrue();
    }

    // ── duplicate username (case-insensitive) ──

    @Test
    void createUserRejectsDuplicateUsernameCaseInsensitively() {
        store.createUser("Carol", "password1", null, null, Set.of("USER"), Set.of(), false);

        assertThatThrownBy(() ->
                store.createUser("carol", "password2", null, null, Set.of("USER"), Set.of(), false))
                .isInstanceOf(DuplicateUsernameException.class);

        // The duplicate attempt wrote no additional row.
        long count = jdbc.sql("SELECT COUNT(*) FROM users WHERE LOWER(username) = 'carol'")
                .query(Integer.class)
                .single();
        assertThat(count).isEqualTo(1);
    }

    // ── findByUsername / findByUserId ──

    @Test
    void findByUsernameIsCaseInsensitive() {
        String userId = store.createUser("Dave", "password1", null, null, Set.of("USER"), Set.of(), false);

        assertThat(store.findByUsername("dave")).isPresent()
                .get().extracting(UserRow::userId).isEqualTo(userId);
        assertThat(store.findByUsername("DAVE")).isPresent()
                .get().extracting(UserRow::userId).isEqualTo(userId);
        assertThat(store.findByUsername("nobody")).isEmpty();
        assertThat(store.findByUsername(null)).isEmpty();
    }

    @Test
    void findByUserIdReturnsMatchingRowOrEmpty() {
        String userId = store.createUser("erin", "password1", null, null, Set.of("USER"), Set.of(), false);

        assertThat(store.findByUserId(userId)).isPresent()
                .get().extracting(UserRow::username).isEqualTo("erin");
        assertThat(store.findByUserId("UNKNOWN000000")).isEmpty();
        assertThat(store.findByUserId(null)).isEmpty();
    }

    // ── changePassword ──

    @Test
    void changePasswordWithCorrectOldPasswordUpdatesHash() {
        String userId = store.createUser("frank", "old-password", null, null,
                Set.of("USER"), Set.of(), true);

        assertThat(store.changePassword("frank", "old-password", "new-password")).isTrue();

        UserRow row = store.findByUserId(userId).orElseThrow();
        assertThat(encoder.matches("new-password", row.passwordHash())).isTrue();
        assertThat(encoder.matches("old-password", row.passwordHash())).isFalse();
        // Changing the password clears the must-change flag.
        assertThat(row.mustChangePassword()).isFalse();
    }

    @Test
    void changePasswordWithWrongOldPasswordReturnsFalseAndLeavesHashUnchanged() {
        String userId = store.createUser("grace", "correct-password", null, null,
                Set.of("USER"), Set.of(), false);
        String before = store.findByUserId(userId).orElseThrow().passwordHash();

        assertThat(store.changePassword("grace", "wrong-password", "new-password")).isFalse();

        String after = store.findByUserId(userId).orElseThrow().passwordHash();
        assertThat(after).isEqualTo(before);
        assertThat(encoder.matches("correct-password", after)).isTrue();
        assertThat(encoder.matches("new-password", after)).isFalse();
    }

    @Test
    void changePasswordForUnknownUserReturnsFalse() {
        assertThat(store.changePassword("ghost", "whatever", "new-password")).isFalse();
    }

    // ── seedDefaultAdmin ──

    @Test
    void seedDefaultAdminIsIdempotent() {
        store.seedDefaultAdmin("admin-password");

        UserRow first = store.findByUsername("admin").orElseThrow();
        assertThat(first.roles()).contains("ADMIN");
        assertThat(first.mustChangePassword()).isTrue();

        // Second call is a no-op: no new row, same user_id retained.
        store.seedDefaultAdmin("different-password");

        long adminCount = jdbc.sql("SELECT COUNT(*) FROM users WHERE LOWER(username) = 'admin'")
                .query(Integer.class)
                .single();
        assertThat(adminCount).isEqualTo(1);

        UserRow second = store.findByUsername("admin").orElseThrow();
        assertThat(second.userId()).isEqualTo(first.userId());
        assertThat(second.passwordHash()).isEqualTo(first.passwordHash());
    }

    // ── updateAccount ──

    @Test
    void updateAccountAppliesActiveRolesScopesAndDisplayName() {
        String userId = store.createUser("heidi", "password1", null, "Heidi",
                Set.of("USER"), Set.of("memory:read"), false);

        Optional<UserRow> updated = store.updateAccount(userId, false,
                Set.of("ADMIN"), Set.of("memory:read", "memory:write"), "Heidi Admin");

        assertThat(updated).isPresent();
        UserRow row = updated.get();
        assertThat(row.active()).isFalse();
        assertThat(row.roles()).containsExactly("ADMIN");
        assertThat(row.scopes()).containsExactlyInAnyOrder("memory:read", "memory:write");
        assertThat(row.displayName()).isEqualTo("Heidi Admin");
    }

    @Test
    void updateAccountLeavesNullFieldsUnchanged() {
        String userId = store.createUser("ivan", "password1", null, "Ivan",
                Set.of("USER"), Set.of("memory:read"), false);

        UserRow row = store.updateAccount(userId, null, null, null, null).orElseThrow();

        assertThat(row.active()).isTrue();
        assertThat(row.roles()).containsExactly("USER");
        assertThat(row.scopes()).containsExactly("memory:read");
        assertThat(row.displayName()).isEqualTo("Ivan");
    }

    @Test
    void updateAccountReturnsEmptyForMissingUser() {
        assertThat(store.updateAccount("UNKNOWN000000", true, Set.of("USER"), Set.of(), "X"))
                .isEmpty();
    }

    // ── deactivateUser ──

    @Test
    void deactivateUserDisablesAccount() {
        String userId = store.createUser("judy", "password1", null, null,
                Set.of("USER"), Set.of(), false);

        assertThat(store.deactivateUser(userId)).isTrue();
        assertThat(store.findByUserId(userId).orElseThrow().active()).isFalse();
    }

    @Test
    void deactivateUnknownUserIsNoOp() {
        assertThat(store.deactivateUser("UNKNOWN000000")).isFalse();
    }
}
