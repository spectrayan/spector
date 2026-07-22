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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.spectrayan.spector.memory.id.TsidGenerator;

/**
 * Unit tests for {@link RefreshTokenStore} against an in-memory H2 database
 * whose schema mirrors {@code V2__multi_user_auth.sql}.
 */
class RefreshTokenStoreTest {

    private static final String USER_ID = "0123456789ABC";

    private JdbcClient jdbc;
    private RefreshTokenStore store;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:refreshtoken-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        jdbc = JdbcClient.create(dataSource);
        jdbc.sql("""
                CREATE TABLE refresh_tokens (
                    token_id    VARCHAR(13)   NOT NULL,
                    user_id     VARCHAR(13)   NOT NULL,
                    token_hash  VARCHAR(64)   NOT NULL,
                    expires_at  TIMESTAMP,
                    revoked     BOOLEAN       NOT NULL DEFAULT FALSE,
                    PRIMARY KEY (token_id)
                )
                """).update();
        store = new RefreshTokenStore(jdbc, new TsidGenerator(0));
    }

    @Test
    void createReturnsRawTokenAndStoresOnlyItsHash() {
        String raw = store.create(USER_ID, Instant.now().plus(30, ChronoUnit.DAYS));

        assertThat(raw).isNotBlank();

        // The raw token must never be persisted — only its 64-char lowercase hex hash.
        String storedHash = jdbc.sql("SELECT token_hash FROM refresh_tokens WHERE user_id = :u")
                .param("u", USER_ID)
                .query(String.class)
                .single();
        assertThat(storedHash)
                .isNotEqualTo(raw)
                .hasSize(64)
                .matches("[0-9a-f]{64}");
        assertThat(storedHash).isEqualTo(RefreshTokenStore.sha256Hex(raw));

        long rawMatches = jdbc.sql("SELECT COUNT(*) FROM refresh_tokens WHERE token_hash = :t")
                .param("t", raw)
                .query(Integer.class)
                .single();
        assertThat(rawMatches).isZero();
    }

    @Test
    void findActiveReturnsOwningRowForNonRevokedNonExpiredToken() {
        Instant expiresAt = Instant.now().plus(1, ChronoUnit.HOURS);
        String raw = store.create(USER_ID, expiresAt);

        Optional<RefreshTokenStore.RefreshTokenRow> row =
                store.findActive(RefreshTokenStore.sha256Hex(raw));

        assertThat(row).isPresent();
        assertThat(row.get().userId()).isEqualTo(USER_ID);
        assertThat(row.get().tokenId()).isNotBlank();
    }

    @Test
    void findActiveReturnsEmptyForExpiredToken() {
        String raw = store.create(USER_ID, Instant.now().minus(1, ChronoUnit.SECONDS));

        assertThat(store.findActive(RefreshTokenStore.sha256Hex(raw))).isEmpty();
    }

    @Test
    void findActiveReturnsEmptyForRevokedToken() {
        String raw = store.create(USER_ID, Instant.now().plus(1, ChronoUnit.HOURS));
        String hash = RefreshTokenStore.sha256Hex(raw);

        String tokenId = store.findActive(hash).orElseThrow().tokenId();
        assertThat(store.revoke(tokenId)).isTrue();

        assertThat(store.findActive(hash)).isEmpty();
    }

    @Test
    void findActiveReturnsEmptyForUnknownHash() {
        assertThat(store.findActive(RefreshTokenStore.sha256Hex("never-issued"))).isEmpty();
        assertThat(store.findActive(null)).isEmpty();
        assertThat(store.findActive("  ")).isEmpty();
    }

    @Test
    void revokeByHashRevokesActiveToken() {
        String raw = store.create(USER_ID, Instant.now().plus(1, ChronoUnit.HOURS));
        String hash = RefreshTokenStore.sha256Hex(raw);

        assertThat(store.revokeByHash(hash)).isTrue();
        assertThat(store.findActive(hash)).isEmpty();
        // Second revoke is a no-op.
        assertThat(store.revokeByHash(hash)).isFalse();
    }

    @Test
    void revokeUnknownTokenIsNoOp() {
        assertThat(store.revoke("UNKNOWNTOKEN0")).isFalse();
        assertThat(store.revoke(null)).isFalse();
    }

    @Test
    void createRejectsBlankUserId() {
        Instant exp = Instant.now().plus(1, ChronoUnit.HOURS);
        assertThatThrownBy(() -> store.create("  ", exp))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createProducesDistinctRawTokensAndHashes() {
        String a = store.create(USER_ID, Instant.now().plus(1, ChronoUnit.HOURS));
        String b = store.create(USER_ID, Instant.now().plus(1, ChronoUnit.HOURS));

        assertThat(a).isNotEqualTo(b);
        assertThat(RefreshTokenStore.sha256Hex(a)).isNotEqualTo(RefreshTokenStore.sha256Hex(b));
    }
}
