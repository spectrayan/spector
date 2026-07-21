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
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import org.h2.jdbcx.JdbcDataSource;
import org.springframework.jdbc.core.simple.JdbcClient;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Property-based tests (jqwik) for <b>Property 7: API-key hash non-reversibility</b> of the
 * multi-user auth design.
 *
 * <p>FOR ANY created API key, only the SHA-256 hex hash is persisted — the stored
 * {@code api_keys.key_hash} never equals the raw key, always equals {@link ApiKeyStore#sha256Hex}
 * of the raw key, and is exactly 64 lowercase hex characters. Validation succeeds
 * <em>iff</em> {@code SHA-256(presented) == stored hash} for a non-revoked, non-expired row: the
 * owning row is returned for the correct hash, and empty is returned for a wrong key, a revoked
 * key, or an expired key.</p>
 *
 * <p>Each try runs against a fresh in-memory H2 database whose schema mirrors the {@code api_keys}
 * table of {@code V3__multi_user_auth.sql}. The {@code fk_api_keys_user} foreign key to
 * {@code users} is intentionally omitted for this table so that arbitrary generated {@code userId}
 * values can be inserted without seeding matching user rows — the property under test concerns hash
 * storage and lookup, not referential integrity. This mirrors the H2 + {@link JdbcClient} approach
 * used by {@code RefreshTokenStoreTest}.</p>
 *
 * <p><b>Validates: Requirements 15.1, 15.3, 5.3</b></p>
 */
class ApiKeyHashNonReversibilityPropertyTest {

    private static final AtomicLong DB_COUNTER = new AtomicLong();

    /** Holds a fresh {@link JdbcClient} plus the {@link ApiKeyStore} under test for one try. */
    private record Fixture(JdbcClient jdbc, ApiKeyStore store) {}

    /**
     * Builds a fresh, isolated in-memory H2 database and an {@link ApiKeyStore} bound to it. A
     * unique database name per invocation guarantees tries never share state.
     */
    private static Fixture newFixture() {
        JdbcDataSource dataSource = new JdbcDataSource();
        String name = "apikeyhash-" + System.nanoTime() + "-" + DB_COUNTER.incrementAndGet();
        dataSource.setURL("jdbc:h2:mem:" + name + ";DB_CLOSE_DELAY=-1");
        JdbcClient jdbc = JdbcClient.create(dataSource);
        jdbc.sql("""
                CREATE TABLE api_keys (
                    key_id      VARCHAR(13)   NOT NULL,
                    user_id     VARCHAR(13)   NOT NULL,
                    key_hash    VARCHAR(64)   NOT NULL,
                    scopes      VARCHAR(1024) NOT NULL DEFAULT '',
                    expires_at  TIMESTAMP,
                    revoked     BOOLEAN       NOT NULL DEFAULT FALSE,
                    created_at  TIMESTAMP     NOT NULL,
                    PRIMARY KEY (key_id)
                )
                """).update();
        return new Fixture(jdbc, new ApiKeyStore(jdbc));
    }

    // ── Generators ──

    /**
     * Valid owning {@code userId} values: non-blank identifiers of 1..13 chars drawn from a safe
     * alphanumeric alphabet, fitting the {@code api_keys.user_id VARCHAR(13)} column.
     */
    @Provide
    Arbitrary<String> userIds() {
        return Arbitraries.strings()
                .withChars("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789")
                .ofMinLength(1)
                .ofMaxLength(13)
                .filter(s -> !s.isBlank());
    }

    /**
     * Scope sets: zero or more scope tokens from the design's scope vocabulary. Persisted as CSV by
     * {@link ApiKeyStore}, so the specific values do not affect the hash property under test.
     */
    @Provide
    Arbitrary<Set<String>> scopeSets() {
        return Arbitraries.of("memory:read", "memory:write", "memory:admin", "auth:register")
                .set()
                .ofMinSize(0)
                .ofMaxSize(4);
    }

    // ── Properties ──

    /**
     * Requirements 15.1, 15.3 — creating a key persists only the SHA-256 hex hash: the stored
     * {@code key_hash} never equals the raw key, equals {@link ApiKeyStore#sha256Hex} of the raw
     * key, and is exactly 64 lowercase hex characters. The raw key is not recoverable from storage.
     */
    @Property(tries = 300)
    void onlySha256HashIsPersisted_neverTheRawKey(
            @ForAll("userIds") String userId,
            @ForAll("scopeSets") Set<String> scopes) {
        Fixture f = newFixture();
        Instant expiresAt = Instant.now().plus(1, ChronoUnit.HOURS);

        ApiKeyStore.ApiKeyCreation created = f.store().create(userId, scopes, expiresAt);
        String raw = created.rawKey();
        assertThat(raw).isNotBlank();

        String storedHash = f.jdbc().sql("SELECT key_hash FROM api_keys WHERE key_id = :id")
                .param("id", created.keyId())
                .query(String.class)
                .single();

        assertThat(storedHash)
                .isNotEqualTo(raw)
                .hasSize(64)
                .matches("[0-9a-f]{64}")
                .isEqualTo(ApiKeyStore.sha256Hex(raw));

        // The raw key is never recoverable: no row stores it verbatim.
        long rawMatches = f.jdbc().sql("SELECT COUNT(*) FROM api_keys WHERE key_hash = :k")
                .param("k", raw)
                .query(Integer.class)
                .single();
        assertThat(rawMatches).isZero();
    }

    /**
     * Requirement 5.3 — validation succeeds <em>iff</em> {@code SHA-256(presented) == stored hash}
     * for a non-revoked, non-expired row: the correct hash resolves the owning row (with the owning
     * userId), while a wrong key (raw + "x") resolves nothing.
     */
    @Property(tries = 300)
    void validationSucceedsIffPresentedHashMatchesActiveRow(
            @ForAll("userIds") String userId,
            @ForAll("scopeSets") Set<String> scopes) {
        Fixture f = newFixture();
        Instant expiresAt = Instant.now().plus(1, ChronoUnit.HOURS);

        ApiKeyStore.ApiKeyCreation created = f.store().create(userId, scopes, expiresAt);
        String raw = created.rawKey();

        Optional<ApiKeyStore.ApiKeyRow> owning =
                f.store().findActiveByHash(ApiKeyStore.sha256Hex(raw));
        assertThat(owning).isPresent();
        assertThat(owning.get().userId()).isEqualTo(userId);
        assertThat(owning.get().keyId()).isEqualTo(created.keyId());
        assertThat(owning.get().keyHash()).isEqualTo(ApiKeyStore.sha256Hex(raw));

        // A wrong presented key hashes differently and must not resolve any row.
        assertThat(f.store().findActiveByHash(ApiKeyStore.sha256Hex(raw + "x"))).isEmpty();
    }

    /**
     * Requirement 5.3 — a revoked key never validates: after {@code revoke(keyId)},
     * {@code findActiveByHash(sha256Hex(raw))} returns empty even though the hash still matches.
     */
    @Property(tries = 300)
    void revokedKeyNeverValidates(
            @ForAll("userIds") String userId,
            @ForAll("scopeSets") Set<String> scopes) {
        Fixture f = newFixture();
        Instant expiresAt = Instant.now().plus(1, ChronoUnit.HOURS);

        ApiKeyStore.ApiKeyCreation created = f.store().create(userId, scopes, expiresAt);
        String hash = ApiKeyStore.sha256Hex(created.rawKey());
        assertThat(f.store().findActiveByHash(hash)).isPresent();

        assertThat(f.store().revoke(created.keyId())).isTrue();
        assertThat(f.store().findActiveByHash(hash)).isEmpty();
    }

    /**
     * Requirement 5.3 — an expired key never validates: a row whose {@code expires_at} is in the
     * past returns empty from {@code findActiveByHash} even though the hash matches.
     */
    @Property(tries = 300)
    void expiredKeyNeverValidates(
            @ForAll("userIds") String userId,
            @ForAll("scopeSets") Set<String> scopes) {
        Fixture f = newFixture();
        Instant expiresAt = Instant.now().minus(1, ChronoUnit.SECONDS);

        ApiKeyStore.ApiKeyCreation created = f.store().create(userId, scopes, expiresAt);
        String hash = ApiKeyStore.sha256Hex(created.rawKey());

        assertThat(f.store().findActiveByHash(hash)).isEmpty();
    }
}
