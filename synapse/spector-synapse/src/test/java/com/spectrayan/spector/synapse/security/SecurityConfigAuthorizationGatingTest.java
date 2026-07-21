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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

import com.spectrayan.spector.synapse.config.SecurityConfig;
import com.spectrayan.spector.synapse.config.SynapseProperties;
import com.spectrayan.spector.synapse.config.SynapseProperties.AuthProperties;
import org.spectrayan.testfixtures.synapse.auth.SecurityGatingChainConfig;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/**
 * Slice tests for the authorization gating produced by the production
 * {@link com.spectrayan.spector.synapse.config.SecurityConfig} filter chain (task 11.3).
 *
 * <h3>Approach</h3>
 * <p>Rather than mirror the security policy in a hand-written test config (as the sibling property
 * test does), these tests drive the <em>production</em> {@link SecurityConfig#filterChain} directly:
 * a minimal {@link AnnotationConfigWebApplicationContext} constructs the real chain via
 * {@code new SecurityConfig(props).filterChain(http, apiKeyFilter, props, jwtResolverProvider)} and
 * exposes it to {@link MockMvc} through {@code springSecurity()}. The extended
 * {@link ApiKeyAuthenticationFilter} is wired with a mock {@link ApiKeyStore} (never exercised —
 * the credential states are simulated at the authorization layer with spring-security-test's
 * {@code authentication(...)} post-processor). No OIDC/HS256 {@code AuthenticationManagerResolver}
 * bean is present, so the {@code ObjectProvider} resolves empty and the OAuth2 resource-server
 * wiring is skipped; bearer-token validation itself is covered by the decoder unit tests. Because
 * the production code expresses scope/role gating with method security ({@code @PreAuthorize}), the
 * fixture config enables {@code @EnableMethodSecurity} and annotates one probe route to exercise the
 * {@code 403} branch through the same authorization pipeline.</p>
 *
 * <p>The fixture configs and probe controller deliberately live in the non-scanned
 * {@code com.spectrayan.testfixtures.synapse.auth} package so that {@code @SpringBootApplication}
 * component scanning in unrelated {@code @SpringBootTest} contexts never picks them up.</p>
 *
 * <p>Covers:</p>
 * <ul>
 *   <li>enabled=false → {@code permitAll} (legacy): {@code /api/**} and {@code /mcp} reachable
 *       without any credential (Requirement 1.1), plus exactly one startup WARN (Requirements 1.1,
 *       1.6);</li>
 *   <li>enabled=true → public paths ({@code /actuator/health}, {@code /api/docs}) reachable without
 *       auth (Requirement 6.2);</li>
 *   <li>enabled=true → {@code 401} with the uniform fail-closed JSON body on a protected path with
 *       no credential (Requirement 6.3);</li>
 *   <li>enabled=true → {@code 403} with the uniform fail-closed JSON body when the principal lacks
 *       the required authority, and {@code 200} when it holds it (Requirements 6.4, 6.5).</li>
 * </ul>
 */
class SecurityConfigAuthorizationGatingTest {

    /** Authority required by the representative scope-gated route. */
    private static final String REQUIRED_SCOPE = "SCOPE_memory:write";

    /** Authority a valid-but-insufficient principal carries. */
    private static final String OTHER_SCOPE = "SCOPE_memory:read";

    /** Chain built from the production {@code SecurityConfig} with {@code spector.auth.enabled=true}. */
    private static MockMvc enabledMvc;

    /** Chain built from the production {@code SecurityConfig} with {@code spector.auth.enabled=false}. */
    private static MockMvc disabledMvc;

    @BeforeAll
    static void setUp() {
        enabledMvc = buildMockMvc(SecurityGatingChainConfig.AuthEnabled.class);
        disabledMvc = buildMockMvc(SecurityGatingChainConfig.AuthDisabled.class);
    }

    private static MockMvc buildMockMvc(Class<?> configClass) {
        AnnotationConfigWebApplicationContext ctx = new AnnotationConfigWebApplicationContext();
        ctx.register(configClass);
        ctx.setServletContext(new MockServletContext());
        ctx.refresh();
        return MockMvcBuilders.webAppContextSetup(ctx)
                .apply(springSecurity())
                .build();
    }

    // ── enabled=true : public-path bypass (Requirement 6.2) ──

    @Nested
    @DisplayName("auth enabled — public paths bypass authentication (Req 6.2)")
    class PublicPathBypass {

        @Test
        void actuatorHealthReachableWithoutCredential() throws Exception {
            enabledMvc.perform(get("/actuator/health"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("health"));
        }

        @Test
        void apiDocsReachableWithoutCredential() throws Exception {
            enabledMvc.perform(get("/api/docs"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("docs"));
        }
    }

    // ── enabled=true : 401 on missing credential (Requirement 6.3) ──

    @Nested
    @DisplayName("auth enabled — protected paths require a credential (Req 6.3)")
    class MissingCredential {

        @Test
        void apiProtectedWithoutCredentialYields401WithUniformBody() throws Exception {
            enabledMvc.perform(get("/api/protected"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentTypeCompatibleWith("application/json"))
                    .andExpect(jsonPath("$.error").value("Authentication required"));
        }

        @Test
        void mcpWithoutCredentialYields401WithUniformBody() throws Exception {
            enabledMvc.perform(get("/mcp"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("Authentication required"));
        }
    }

    // ── enabled=true : 403 on insufficient authority (Requirements 6.4, 6.5) ──

    @Nested
    @DisplayName("auth enabled — insufficient authority is forbidden (Req 6.4, 6.5)")
    class InsufficientAuthority {

        @Test
        void scopedRouteWithWrongScopeYields403WithUniformBody() throws Exception {
            enabledMvc.perform(get("/api/scoped").with(authentication(token(OTHER_SCOPE))))
                    .andExpect(status().isForbidden())
                    .andExpect(content().contentTypeCompatibleWith("application/json"))
                    .andExpect(jsonPath("$.error").value("Forbidden"));
        }

        @Test
        void scopedRouteWithRequiredScopeReachesHandler() throws Exception {
            enabledMvc.perform(get("/api/scoped").with(authentication(token(REQUIRED_SCOPE))))
                    .andExpect(status().isOk())
                    .andExpect(content().string("scoped"));
        }

        @Test
        void protectedRouteWithAnyValidCredentialReachesHandler() throws Exception {
            enabledMvc.perform(get("/api/protected").with(authentication(token(OTHER_SCOPE))))
                    .andExpect(status().isOk())
                    .andExpect(content().string("protected"));
        }
    }

    // ── enabled=false : permit-all legacy behavior (Requirement 1.1) ──

    @Nested
    @DisplayName("auth disabled — legacy permit-all (Req 1.1)")
    class LegacyPermitAll {

        @Test
        void apiProtectedReachableWithoutCredential() throws Exception {
            disabledMvc.perform(get("/api/protected"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("protected"));
        }

        @Test
        void mcpReachableWithoutCredential() throws Exception {
            disabledMvc.perform(get("/mcp"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("mcp"));
        }
    }

    // ── startup WARN (Requirements 1.1, 1.6) ──

    @Nested
    @DisplayName("startup WARN emitted only when auth disabled (Req 1.1, 1.6)")
    class StartupWarning {

        @Test
        void exactlyOneWarnWhenDisabledAndNoneWhenEnabled() {
            Logger secLogger = (Logger) LoggerFactory.getLogger(SecurityConfig.class);
            ListAppender<ILoggingEvent> appender = new ListAppender<>();
            appender.start();
            secLogger.addAppender(appender);
            try {
                // Disabled → exactly one WARN advising the server is unauthenticated.
                new SecurityConfig(propsWithAuth(false)).warnWhenUnauthenticated();

                List<ILoggingEvent> warnsAfterDisabled = appender.list.stream()
                        .filter(e -> e.getLevel() == Level.WARN)
                        .toList();
                assertThat(warnsAfterDisabled)
                        .as("exactly one startup WARN when auth is disabled (Req 1.6)")
                        .hasSize(1);
                assertThat(warnsAfterDisabled.getFirst().getFormattedMessage())
                        .contains("UNAUTHENTICATED")
                        .contains("spector.auth.enabled=false");

                // Enabled → emits nothing further (no WARN when the server is authenticated).
                new SecurityConfig(propsWithAuth(true)).warnWhenUnauthenticated();
                long warnCount = appender.list.stream()
                        .filter(e -> e.getLevel() == Level.WARN)
                        .count();
                assertThat(warnCount)
                        .as("enabled server emits no additional unauthenticated WARN (Req 1.1)")
                        .isEqualTo(1);
            } finally {
                secLogger.detachAppender(appender);
                appender.stop();
            }
        }
    }

    // ── helpers ──

    private static Authentication token(String authority) {
        return new UsernamePasswordAuthenticationToken(
                "user_00000001", "n/a", AuthorityUtils.createAuthorityList(authority));
    }

    private static SynapseProperties propsWithAuth(boolean enabled) {
        AuthProperties auth = new AuthProperties(enabled, null, null, null, null, null, null, null);
        return new SynapseProperties(7070, "shared-key", "./spector-data", null, null, null, auth);
    }
}
