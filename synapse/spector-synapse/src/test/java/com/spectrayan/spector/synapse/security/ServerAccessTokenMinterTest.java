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
import java.time.Duration;
import java.util.Date;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import com.spectrayan.spector.synapse.config.JwtDecoderConfig;
import com.spectrayan.spector.synapse.config.SynapseProperties;
import com.spectrayan.spector.synapse.config.SynapseProperties.AuthProperties;
import com.spectrayan.spector.synapse.config.SynapseProperties.JwtProperties;

/**
 * Unit tests for {@link ServerAccessTokenMinter}.
 *
 * <p>The critical property is round-trip: a minted HS256 token must validate against a decoder
 * built exactly like the production {@code serverJwtDecoder} in {@link JwtDecoderConfig} (same
 * secret, same HS256 algorithm, same issuer/expiry validation) and expose the {@code sub},
 * {@code scope}, {@code roles}, and {@code jti} claims (Requirements 2.1, 2.2).</p>
 */
class ServerAccessTokenMinterTest {

    /** A >= 32-byte secret, the minimum required for HS256. */
    private static final String SECRET = "test-secret-that-is-long-enough-0123456789";

    private ServerAccessTokenMinter minter() {
        AuthProperties auth = new AuthProperties(
                true,
                new JwtProperties(SECRET, Duration.ofHours(1)),
                null, null, null, null, null, null);
        SynapseProperties props = new SynapseProperties(0, null, null, null, null, null, auth);
        return new ServerAccessTokenMinter(props);
    }

    private NimbusJwtDecoder serverDecoder() {
        SecretKey key = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(Duration.ofSeconds(60)),
                new JwtIssuerValidator(JwtDecoderConfig.SERVER_ISSUER)));
        return decoder;
    }

    @Test
    void mintedTokenValidatesAgainstServerDecoder() {
        ServerAccessTokenMinter.MintedAccessToken minted =
                minter().mint("USER0000000AB", List.of("memory:read", "memory:write"), List.of("USER"));

        Jwt decoded = serverDecoder().decode(minted.token());

        assertThat(decoded.getSubject()).isEqualTo("USER0000000AB");
        // SERVER_ISSUER ("spector-synapse") is not a URL, so read the raw string claim rather
        // than getIssuer() which coerces to java.net.URL.
        assertThat(decoded.getClaimAsString("iss")).isEqualTo(JwtDecoderConfig.SERVER_ISSUER);
        assertThat(decoded.getId()).isEqualTo(minted.jti());
        assertThat(decoded.getExpiresAt()).isNotNull();
    }

    @Test
    void mintedTokenCarriesScopeAndRolesClaims() {
        ServerAccessTokenMinter.MintedAccessToken minted =
                minter().mint("USER0000000AB", List.of("memory:read", "memory:write"), List.of("USER", "ADMIN"));

        Jwt decoded = serverDecoder().decode(minted.token());

        // scope is a space-delimited string; roles is a JSON array — both shapes the decoder reads.
        assertThat(decoded.getClaimAsString("scope")).contains("memory:read", "memory:write");
        assertThat(decoded.getClaimAsStringList("roles")).containsExactlyInAnyOrder("USER", "ADMIN");
    }

    @Test
    void expiresInSecondsReflectsConfiguredTtl() {
        ServerAccessTokenMinter.MintedAccessToken minted =
                minter().mint("USER0000000AB", List.of(), List.of());

        assertThat(minted.expiresInSeconds()).isEqualTo(3600L);
    }

    @Test
    void mintFromAuthoritiesStripsScopeAndRolePrefixes() {
        ServerAccessTokenMinter.MintedAccessToken minted = minter().mintFromAuthorities(
                "USER0000000AB",
                List.of(new SimpleGrantedAuthority("SCOPE_memory:read"),
                        new SimpleGrantedAuthority("ROLE_ADMIN")));

        Jwt decoded = serverDecoder().decode(minted.token());

        assertThat(decoded.getClaimAsString("scope")).isEqualTo("memory:read");
        assertThat(decoded.getClaimAsStringList("roles")).containsExactly("ADMIN");
    }

    @Test
    void distinctMintsHaveDistinctJti() {
        ServerAccessTokenMinter minter = minter();
        ServerAccessTokenMinter.MintedAccessToken a = minter.mint("USER0000000AB", List.of(), List.of());
        ServerAccessTokenMinter.MintedAccessToken b = minter.mint("USER0000000AB", List.of(), List.of());

        assertThat(a.jti()).isNotEqualTo(b.jti());
    }

    @Test
    void subjectIsRequired() {
        ServerAccessTokenMinter minter = minter();
        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> minter.mint("  ", List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void expiryIsInTheFuture() {
        ServerAccessTokenMinter.MintedAccessToken minted =
                minter().mint("USER0000000AB", List.of(), List.of());
        assertThat(minted.expiresAt()).isAfter(new Date().toInstant());
    }
}
