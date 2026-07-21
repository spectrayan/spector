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
package com.spectrayan.spector.synapse.config;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import com.spectrayan.spector.synapse.config.SynapseProperties.AuthProperties;

/**
 * Unit tests for {@link AuthProperties} default coercion and Spring configuration binding
 * (including {@code ${...}} placeholder / environment resolution for the {@code jwt.secret}
 * and {@code default-admin.password} secrets).
 *
 * <p>Requirements: 14.7 (default PBKDF2 iterations), 18.1/18.2/18.4 (documented defaults and
 * environment-sourced secrets).</p>
 */
@DisplayName("AuthProperties — defaults and binding")
class AuthPropertiesTest {

    @Nested
    @DisplayName("Record default coercion (constructed directly)")
    class DefaultCoercion {

        @Test
        @DisplayName("applies every documented default when sub-records are absent")
        void appliesDocumentedDefaults() {
            AuthProperties auth = new AuthProperties(
                    false, null, null, null, null, null, null, null);

            assertThat(auth.enabled()).isFalse();

            // jwt.ttl default = 1 hour; secret is left null until supplied via env.
            assertThat(auth.jwt()).isNotNull();
            assertThat(auth.jwt().ttl()).isEqualTo(Duration.ofHours(1));
            assertThat(auth.jwt().secret()).isNull();

            // refresh.ttl default = 30 days.
            assertThat(auth.refresh()).isNotNull();
            assertThat(auth.refresh().ttl()).isEqualTo(Duration.ofDays(30));

            // pbkdf2.iterations default = 310000 (OWASP-2024 baseline).
            assertThat(auth.pbkdf2()).isNotNull();
            assertThat(auth.pbkdf2().iterations()).isEqualTo(310_000);

            // lockout defaults = 5 attempts / 15 minutes.
            assertThat(auth.lockout()).isNotNull();
            assertThat(auth.lockout().maxAttempts()).isEqualTo(5);
            assertThat(auth.lockout().minutes()).isEqualTo(15);

            // public-paths default set.
            assertThat(auth.publicPaths())
                    .containsExactly("/actuator/health", "/api/docs");

            // oidc empty (disabled) by default.
            assertThat(auth.oidc()).isNotNull();
            assertThat(auth.oidc().jwksUrl()).isEmpty();
            assertThat(auth.oidc().issuer()).isEmpty();

            // default-admin present but password unset until supplied via env.
            assertThat(auth.defaultAdmin()).isNotNull();
            assertThat(auth.defaultAdmin().password()).isNull();
        }

        @Test
        @DisplayName("coerces out-of-range numeric tuning knobs back to documented defaults")
        void coercesOutOfRangeNumericValues() {
            AuthProperties auth = new AuthProperties(
                    true,
                    new SynapseProperties.JwtProperties("s", Duration.ZERO),
                    new SynapseProperties.RefreshProperties(Duration.ofSeconds(-1)),
                    null,
                    null,
                    new SynapseProperties.Pbkdf2Properties(0),
                    new SynapseProperties.LockoutProperties(0, 0),
                    List.of());

            assertThat(auth.jwt().ttl()).isEqualTo(Duration.ofHours(1));
            assertThat(auth.refresh().ttl()).isEqualTo(Duration.ofDays(30));
            assertThat(auth.pbkdf2().iterations()).isEqualTo(310_000);
            assertThat(auth.lockout().maxAttempts()).isEqualTo(5);
            assertThat(auth.lockout().minutes()).isEqualTo(15);
            // Empty public-paths list falls back to the documented default.
            assertThat(auth.publicPaths()).containsExactly("/actuator/health", "/api/docs");
        }

        @Test
        @DisplayName("preserves valid supplied values without coercion")
        void preservesSuppliedValues() {
            AuthProperties auth = new AuthProperties(
                    true,
                    new SynapseProperties.JwtProperties("jwt-secret", Duration.ofMinutes(30)),
                    new SynapseProperties.RefreshProperties(Duration.ofDays(7)),
                    new SynapseProperties.OidcProperties("https://idp/jwks", "https://idp"),
                    new SynapseProperties.DefaultAdminProperties("admin-pw"),
                    new SynapseProperties.Pbkdf2Properties(600_000),
                    new SynapseProperties.LockoutProperties(3, 30),
                    List.of("/custom"));

            assertThat(auth.enabled()).isTrue();
            assertThat(auth.jwt().secret()).isEqualTo("jwt-secret");
            assertThat(auth.jwt().ttl()).isEqualTo(Duration.ofMinutes(30));
            assertThat(auth.refresh().ttl()).isEqualTo(Duration.ofDays(7));
            assertThat(auth.oidc().jwksUrl()).isEqualTo("https://idp/jwks");
            assertThat(auth.oidc().issuer()).isEqualTo("https://idp");
            assertThat(auth.defaultAdmin().password()).isEqualTo("admin-pw");
            assertThat(auth.pbkdf2().iterations()).isEqualTo(600_000);
            assertThat(auth.lockout().maxAttempts()).isEqualTo(3);
            assertThat(auth.lockout().minutes()).isEqualTo(30);
            assertThat(auth.publicPaths()).containsExactly("/custom");
        }
    }

    @Nested
    @DisplayName("Spring configuration binding")
    class SpringBinding {

        private final ApplicationContextRunner runner = new ApplicationContextRunner()
                .withUserConfiguration(TestConfig.class);

        @Test
        @DisplayName("binds documented defaults when only the master switch is present")
        void bindsDefaults() {
            runner.withPropertyValues("spector.auth.enabled=false")
                    .run(ctx -> {
                        AuthProperties auth = ctx.getBean(SynapseProperties.class).auth();
                        assertThat(auth.enabled()).isFalse();
                        assertThat(auth.jwt().ttl()).isEqualTo(Duration.ofHours(1));
                        assertThat(auth.refresh().ttl()).isEqualTo(Duration.ofDays(30));
                        assertThat(auth.pbkdf2().iterations()).isEqualTo(310_000);
                        assertThat(auth.lockout().maxAttempts()).isEqualTo(5);
                        assertThat(auth.lockout().minutes()).isEqualTo(15);
                        assertThat(auth.publicPaths())
                                .containsExactly("/actuator/health", "/api/docs");
                        assertThat(auth.oidc().jwksUrl()).isEmpty();
                        assertThat(auth.oidc().issuer()).isEmpty();
                    });
        }

        @Test
        @DisplayName("binds explicitly supplied auth values")
        void bindsSuppliedValues() {
            runner.withPropertyValues(
                            "spector.auth.enabled=true",
                            "spector.auth.jwt.ttl=2h",
                            "spector.auth.refresh.ttl=10d",
                            "spector.auth.oidc.jwks-url=https://idp/jwks",
                            "spector.auth.oidc.issuer=https://idp",
                            "spector.auth.pbkdf2.iterations=400000",
                            "spector.auth.lockout.max-attempts=7",
                            "spector.auth.lockout.minutes=20",
                            "spector.auth.public-paths=/actuator/health,/api/docs,/ping")
                    .run(ctx -> {
                        AuthProperties auth = ctx.getBean(SynapseProperties.class).auth();
                        assertThat(auth.enabled()).isTrue();
                        assertThat(auth.jwt().ttl()).isEqualTo(Duration.ofHours(2));
                        assertThat(auth.refresh().ttl()).isEqualTo(Duration.ofDays(10));
                        assertThat(auth.oidc().jwksUrl()).isEqualTo("https://idp/jwks");
                        assertThat(auth.oidc().issuer()).isEqualTo("https://idp");
                        assertThat(auth.pbkdf2().iterations()).isEqualTo(400_000);
                        assertThat(auth.lockout().maxAttempts()).isEqualTo(7);
                        assertThat(auth.lockout().minutes()).isEqualTo(20);
                        assertThat(auth.publicPaths())
                                .containsExactly("/actuator/health", "/api/docs", "/ping");
                    });
        }

        @Test
        @DisplayName("resolves jwt.secret and default-admin.password directly from bound values")
        void bindsSecretsFromValues() {
            runner.withPropertyValues(
                            "spector.auth.enabled=true",
                            "spector.auth.jwt.secret=super-secret-signing-key",
                            "spector.auth.default-admin.password=change-me-now")
                    .run(ctx -> {
                        AuthProperties auth = ctx.getBean(SynapseProperties.class).auth();
                        assertThat(auth.jwt().secret()).isEqualTo("super-secret-signing-key");
                        assertThat(auth.defaultAdmin().password()).isEqualTo("change-me-now");
                    });
        }

        @Test
        @DisplayName("resolves jwt.secret and default-admin.password through ${...} placeholders")
        void resolvesSecretsViaPlaceholder() {
            // Mirrors how the YAML sources secrets from the environment: the placeholder is
            // resolved against another property present in the Spring Environment.
            runner.withPropertyValues(
                            "SPECTOR_AUTH_JWT_SECRET=env-jwt-secret",
                            "SPECTOR_ADMIN_PASSWORD=env-admin-pw",
                            "spector.auth.enabled=true",
                            "spector.auth.jwt.secret=${SPECTOR_AUTH_JWT_SECRET}",
                            "spector.auth.default-admin.password=${SPECTOR_ADMIN_PASSWORD}")
                    .run(ctx -> {
                        AuthProperties auth = ctx.getBean(SynapseProperties.class).auth();
                        assertThat(auth.jwt().secret()).isEqualTo("env-jwt-secret");
                        assertThat(auth.defaultAdmin().password()).isEqualTo("env-admin-pw");
                    });
        }
    }

    @Configuration
    @EnableConfigurationProperties(SynapseProperties.class)
    static class TestConfig {
    }
}
