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
import java.util.Map;
import java.util.Set;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrayan.spector.memory.id.TsidGenerator;
import com.spectrayan.spector.synapse.config.JwtDecoderConfig;
import com.spectrayan.spector.synapse.config.SecurityConfig;
import com.spectrayan.spector.synapse.config.SynapseProperties;
import com.spectrayan.spector.synapse.config.SynapseProperties.AuthProperties;
import com.spectrayan.spector.synapse.config.SynapseProperties.DefaultAdminProperties;
import com.spectrayan.spector.synapse.config.SynapseProperties.JwtProperties;
import com.spectrayan.spector.synapse.config.SynapseProperties.Pbkdf2Properties;
import com.spectrayan.spector.synapse.security.AuthDto.ChangePasswordRequest;
import com.spectrayan.spector.synapse.security.AuthDto.CreateApiKeyRequest;
import com.spectrayan.spector.synapse.security.AuthDto.CreateApiKeyResponse;
import com.spectrayan.spector.synapse.security.AuthDto.LoginRequest;
import com.spectrayan.spector.synapse.security.AuthDto.LoginResponse;
import com.spectrayan.spector.synapse.security.AuthDto.RefreshRequest;
import com.spectrayan.spector.synapse.security.AuthDto.RefreshResponse;
import com.spectrayan.spector.synapse.security.AuthDto.RegisterRequest;
import com.spectrayan.spector.synapse.security.AuthDto.RegisterResponse;
import com.spectrayan.spector.synapse.security.AuthDto.UpdateUserRequest;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Integration tests for the {@code /api/v1/auth} REST surface with authentication
 * <strong>ENABLED</strong> (Requirement 12 lifecycle, Requirement 2 login).
 *
 * <p>The whole <em>production</em> Spring Security composition is exercised end-to-end: the real
 * {@link SecurityConfig#filterChain} (with {@code spector.auth.enabled=true}), the extended
 * {@link ApiKeyAuthenticationFilter}, the OAuth2 Resource Server wired with the production
 * issuer-routing decoder from {@link JwtDecoderConfig}, method-security {@code @PreAuthorize} gating,
 * the real {@link AuthController}, and the JDBC-backed stores over an <strong>isolated in-memory
 * H2</strong> whose schema is created by the real Flyway migrations (V1..V3). The seeded default
 * {@code admin} account (Requirement 12.7) is created via {@link UserAccountStore#seedDefaultAdmin}
 * so the admin-gated operations can be authenticated with a real server-issued bearer token.</p>
 *
 * <h3>Why an explicitly-assembled context (not {@code @SpringBootTest})</h3>
 * <p>Sibling security tests in this package declare plain (non-{@code @TestConfiguration}) nested
 * {@code @Configuration}/{@code @RestController} classes that a full component scan would pick up,
 * colliding on bean names such as {@code filterChain}/{@code probeController}. To keep this test
 * self-contained and robust (and to avoid touching other files), the context is built directly from
 * an {@link AnnotationConfigWebApplicationContext} that registers exactly the production beans under
 * test — the same manual-assembly approach the existing {@code SecurityConfigAuthorizationGatingTest}
 * uses to drive the real {@code SecurityConfig} chain. {@link MockMvc} then runs every request
 * through the real {@code springSecurityFilterChain}.</p>
 *
 * <h3>Documented adaptations</h3>
 * <ul>
 *   <li><strong>Uniform-401 body.</strong> The login endpoint answers failures with the shared
 *       {@code ErrorResponse} record, which carries a per-response {@code timestamp}; the uniform,
 *       non-enumerating contract (Requirements 2.3, 2.6, 19.2) is therefore asserted on the
 *       {@code status}/{@code error}/{@code message} triple (byte-identical for bad-credential and
 *       locked-account failures), not on the timestamp.</li>
 *   <li><strong>Locked account.</strong> The account is locked deterministically via
 *       {@link UserAccountStore#recordFailure(String)} (the same store the lockout listener drives),
 *       then a login with the <em>correct</em> password is rejected with the uniform 401
 *       (Requirement 2.6) — avoiding any dependency on authentication-event delivery.</li>
 *   <li><strong>Logout blocklisting.</strong> This OSS module records the logged-out {@code jti} in
 *       {@code jti_blocklist} (Requirement 12.4) but wires no filter that re-checks the blocklist on
 *       later requests; the test asserts the observable controller contract — after {@code logout}
 *       the token's {@code jti} is present in the blocklist (verified via {@link JtiBlocklist}).</li>
 * </ul>
 */
@DisplayName("Auth REST surface — Integration (auth enabled)")
class AuthRestSurfaceIntegrationTest {

    private static final String AUTH = "/api/v1/auth";
    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_PASSWORD = "Admin!Test!Pw!123";
    private static final String UNIFORM_LOGIN_MESSAGE = "Invalid credentials";

    private static AnnotationConfigWebApplicationContext ctx;
    private static MockMvc mvc;
    private static ObjectMapper mapper;
    private static UserAccountStore userAccountStore;
    private static RefreshTokenStore refreshTokenStore;
    private static JtiBlocklist jtiBlocklist;
    private static JwtDecoder serverJwtDecoder;

    @BeforeAll
    static void setUp() {
        ctx = new AnnotationConfigWebApplicationContext();
        // JwtDecoderConfig's beans are @ConditionalOnProperty(spector.auth.enabled=true); make that
        // property visible to the condition evaluator in this hand-built context.
        ctx.getEnvironment().getPropertySources().addFirst(new MapPropertySource(
                "auth-rest-test", Map.of("spector.auth.enabled", "true")));
        ctx.register(AuthTestConfig.class, JwtDecoderConfig.class);
        ctx.setServletContext(new MockServletContext());
        ctx.refresh();

        mvc = MockMvcBuilders.webAppContextSetup(ctx).apply(springSecurity()).build();
        mapper = new ObjectMapper();
        userAccountStore = ctx.getBean(UserAccountStore.class);
        refreshTokenStore = ctx.getBean(RefreshTokenStore.class);
        jtiBlocklist = ctx.getBean(JtiBlocklist.class);
        serverJwtDecoder = ctx.getBean(JwtDecoder.class);

        // Seed the default admin exactly as AuthStartupInitializer does on ApplicationReadyEvent.
        userAccountStore.seedDefaultAdmin(ADMIN_PASSWORD);
    }

    @AfterAll
    static void tearDown() {
        if (ctx != null) {
            ctx.close();
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Login (Requirements 2.1, 2.3, 2.6, 2.7)
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("POST /login")
    class Login {

        @Test
        @DisplayName("valid credentials → 200 with access + refresh tokens (Req 2.1)")
        void validCredentials_returnsAccessAndRefreshTokens() throws Exception {
            MvcResult res = mvc.perform(post(AUTH + "/login")
                            .contentType(APPLICATION_JSON)
                            .content(json(new LoginRequest(ADMIN_USERNAME, ADMIN_PASSWORD))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.access_token").isNotEmpty())
                    .andExpect(jsonPath("$.refresh_token").isNotEmpty())
                    .andExpect(jsonPath("$.token_type").value("Bearer"))
                    .andExpect(jsonPath("$.expires_in").value(3600))
                    .andReturn();

            LoginResponse body = read(res, LoginResponse.class);
            // The access token validates against the production server decoder (sub == admin TSID).
            Jwt decoded = serverJwtDecoder.decode(body.accessToken());
            assertThat(decoded.getSubject()).isEqualTo(adminUserId());
        }

        @Test
        @DisplayName("bad and locked credentials → identical uniform 401 body (Reqs 2.3, 2.6, 19.2)")
        void badAndLockedCredentials_shareUniform401() throws Exception {
            // Bad credentials for an existing, non-locked account.
            registerUser(adminAccessToken(), "login-bad", "OrigPass123", null);
            MvcResult bad = mvc.perform(post(AUTH + "/login")
                            .contentType(APPLICATION_JSON)
                            .content(json(new LoginRequest("login-bad", "WrongPass999"))))
                    .andExpect(status().isUnauthorized())
                    .andReturn();

            // A locked account rejected even though the submitted password is correct (Req 2.6).
            String lockedUserId = registerUser(adminAccessToken(), "login-locked", "OrigPass123", null);
            for (int i = 0; i < 5; i++) {          // lockout.max-attempts == 5
                userAccountStore.recordFailure(lockedUserId);
            }
            MvcResult locked = mvc.perform(post(AUTH + "/login")
                            .contentType(APPLICATION_JSON)
                            .content(json(new LoginRequest("login-locked", "OrigPass123"))))
                    .andExpect(status().isUnauthorized())
                    .andReturn();

            JsonNode badBody = tree(bad);
            JsonNode lockedBody = tree(locked);
            // Byte-identical on the enumeration-relevant triple (timestamp differs by design).
            assertThat(lockedBody.get("status").asInt()).isEqualTo(badBody.get("status").asInt()).isEqualTo(401);
            assertThat(lockedBody.get("error").asText()).isEqualTo(badBody.get("error").asText());
            assertThat(lockedBody.get("message").asText())
                    .isEqualTo(badBody.get("message").asText())
                    .isEqualTo(UNIFORM_LOGIN_MESSAGE);
        }

        @Test
        @DisplayName("missing password field → 400 without credential verification (Req 2.7)")
        void missingField_returns400() throws Exception {
            mvc.perform(post(AUTH + "/login")
                            .contentType(APPLICATION_JSON)
                            .content("{\"username\":\"someone\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Refresh (Requirements 12.3, 12.9)
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("POST /refresh")
    class Refresh {

        @Test
        @DisplayName("valid refresh token → 200 with a new access token (Req 12.3)")
        void validToken_returnsNewAccessToken() throws Exception {
            LoginResponse login = loginOk(ADMIN_USERNAME, ADMIN_PASSWORD);

            MvcResult res = mvc.perform(post(AUTH + "/refresh")
                            .contentType(APPLICATION_JSON)
                            .content(json(new RefreshRequest(login.refreshToken()))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.access_token").isNotEmpty())
                    .andExpect(jsonPath("$.token_type").value("Bearer"))
                    .andReturn();

            RefreshResponse body = read(res, RefreshResponse.class);
            assertThat(serverJwtDecoder.decode(body.accessToken()).getSubject()).isEqualTo(adminUserId());
        }

        @Test
        @DisplayName("unknown refresh token → 401 (Req 12.9)")
        void unknownToken_returns401() throws Exception {
            mvc.perform(post(AUTH + "/refresh")
                            .contentType(APPLICATION_JSON)
                            .content(json(new RefreshRequest("not-a-real-refresh-token"))))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("revoked refresh token → 401 (Req 12.9)")
        void revokedToken_returns401() throws Exception {
            LoginResponse login = loginOk(ADMIN_USERNAME, ADMIN_PASSWORD);
            // Revoke by the token's hash (only the hash is ever persisted).
            assertThat(refreshTokenStore.revokeByHash(RefreshTokenStore.sha256Hex(login.refreshToken())))
                    .isTrue();

            mvc.perform(post(AUTH + "/refresh")
                            .contentType(APPLICATION_JSON)
                            .content(json(new RefreshRequest(login.refreshToken()))))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("expired refresh token → 401 (Req 12.9)")
        void expiredToken_returns401() throws Exception {
            // Seed a refresh token that is already expired; findActive's expiry filter rejects it.
            String expiredRaw = refreshTokenStore.create(adminUserId(), Instant.now().minusSeconds(60));

            mvc.perform(post(AUTH + "/refresh")
                            .contentType(APPLICATION_JSON)
                            .content(json(new RefreshRequest(expiredRaw))))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Logout (Requirement 12.4)
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("POST /logout → 200 and the token jti is added to the blocklist (Req 12.4)")
    void logout_blocklistsJti() throws Exception {
        String access = adminAccessToken();
        String jti = serverJwtDecoder.decode(access).getId();
        assertThat(jti).isNotBlank();
        assertThat(jtiBlocklist.isBlocked(jti)).isFalse();

        mvc.perform(post(AUTH + "/logout").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk());

        // Observable contract of Req 12.4: the jti is now blocklisted so it can be rejected on reuse.
        assertThat(jtiBlocklist.isBlocked(jti)).isTrue();
    }

    // ══════════════════════════════════════════════════════════════
    // Register (Requirements 12.1, 12.10, 12.12)
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("POST /register")
    class Register {

        @Test
        @DisplayName("without ADMIN role → 403 (Req 12.10)")
        void withoutAdmin_returns403() throws Exception {
            registerUser(adminAccessToken(), "reg-normal", "OrigPass123", Set.of("USER"));
            String userToken = loginOk("reg-normal", "OrigPass123").accessToken();

            mvc.perform(post(AUTH + "/register")
                            .header("Authorization", "Bearer " + userToken)
                            .contentType(APPLICATION_JSON)
                            .content(json(new RegisterRequest("reg-denied", "OrigPass123",
                                    null, null, null, null, null))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("with ADMIN role → 201 and a 13-char TSID user_id (Req 12.1)")
        void withAdmin_returns201() throws Exception {
            MvcResult res = mvc.perform(post(AUTH + "/register")
                            .header("Authorization", "Bearer " + adminAccessToken())
                            .contentType(APPLICATION_JSON)
                            .content(json(new RegisterRequest("reg-ok", "OrigPass123",
                                    null, null, null, null, null))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.user_id").isNotEmpty())
                    .andReturn();

            assertThat(read(res, RegisterResponse.class).userId()).hasSize(13);
        }

        @Test
        @DisplayName("duplicate username → 409 (Req 12.12)")
        void duplicateUsername_returns409() throws Exception {
            String token = adminAccessToken();
            registerUser(token, "reg-dup", "OrigPass123", null);

            mvc.perform(post(AUTH + "/register")
                            .header("Authorization", "Bearer " + token)
                            .contentType(APPLICATION_JSON)
                            .content(json(new RegisterRequest("reg-dup", "OrigPass123",
                                    null, null, null, null, null))))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("password length violation → 400 (Req 12.12)")
        void lengthViolation_returns400() throws Exception {
            mvc.perform(post(AUTH + "/register")
                            .header("Authorization", "Bearer " + adminAccessToken())
                            .contentType(APPLICATION_JSON)
                            .content(json(new RegisterRequest("reg-short", "short",
                                    null, null, null, null, null))))
                    .andExpect(status().isBadRequest());
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Change password (Requirements 12.2, 12.8)
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("POST /change-password")
    class ChangePassword {

        @Test
        @DisplayName("wrong current password → 401 and the hash is unchanged (Req 12.8)")
        void wrongCurrent_returns401() throws Exception {
            registerUser(adminAccessToken(), "cp-wrong", "OrigPass123", null);
            String token = loginOk("cp-wrong", "OrigPass123").accessToken();

            mvc.perform(post(AUTH + "/change-password")
                            .header("Authorization", "Bearer " + token)
                            .contentType(APPLICATION_JSON)
                            .content(json(new ChangePasswordRequest("WrongCurrent1", "BrandNewPass1"))))
                    .andExpect(status().isUnauthorized());

            // The original password still works — the hash was not changed.
            loginOk("cp-wrong", "OrigPass123");
        }

        @Test
        @DisplayName("correct current password → 200 and the new password authenticates (Req 12.2)")
        void success_returns200() throws Exception {
            registerUser(adminAccessToken(), "cp-ok", "OrigPass123", null);
            String token = loginOk("cp-ok", "OrigPass123").accessToken();

            mvc.perform(post(AUTH + "/change-password")
                            .header("Authorization", "Bearer " + token)
                            .contentType(APPLICATION_JSON)
                            .content(json(new ChangePasswordRequest("OrigPass123", "BrandNewPass1"))))
                    .andExpect(status().isOk());

            // The new password now authenticates.
            loginOk("cp-ok", "BrandNewPass1");
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Admin users (Requirements 12.5, 12.10, 12.11)
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("/users (ADMIN only)")
    class AdminUsers {

        @Test
        @DisplayName("GET /users as admin → 200 list that never carries password hashes (Req 12.5)")
        void listUsers_asAdmin_omitsPasswordHashes() throws Exception {
            registerUser(adminAccessToken(), "list-target", "OrigPass123", null);

            MvcResult res = mvc.perform(get(AUTH + "/users")
                            .header("Authorization", "Bearer " + adminAccessToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andReturn();

            String bodyText = res.getResponse().getContentAsString();
            // No password-hash field or {pbkdf2} value ever leaves the store (Req 14.1).
            assertThat(bodyText).doesNotContain("password_hash").doesNotContain("{pbkdf2}");

            JsonNode arr = mapper.readTree(bodyText);
            assertThat(arr.isArray()).isTrue();
            assertThat(arr).anySatisfy(node ->
                    assertThat(node.get("username").asText()).isEqualTo(ADMIN_USERNAME));
        }

        @Test
        @DisplayName("GET /users without ADMIN role → 403 (Req 12.10)")
        void listUsers_withoutAdmin_returns403() throws Exception {
            registerUser(adminAccessToken(), "list-normal", "OrigPass123", Set.of("USER"));
            String userToken = loginOk("list-normal", "OrigPass123").accessToken();

            mvc.perform(get(AUTH + "/users").header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("GET /users unauthenticated → 401 (Req 12.10)")
        void listUsers_unauthenticated_returns401() throws Exception {
            mvc.perform(get(AUTH + "/users"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("PUT /users/{id} as admin → 200 with the applied update (Req 12.11)")
        void updateUser_asAdmin_appliesUpdate() throws Exception {
            String targetId = registerUser(adminAccessToken(), "upd-target", "OrigPass123", Set.of("USER"));

            mvc.perform(put(AUTH + "/users/" + targetId)
                            .header("Authorization", "Bearer " + adminAccessToken())
                            .contentType(APPLICATION_JSON)
                            .content(json(new UpdateUserRequest(false, Set.of("USER", "ADMIN"),
                                    null, "Updated Name"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.user_id").value(targetId))
                    .andExpect(jsonPath("$.active").value(false))
                    .andExpect(jsonPath("$.display_name").value("Updated Name"));
        }

        @Test
        @DisplayName("PUT /users/{id} for a missing user → 404 (Req 12.11)")
        void updateUser_missing_returns404() throws Exception {
            mvc.perform(put(AUTH + "/users/MISSING0000AB")
                            .header("Authorization", "Bearer " + adminAccessToken())
                            .contentType(APPLICATION_JSON)
                            .content(json(new UpdateUserRequest(false, null, null, null))))
                    .andExpect(status().isNotFound());
        }
    }

    // ══════════════════════════════════════════════════════════════
    // API keys (Requirement 12.6)
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("/api-keys")
    class ApiKeys {

        @Test
        @DisplayName("POST /api-keys returns the raw key once and that key authenticates a later request (Req 12.6)")
        void createApiKey_returnsRawKeyOnce_andAuthenticatesSubsequentRequest() throws Exception {
            MvcResult res = mvc.perform(post(AUTH + "/api-keys")
                            .header("Authorization", "Bearer " + adminAccessToken())
                            .contentType(APPLICATION_JSON)
                            .content(json(new CreateApiKeyRequest(Set.of("memory:read"), null))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.key_id").isNotEmpty())
                    .andExpect(jsonPath("$.api_key").isNotEmpty())
                    .andReturn();

            CreateApiKeyResponse created = read(res, CreateApiKeyResponse.class);

            // The raw key authenticates a subsequent request via X-API-Key (endpoint needs only auth).
            mvc.perform(post(AUTH + "/api-keys")
                            .header("X-API-Key", created.apiKey())
                            .contentType(APPLICATION_JSON)
                            .content(json(new CreateApiKeyRequest(Set.of("memory:read"), null))))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("DELETE /api-keys/{id} revokes the key so it no longer authenticates (Req 12.6)")
        void deleteApiKey_revokes_thenKeyNoLongerAuthenticates() throws Exception {
            String adminToken = adminAccessToken();
            CreateApiKeyResponse created = read(mvc.perform(post(AUTH + "/api-keys")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(APPLICATION_JSON)
                            .content(json(new CreateApiKeyRequest(Set.of("memory:read"), null))))
                    .andExpect(status().isCreated())
                    .andReturn(), CreateApiKeyResponse.class);

            // Sanity: the freshly issued key authenticates.
            mvc.perform(post(AUTH + "/api-keys")
                            .header("X-API-Key", created.apiKey())
                            .contentType(APPLICATION_JSON)
                            .content(json(new CreateApiKeyRequest(Set.of("memory:read"), null))))
                    .andExpect(status().isCreated());

            // Revoke it.
            mvc.perform(delete(AUTH + "/api-keys/" + created.keyId())
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isNoContent());

            // The revoked key no longer authenticates → the protected endpoint returns 401.
            mvc.perform(post(AUTH + "/api-keys")
                            .header("X-API-Key", created.apiKey())
                            .contentType(APPLICATION_JSON)
                            .content(json(new CreateApiKeyRequest(Set.of("memory:read"), null))))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Helpers
    // ══════════════════════════════════════════════════════════════

    private String adminUserId() {
        return userAccountStore.findByUsername(ADMIN_USERNAME).orElseThrow().userId();
    }

    private String adminAccessToken() throws Exception {
        return loginOk(ADMIN_USERNAME, ADMIN_PASSWORD).accessToken();
    }

    private LoginResponse loginOk(String username, String password) throws Exception {
        MvcResult res = mvc.perform(post(AUTH + "/login")
                        .contentType(APPLICATION_JSON)
                        .content(json(new LoginRequest(username, password))))
                .andExpect(status().isOk())
                .andReturn();
        return read(res, LoginResponse.class);
    }

    /** Registers a user via the admin-gated endpoint and returns the generated TSID user_id. */
    private String registerUser(String adminToken, String username, String password, Set<String> roles)
            throws Exception {
        MvcResult res = mvc.perform(post(AUTH + "/register")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(APPLICATION_JSON)
                        .content(json(new RegisterRequest(username, password, null, null, roles, null, null))))
                .andExpect(status().isCreated())
                .andReturn();
        return read(res, RegisterResponse.class).userId();
    }

    private String json(Object value) throws Exception {
        return mapper.writeValueAsString(value);
    }

    private <T> T read(MvcResult res, Class<T> type) throws Exception {
        return mapper.readValue(res.getResponse().getContentAsString(), type);
    }

    private JsonNode tree(MvcResult res) throws Exception {
        return mapper.readTree(res.getResponse().getContentAsString());
    }

    // ══════════════════════════════════════════════════════════════
    // Explicit production-bean assembly (no component scan)
    // ══════════════════════════════════════════════════════════════

    /**
     * Registers the exact production beans the auth REST surface needs, over an isolated in-memory
     * H2 whose schema is migrated by the real Flyway scripts. Mirrors the wiring in
     * {@code SecurityConfig}/{@code JwtDecoderConfig} so the tests exercise the real filter chain,
     * controller, and JDBC stores without a full component scan.
     */
    @Configuration
    @EnableWebSecurity
    @EnableMethodSecurity
    @EnableWebMvc
    static class AuthTestConfig {

        private static final int PBKDF2_SALT_LENGTH = 16;
        private static final int PBKDF2_ITERATIONS = 1000;   // cheap; validator only requires >= 1
        private static final String JWT_SECRET =
                "integration-auth-rest-secret-key-at-least-32-bytes-long-0123456789";

        @Bean
        SynapseProperties synapseProperties() {
            // Only the credential-acquisition endpoints are public; every other lifecycle endpoint
            // (logout/register/change-password/users/api-keys) still requires authentication. This
            // mirrors a real auth-enabled deployment, which must expose login/refresh via
            // spector.auth.public-paths so a client can obtain its first token.
            AuthProperties auth = new AuthProperties(
                    true,
                    new JwtProperties(JWT_SECRET, null),
                    null,
                    null,
                    new DefaultAdminProperties(ADMIN_PASSWORD),
                    new Pbkdf2Properties(PBKDF2_ITERATIONS),
                    null,
                    java.util.List.of("/actuator/health", "/api/docs",
                            "/api/v1/auth/login", "/api/v1/auth/refresh"));
            return new SynapseProperties(0, null, "./target/spector-authrest-test", null, null, null, auth);
        }

        @Bean
        DataSource dataSource() {
            DriverManagerDataSource ds = new DriverManagerDataSource();
            ds.setDriverClassName("org.h2.Driver");
            ds.setUrl("jdbc:h2:mem:authrest_task_13_4;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
            ds.setUsername("sa");
            ds.setPassword("");
            return ds;
        }

        @Bean(initMethod = "migrate")
        Flyway flyway(DataSource dataSource) {
            return Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration")
                    .load();
        }

        @Bean
        JdbcClient jdbcClient(DataSource dataSource) {
            return JdbcClient.create(dataSource);
        }

        @Bean
        TsidGenerator tsidGenerator() {
            return new TsidGenerator();
        }

        @Bean
        Pbkdf2PasswordEncoder passwordEncoder() {
            return new Pbkdf2PasswordEncoder("", PBKDF2_SALT_LENGTH, PBKDF2_ITERATIONS,
                    Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256);
        }

        @Bean
        UserAccountStore userAccountStore(JdbcClient jdbc, Pbkdf2PasswordEncoder encoder,
                                          SynapseProperties props) {
            return new UserAccountStore(jdbc, encoder, props);
        }

        @Bean
        JdbcUserDetailsService jdbcUserDetailsService(UserAccountStore accountStore) {
            return new JdbcUserDetailsService(accountStore);
        }

        @Bean
        DaoAuthenticationProvider daoAuthenticationProvider(UserDetailsService uds,
                                                            Pbkdf2PasswordEncoder encoder) {
            DaoAuthenticationProvider provider = new DaoAuthenticationProvider(uds);
            provider.setPasswordEncoder(encoder);
            return provider;
        }

        @Bean
        AuthenticationManager authenticationManager(DaoAuthenticationProvider provider) {
            return new ProviderManager(provider);
        }

        @Bean
        ApiKeyStore apiKeyStore(JdbcClient jdbc) {
            return new ApiKeyStore(jdbc);
        }

        @Bean
        RefreshTokenStore refreshTokenStore(JdbcClient jdbc, TsidGenerator tsid) {
            return new RefreshTokenStore(jdbc, tsid);
        }

        @Bean
        JtiBlocklist jtiBlocklist(JdbcClient jdbc) {
            return new JtiBlocklist(jdbc);
        }

        @Bean
        ServerAccessTokenMinter serverAccessTokenMinter(SynapseProperties props) {
            return new ServerAccessTokenMinter(props);
        }

        @Bean
        ApiKeyAuthenticationFilter apiKeyFilter(SynapseProperties props, ApiKeyStore apiKeyStore) {
            return new ApiKeyAuthenticationFilter(props, apiKeyStore);
        }

        /**
         * Exposes {@code spector.auth.*} as an {@link AuthProperties} bean so the registered
         * {@link JwtDecoderConfig} can build the production server HS256 decoder and issuer-routing
         * resolver (its {@code @Bean} methods take an {@code AuthProperties} argument).
         */
        @Bean
        AuthProperties authProperties(SynapseProperties props) {
            return props.auth();
        }

        @Bean
        SecurityFilterChain filterChain(
                HttpSecurity http,
                ApiKeyAuthenticationFilter apiKeyFilter,
                SynapseProperties props,
                ObjectProvider<AuthenticationManagerResolver<HttpServletRequest>> jwtResolverProvider)
                throws Exception {
            // Drive the PRODUCTION security composition root directly (auth enabled).
            return new SecurityConfig(props).filterChain(http, apiKeyFilter, props, jwtResolverProvider);
        }

        @Bean
        AuthController authController(AuthenticationManager authenticationManager,
                                     ServerAccessTokenMinter tokenMinter,
                                     RefreshTokenStore refreshTokenStore,
                                     JtiBlocklist jtiBlocklist,
                                     UserAccountStore userAccountStore,
                                     ApiKeyStore apiKeyStore,
                                     SynapseProperties props) {
            return new AuthController(authenticationManager, tokenMinter, refreshTokenStore,
                    jtiBlocklist, userAccountStore, apiKeyStore, props);
        }

        @Bean
        AuthExceptionHandler authExceptionHandler() {
            return new AuthExceptionHandler();
        }
    }
}
