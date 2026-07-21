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

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import org.h2.jdbcx.JdbcDataSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;

import com.nimbusds.jwt.SignedJWT;
import com.spectrayan.spector.memory.StorageLayout;
import com.spectrayan.spector.memory.id.TsidGenerator;
import com.spectrayan.spector.synapse.config.SynapseProperties;
import com.spectrayan.spector.synapse.config.SynapseProperties.AuthProperties;
import com.spectrayan.spector.synapse.config.SynapseProperties.JwtProperties;
import com.spectrayan.spector.synapse.config.SynapseProperties.LockoutProperties;
import com.spectrayan.spector.synapse.config.SynapseProperties.Pbkdf2Properties;
import com.spectrayan.spector.synapse.security.ServerAccessTokenMinter.MintedAccessToken;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Assume;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Property-based tests (jqwik) for <b>Property 8: TSID / PII avoidance</b> of the
 * multi-user auth design.
 *
 * <p>FOR ANY created User, the assigned {@code user_id} is a valid 13-character Crockford
 * Base32 TSID, and the login {@code username} never appears in any identity-derived artifact:
 * the resolved namespace directory path
 * ({@link StorageLayout#namespaceDirSharded(Path, String)}), the JWT {@code sub} claim, or the
 * persisted foreign keys of the {@code api_keys} / {@code refresh_tokens} tables (which equal the
 * {@code user_id}). API-key and refresh-token values are stored only as SHA-256 hashes, so the
 * username cannot leak through them either.</p>
 *
 * <p>This property crosses the store, path, and token layers, so it exercises the real
 * {@link UserAccountStore} against an in-memory H2 database whose schema mirrors
 * {@code V3__multi_user_auth.sql} (the same in-memory H2 + {@link JdbcClient} pattern used by
 * {@code RefreshTokenStoreTest} / {@code JtiBlocklistTest}), a real {@link Pbkdf2PasswordEncoder}
 * (a single iteration for speed — hashing behaviour is covered elsewhere), a real
 * {@link ServerAccessTokenMinter}, {@link ApiKeyStore}, and {@link RefreshTokenStore}.</p>
 *
 * <p>Generated usernames are drawn from a realistic handle alphabet (letters, digits, and the
 * separators {@code . _ -}) of length 1..64 and are constructed to always contain at least one
 * separator. Because the identity-derived portion of a namespace path — {@code namespaces/AA/BB/
 * userId} — is composed solely of the lowercase-hex shard segments and the uppercase Crockford
 * {@code userId}, it can never contain a separator; requiring one in every username therefore
 * rules out coincidental substring matches and keeps the "username never appears" assertion
 * meaningful rather than probabilistic.</p>
 *
 * <p><b>Validates: Requirements 16.1, 16.2</b></p>
 */
class UserAccountTsidPiiAvoidancePropertyTest {

    /** A 13-character Crockford Base32 TSID (alphabet {@code 0-9 A-Z} excluding I, L, O, U). */
    private static final String TSID_PATTERN = "^[0-9A-HJKMNP-TV-Z]{13}$";

    /** HS256 signing secret (>= 32 bytes as required by {@link ServerAccessTokenMinter}). */
    private static final String JWT_SECRET = "test-secret-test-secret-test-secret-0123456789";

    /** Controlled persistence root — {@code namespaceDirSharded} never mutates the filesystem. */
    private static final Path BASE = Path.of("target", "tsidpiiprop").toAbsolutePath().normalize();

    private static final String ALNUM =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    // ── Generators ──

    /**
     * Realistic login handles: letters/digits/{@code . _ -}, length 1..64, always carrying at
     * least one separator so the whole handle can never be a substring of the separator-free,
     * identity-derived namespace path.
     */
    @Provide
    Arbitrary<String> usernames() {
        Arbitrary<String> prefix = Arbitraries.strings().withChars(ALNUM).ofMinLength(0).ofMaxLength(30);
        Arbitrary<Character> separator = Arbitraries.of('.', '_', '-');
        Arbitrary<String> suffix = Arbitraries.strings().withChars(ALNUM).ofMinLength(0).ofMaxLength(30);
        return Combinators.combine(prefix, separator, suffix).as((p, sep, s) -> p + sep + s);
    }

    /** Passwords: 8..128 characters over a realistic alphabet; non-blank. */
    @Provide
    Arbitrary<String> passwords() {
        return Arbitraries.strings()
                .withChars(ALNUM + "._-!@#$%^&*")
                .ofMinLength(8)
                .ofMaxLength(128)
                .filter(s -> !s.isBlank());
    }

    // ── Property ──

    /**
     * FOR ANY (username, password): {@code createUser} assigns a 13-char TSID {@code user_id};
     * the username never appears in the namespace path, the JWT {@code sub} claim, or the
     * persisted foreign keys — those are all derived from (and equal to) the {@code user_id}.
     */
    @Property(tries = 200)
    void userIdIsTsidAndUsernameNeverLeaksAcrossPathTokenAndKeys(
            @ForAll("usernames") String username,
            @ForAll("passwords") String password) throws Exception {

        Fixture fx = newFixture();

        String userId = fx.store.createUser(username, password, null, null,
                Set.of("USER"), Set.of("memory:read", "memory:write"), false);

        // (Req 16.1) user_id is a valid 13-character Crockford Base32 TSID — never the username.
        assertThat(userId).matches(TSID_PATTERN);
        assertThat(userId).doesNotContain(username);
        assertThat(userId).isNotEqualTo(username);

        // (Req 16.2) The namespace directory is resolved from the userId, never the username.
        Path nsDir = StorageLayout.namespaceDirSharded(BASE, userId);
        // Terminal segment is the userId itself.
        assertThat(nsDir.getFileName().toString()).isEqualTo(userId);
        // The identity-derived (relative) portion — namespaces/AA/BB/userId — cannot embed the
        // username, which always carries a separator absent from that path.
        String relative = BASE.relativize(nsDir).toString();
        assertThat(relative).doesNotContain(username);
        // The full path likewise excludes the username (skip only if the controlled base path
        // itself happens to embed the generated handle).
        Assume.that(!BASE.toString().contains(username));
        assertThat(nsDir.toString()).doesNotContain(username);

        // (Req 16.2) The JWT `sub` claim is the userId (never the login username).
        MintedAccessToken minted = fx.minter.mint(userId, List.of("memory:read"), List.of("USER"));
        String sub = SignedJWT.parse(minted.token()).getJWTClaimsSet().getSubject();
        assertThat(sub).isEqualTo(userId);
        assertThat(sub).doesNotContain(username);

        // (Req 16.2) Persisted foreign keys equal the userId and never carry the username.
        fx.apiKeys.create(userId, Set.of("memory:read"), Instant.now().plus(1, ChronoUnit.DAYS));
        fx.refreshTokens.create(userId, Instant.now().plus(1, ChronoUnit.DAYS));

        String apiKeyFk = single(fx.jdbc, "SELECT user_id FROM api_keys");
        String refreshFk = single(fx.jdbc, "SELECT user_id FROM refresh_tokens");
        assertThat(apiKeyFk).isEqualTo(userId);
        assertThat(apiKeyFk).doesNotContain(username);
        assertThat(refreshFk).isEqualTo(userId);
        assertThat(refreshFk).doesNotContain(username);

        // No persisted foreign key equals the username.
        assertThat(countWhereUserId(fx.jdbc, "api_keys", username)).isZero();
        assertThat(countWhereUserId(fx.jdbc, "refresh_tokens", username)).isZero();

        // The username is confined to the `username` column; the primary key is the TSID.
        assertThat(single(fx.jdbc, "SELECT user_id FROM users")).isEqualTo(userId);
        assertThat(single(fx.jdbc, "SELECT username FROM users")).isEqualTo(username);
    }

    // ── Fixture ──

    /** Per-try store/token wiring backed by a fresh in-memory H2 database. */
    private record Fixture(JdbcClient jdbc, UserAccountStore store, ServerAccessTokenMinter minter,
                           ApiKeyStore apiKeys, RefreshTokenStore refreshTokens) {
    }

    private static Fixture newFixture() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:tsidpii-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        JdbcClient jdbc = JdbcClient.create(dataSource);
        createSchema(jdbc);

        AuthProperties auth = new AuthProperties(
                true,
                new JwtProperties(JWT_SECRET, Duration.ofHours(1)),
                null, null, null,
                new Pbkdf2Properties(1),
                new LockoutProperties(5, 15),
                null);
        SynapseProperties props = new SynapseProperties(0, null, null, null, null, null, auth);

        Pbkdf2PasswordEncoder encoder = new Pbkdf2PasswordEncoder(
                "", 16, 1, Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256);

        return new Fixture(
                jdbc,
                new UserAccountStore(jdbc, encoder, props),
                new ServerAccessTokenMinter(props),
                new ApiKeyStore(jdbc),
                new RefreshTokenStore(jdbc, new TsidGenerator(0)));
    }

    /** Schema mirroring {@code V3__multi_user_auth.sql} (users + FK-owning key/token tables). */
    private static void createSchema(JdbcClient jdbc) {
        jdbc.sql("""
                CREATE TABLE users (
                    user_id               VARCHAR(13)   NOT NULL,
                    username              VARCHAR(64)   NOT NULL,
                    password_hash         VARCHAR(512)  NOT NULL,
                    email                 VARCHAR(320),
                    display_name          VARCHAR(255),
                    roles                 VARCHAR(512)  NOT NULL DEFAULT '',
                    scopes                VARCHAR(1024) NOT NULL DEFAULT '',
                    must_change_password  BOOLEAN       NOT NULL DEFAULT FALSE,
                    active                BOOLEAN       NOT NULL DEFAULT TRUE,
                    failed_login_count    INT           NOT NULL DEFAULT 0,
                    locked_until          TIMESTAMP,
                    last_login_at         TIMESTAMP,
                    created_at            TIMESTAMP     NOT NULL,
                    updated_at            TIMESTAMP     NOT NULL,
                    PRIMARY KEY (user_id),
                    CONSTRAINT uq_users_username UNIQUE (username)
                )
                """).update();
        jdbc.sql("""
                CREATE TABLE api_keys (
                    key_id      VARCHAR(13)   NOT NULL,
                    user_id     VARCHAR(13)   NOT NULL,
                    key_hash    VARCHAR(64)   NOT NULL,
                    scopes      VARCHAR(1024) NOT NULL DEFAULT '',
                    expires_at  TIMESTAMP,
                    revoked     BOOLEAN       NOT NULL DEFAULT FALSE,
                    created_at  TIMESTAMP     NOT NULL,
                    PRIMARY KEY (key_id),
                    CONSTRAINT fk_api_keys_user FOREIGN KEY (user_id) REFERENCES users (user_id)
                )
                """).update();
        jdbc.sql("""
                CREATE TABLE refresh_tokens (
                    token_id    VARCHAR(13)   NOT NULL,
                    user_id     VARCHAR(13)   NOT NULL,
                    token_hash  VARCHAR(64)   NOT NULL,
                    expires_at  TIMESTAMP,
                    revoked     BOOLEAN       NOT NULL DEFAULT FALSE,
                    PRIMARY KEY (token_id),
                    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (user_id)
                )
                """).update();
    }

    private static String single(JdbcClient jdbc, String sql) {
        return jdbc.sql(sql).query(String.class).single();
    }

    private static long countWhereUserId(JdbcClient jdbc, String table, String userIdValue) {
        return jdbc.sql("SELECT COUNT(*) FROM " + table + " WHERE user_id = :u")
                .param("u", userIdValue)
                .query(Integer.class)
                .single();
    }
}
