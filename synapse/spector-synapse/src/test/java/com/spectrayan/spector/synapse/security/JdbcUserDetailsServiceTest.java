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
import java.time.temporal.ChronoUnit;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;

import com.spectrayan.spector.synapse.config.SynapseProperties;

/**
 * Unit tests for {@link JdbcUserDetailsService#loadUserByUsername(String)} against an in-memory
 * H2 database whose schema mirrors the {@code users} table of {@code V3__multi_user_auth.sql}.
 *
 * <p>Verifies that the adapted {@code UserDetails} keys off the TSID {@code user_id} (not the login
 * handle), exposes the stored {@code {pbkdf2}} hash as its password, exposes {@code ROLE_*} +
 * {@code SCOPE_*} authorities, maps {@code active} to {@code isEnabled()} and {@code locked_until}
 * to {@code isAccountNonLocked()}, and raises {@link UsernameNotFoundException} for absent handles
 * (Requirements 7.1, 12.1).</p>
 */
class JdbcUserDetailsServiceTest {

    private JdbcClient jdbc;
    private Pbkdf2PasswordEncoder encoder;
    private UserAccountStore store;
    private JdbcUserDetailsService service;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:userdetails-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
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

        encoder = new Pbkdf2PasswordEncoder(
                "", 16, 1, Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256);
        store = new UserAccountStore(jdbc, encoder, new SynapseProperties(0, null, null, null, null, null, null));
        service = new JdbcUserDetailsService(store);
    }

    @Test
    void loadUserByUsernameExposesTsidAsPrincipalNotHandle() {
        String userId = store.createUser("alice", "password1", null, null,
                Set.of("USER"), Set.of("memory:read"), false);

        UserDetails details = service.loadUserByUsername("alice");

        // The principal name is the stable 13-char TSID, never the login handle.
        assertThat(details.getUsername()).isEqualTo(userId).hasSize(13);
        assertThat(details.getUsername()).isNotEqualTo("alice");
    }

    @Test
    void loadUserByUsernameExposesStoredPbkdf2Hash() {
        String userId = store.createUser("bob", "password1", null, null,
                Set.of("USER"), Set.of(), false);
        String storedHash = store.findByUserId(userId).orElseThrow().passwordHash();

        UserDetails details = service.loadUserByUsername("bob");

        assertThat(details.getPassword()).isEqualTo(storedHash);
        // The exposed password is a verifiable hash, never the raw password.
        assertThat(details.getPassword()).isNotEqualTo("password1");
        assertThat(encoder.matches("password1", details.getPassword())).isTrue();
    }

    @Test
    void loadUserByUsernameMapsRolesAndScopesToPrefixedAuthorities() {
        store.createUser("carol", "password1", null, null,
                Set.of("ADMIN", "USER"), Set.of("memory:read", "memory:write"), false);

        UserDetails details = service.loadUserByUsername("carol");

        assertThat(details.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder(
                        "ROLE_ADMIN", "ROLE_USER", "SCOPE_memory:read", "SCOPE_memory:write");
    }

    @Test
    void loadUserByUsernameMapsActiveToEnabled() {
        String userId = store.createUser("dave", "password1", null, null,
                Set.of("USER"), Set.of(), false);

        assertThat(service.loadUserByUsername("dave").isEnabled()).isTrue();

        store.deactivateUser(userId);
        assertThat(service.loadUserByUsername("dave").isEnabled()).isFalse();
    }

    @Test
    void loadUserByUsernameMapsLockedUntilToAccountNonLocked() {
        String userId = store.createUser("erin", "password1", null, null,
                Set.of("USER"), Set.of(), false);

        // No lockout yet: account is non-locked.
        assertThat(service.loadUserByUsername("erin").isAccountNonLocked()).isTrue();

        // Set locked_until into the future directly on the row.
        jdbc.sql("UPDATE users SET locked_until = :until WHERE user_id = :id")
                .param("until", Instant.now().plus(15, ChronoUnit.MINUTES))
                .param("id", userId)
                .update();

        assertThat(service.loadUserByUsername("erin").isAccountNonLocked()).isFalse();
    }

    @Test
    void loadUserByUsernameTreatsPastLockAsNonLocked() {
        String userId = store.createUser("frank", "password1", null, null,
                Set.of("USER"), Set.of(), false);
        jdbc.sql("UPDATE users SET locked_until = :until WHERE user_id = :id")
                .param("until", Instant.now().minus(1, ChronoUnit.MINUTES))
                .param("id", userId)
                .update();

        assertThat(service.loadUserByUsername("frank").isAccountNonLocked()).isTrue();
    }

    @Test
    void loadUserByUsernameThrowsWhenAbsent() {
        assertThatThrownBy(() -> service.loadUserByUsername("nobody"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
