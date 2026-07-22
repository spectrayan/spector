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
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import org.h2.jdbcx.JdbcDataSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;

import com.spectrayan.spector.synapse.config.SynapseProperties;
import com.spectrayan.spector.synapse.config.SynapseProperties.AuthProperties;
import com.spectrayan.spector.synapse.config.SynapseProperties.LockoutProperties;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;

/**
 * Property-based tests (jqwik) for <b>Property 6: Lockout monotonicity</b> of the multi-user
 * auth design.
 *
 * <p>For ANY user, after &ge; {@code lockout.max-attempts} consecutive failed logins the account
 * becomes locked (its {@code locked_until} is set to a time in the future); while the account is
 * locked a further failed attempt never clears or extends the lock and never increments the
 * {@code failed_login_count}; and a successful login within an unlocked window resets
 * {@code failed_login_count} to zero, clears the lock, and stamps {@code last_login_at}.</p>
 *
 * <p>The invariants are exercised directly against
 * {@link UserAccountStore#recordFailure(String)} / {@link UserAccountStore#recordSuccess(String)}
 * backed by an in-memory H2 database whose {@code users} table mirrors
 * {@code V3__multi_user_auth.sql}, using a real (low-iteration) {@link Pbkdf2PasswordEncoder} and an
 * {@link AuthProperties} carrying generated lockout thresholds. Because {@code locked_until} is
 * computed from wall-clock {@code now + minutes} (with {@code minutes >= 1}), a lock established
 * during a try never elapses within that try, so "locked" is equivalent to "{@code locked_until} is
 * in the future".</p>
 *
 * <p><b>Validates: Requirements 2.5, 13.1, 13.2, 13.3, 13.4, 13.5</b></p>
 */
class LockoutMonotonicityPropertyTest {

    private static final String USERNAME = "lockout-user";
    private static final String PASSWORD = "Password123!";

    // ── Generators ──

    /**
     * A random sequence of authentication events: {@code true} = failed attempt,
     * {@code false} = successful attempt. Constrained to a modest length so each try stays fast
     * while still crossing the lockout threshold repeatedly.
     */
    @Provide
    Arbitrary<List<Boolean>> eventSequences() {
        return Arbitraries.of(true, false).list().ofMinSize(1).ofMaxSize(40);
    }

    // ── Property 6: lockout monotonicity over a random event sequence ──

    /**
     * Drives a fresh account through a generated sequence of failure/success events and asserts the
     * lockout invariants after every event.
     *
     * @param maxAttempts generated {@code lockout.max-attempts} (1..10)
     * @param minutes     generated {@code lockout.minutes} (1..60)
     * @param events      generated event sequence (true = failure, false = success)
     */
    @Property(tries = 300)
    void lockoutIsMonotonicAcrossFailuresAndResetsOnSuccess(
            @ForAll @IntRange(min = 1, max = 10) int maxAttempts,
            @ForAll @IntRange(min = 1, max = 60) int minutes,
            @ForAll("eventSequences") @Size(min = 1, max = 40) List<Boolean> events) {

        UserAccountStore store = newStore(maxAttempts, minutes);
        String userId = store.createUser(USERNAME, PASSWORD, null, null,
                Set.of("USER"), Set.of("memory:read"), false);

        // Freshly created account starts unlocked with a zero failure counter.
        UserRow initial = require(store.findByUserId(userId));
        assertThat(initial.failedLoginCount()).isZero();
        assertThat(initial.isLocked(Instant.now())).isFalse();

        for (boolean failure : events) {
            UserRow before = require(store.findByUserId(userId));
            boolean wasLocked = before.isLocked(Instant.now());

            if (failure) {
                store.recordFailure(userId);
                UserRow after = require(store.findByUserId(userId));

                if (wasLocked) {
                    // Requirement 13.4: a failure while locked neither increments the counter
                    // nor clears/extends the existing lock.
                    assertThat(after.failedLoginCount()).isEqualTo(before.failedLoginCount());
                    assertThat(after.lockedUntil()).isEqualTo(before.lockedUntil());
                    assertThat(after.isLocked(Instant.now())).isTrue();
                } else {
                    // Requirement 13.1: a failure while unlocked increments the counter by exactly 1.
                    int expected = before.failedLoginCount() + 1;
                    assertThat(after.failedLoginCount()).isEqualTo(expected);

                    if (expected >= maxAttempts) {
                        // Requirements 2.5, 13.2, 13.3: reaching the threshold locks the account
                        // until now + minutes (a future instant).
                        assertThat(after.isLocked(Instant.now())).isTrue();
                        assertThat(after.lockedUntil()).isNotNull();
                        assertThat(after.lockedUntil()).isAfter(after.updatedAt());
                    } else {
                        // Below threshold the account remains unlocked.
                        assertThat(after.isLocked(Instant.now())).isFalse();
                    }
                }
            } else if (!wasLocked) {
                // Requirement 13.5: a success within an unlocked window resets the counter to zero,
                // clears the lock, and stamps last_login_at. (In the running system a success event
                // cannot fire while locked, since authentication is blocked, so we only exercise
                // recordSuccess in an unlocked window.)
                store.recordSuccess(userId);
                UserRow after = require(store.findByUserId(userId));

                assertThat(after.failedLoginCount()).isZero();
                assertThat(after.lockedUntil()).isNull();
                assertThat(after.isLocked(Instant.now())).isFalse();
                assertThat(after.lastLoginAt()).isNotNull();
            }
            // A success while locked is a no-op in the model (blocked by the auth provider),
            // leaving the lock intact — nothing to assert or invoke.
        }
    }

    // ── Fixtures ──

    /**
     * Builds a {@link UserAccountStore} over a fresh in-memory H2 database whose {@code users}
     * table mirrors {@code V3__multi_user_auth.sql}, wired to a low-iteration real PBKDF2 encoder
     * and an {@link AuthProperties} carrying the generated lockout thresholds.
     */
    private static UserAccountStore newStore(int maxAttempts, int minutes) {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:lockout-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        JdbcClient jdbc = JdbcClient.create(dataSource);
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

        // Real encoder with a single PBKDF2 iteration to keep createUser fast across many tries.
        Pbkdf2PasswordEncoder encoder = new Pbkdf2PasswordEncoder(
                "", 16, 1, Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256);

        SynapseProperties props = new SynapseProperties(
                0, null, null, null, null, null,
                new AuthProperties(true, null, null, null, null, null,
                        new LockoutProperties(maxAttempts, minutes), null));

        return new UserAccountStore(jdbc, encoder, props);
    }

    private static UserRow require(Optional<UserRow> row) {
        return row.orElseThrow(() -> new AssertionError("expected the user row to be present"));
    }
}
