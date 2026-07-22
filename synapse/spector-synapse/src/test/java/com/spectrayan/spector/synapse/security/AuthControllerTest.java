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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import com.spectrayan.spector.synapse.config.SynapseProperties;
import com.spectrayan.spector.synapse.config.SynapseProperties.AuthProperties;
import com.spectrayan.spector.synapse.config.SynapseProperties.RefreshProperties;
import com.spectrayan.spector.synapse.memory.MemoryDto.ErrorResponse;
import com.spectrayan.spector.synapse.security.ApiKeyStore.ApiKeyCreation;
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
import com.spectrayan.spector.synapse.security.AuthDto.UserSummary;
import com.spectrayan.spector.synapse.security.ServerAccessTokenMinter.MintedAccessToken;

/**
 * Unit tests for {@link AuthController} with mocked collaborators, covering the login/refresh/logout
 * behaviours in Requirements 2.1–2.4, 2.6, 2.7, 12.3, 12.4, and 19.2.
 */
class AuthControllerTest {

    private static final String USER_ID = "USER0000000AB";

    private AuthenticationManager authenticationManager;
    private ServerAccessTokenMinter tokenMinter;
    private RefreshTokenStore refreshTokenStore;
    private JtiBlocklist jtiBlocklist;
    private UserAccountStore userAccountStore;
    private ApiKeyStore apiKeyStore;
    private AuthController controller;

    @BeforeEach
    void setUp() {
        authenticationManager = mock(AuthenticationManager.class);
        tokenMinter = mock(ServerAccessTokenMinter.class);
        refreshTokenStore = mock(RefreshTokenStore.class);
        jtiBlocklist = mock(JtiBlocklist.class);
        userAccountStore = mock(UserAccountStore.class);
        apiKeyStore = mock(ApiKeyStore.class);

        AuthProperties auth = new AuthProperties(
                true, null, new RefreshProperties(Duration.ofDays(30)), null, null, null, null, null);
        SynapseProperties props = new SynapseProperties(0, null, null, null, null, null, auth);

        controller = new AuthController(authenticationManager, tokenMinter, refreshTokenStore,
                jtiBlocklist, userAccountStore, apiKeyStore, props);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void bindPrincipal(String userId) {
        Authentication authenticated = new UsernamePasswordAuthenticationToken(userId, null, List.of());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authenticated);
        SecurityContextHolder.setContext(context);
    }

    private MintedAccessToken sampleAccess() {
        return new MintedAccessToken("access.jwt.token", "jti-123",
                Instant.now().plusSeconds(3600), 3600L);
    }

    // ── login ──

    @Test
    void loginMissingUsernameReturns400AndSkipsVerification() {
        ResponseEntity<?> response = controller.login(new LoginRequest(" ", "pw"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(((ErrorResponse) response.getBody()).status()).isEqualTo(400);
        verifyNoInteractions(authenticationManager);
    }

    @Test
    void loginMissingPasswordReturns400AndSkipsVerification() {
        ResponseEntity<?> response = controller.login(new LoginRequest("alice", null));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(authenticationManager);
    }

    @Test
    void loginNullBodyReturns400() {
        ResponseEntity<?> response = controller.login(null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void loginBadCredentialsReturnsUniform401AndIssuesNoTokens() {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("bad"));

        ResponseEntity<?> response = controller.login(new LoginRequest("alice", "wrong"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(((ErrorResponse) response.getBody()).message()).isEqualTo("Invalid credentials");
        verify(refreshTokenStore, never()).create(anyString(), any());
    }

    @Test
    void loginLockedAccountReturnsSameUniform401AsBadCredentials() {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new LockedException("locked"));

        ResponseEntity<?> response = controller.login(new LoginRequest("alice", "correct"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        // Byte-identical body to the bad-credentials case: no account enumeration (Req 19.2).
        assertThat(((ErrorResponse) response.getBody()).message()).isEqualTo("Invalid credentials");
    }

    @Test
    void loginSuccessReturnsAccessAndRefreshTokens() {
        Authentication authenticated = new UsernamePasswordAuthenticationToken(
                USER_ID, null, List.of(new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("SCOPE_memory:read")));
        when(authenticationManager.authenticate(any())).thenReturn(authenticated);
        when(tokenMinter.mintFromAuthorities(eq(USER_ID), any())).thenReturn(sampleAccess());
        when(refreshTokenStore.create(eq(USER_ID), any())).thenReturn("raw-refresh-token");

        ResponseEntity<?> response = controller.login(new LoginRequest("alice", "correct"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        LoginResponse body = (LoginResponse) response.getBody();
        assertThat(body.accessToken()).isEqualTo("access.jwt.token");
        assertThat(body.tokenType()).isEqualTo("Bearer");
        assertThat(body.expiresIn()).isEqualTo(3600L);
        assertThat(body.refreshToken()).isEqualTo("raw-refresh-token");
        assertThat(body.refreshExpiresIn()).isEqualTo(Duration.ofDays(30).toSeconds());
        verify(refreshTokenStore).create(eq(USER_ID), any());
    }

    // ── refresh ──

    @Test
    void refreshMissingTokenReturns400() {
        ResponseEntity<?> response = controller.refresh(new RefreshRequest("  "));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(refreshTokenStore);
    }

    @Test
    void refreshInactiveTokenReturns401() {
        when(refreshTokenStore.findActive(anyString())).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.refresh(new RefreshRequest("raw"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(tokenMinter, never()).mint(anyString(), any(), any());
    }

    @Test
    void refreshForVanishedAccountReturns401() {
        when(refreshTokenStore.findActive(anyString()))
                .thenReturn(Optional.of(new RefreshTokenStore.RefreshTokenRow("tok", USER_ID, Instant.now())));
        when(userAccountStore.findByUserId(USER_ID)).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.refresh(new RefreshRequest("raw"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(tokenMinter, never()).mint(anyString(), any(), any());
    }

    @Test
    void refreshActiveTokenIssuesNewAccessToken() {
        when(refreshTokenStore.findActive(anyString()))
                .thenReturn(Optional.of(new RefreshTokenStore.RefreshTokenRow("tok", USER_ID, Instant.now())));
        when(userAccountStore.findByUserId(USER_ID)).thenReturn(Optional.of(activeUser()));
        when(tokenMinter.mint(eq(USER_ID), any(), any())).thenReturn(sampleAccess());

        ResponseEntity<?> response = controller.refresh(new RefreshRequest("raw"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        RefreshResponse body = (RefreshResponse) response.getBody();
        assertThat(body.accessToken()).isEqualTo("access.jwt.token");
        assertThat(body.tokenType()).isEqualTo("Bearer");
        assertThat(body.expiresIn()).isEqualTo(3600L);
    }

    // ── logout ──

    @Test
    void logoutBlocklistsJtiFromJwtPrincipal() {
        Instant exp = Instant.now().plusSeconds(3600);
        Jwt jwt = mock(Jwt.class);
        when(jwt.getId()).thenReturn("jti-abc");
        when(jwt.getExpiresAt()).thenReturn(exp);

        ResponseEntity<Void> response = controller.logout(jwt);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(jtiBlocklist).add("jti-abc", exp);
    }

    @Test
    void logoutWithoutJwtIsNoOp() {
        ResponseEntity<Void> response = controller.logout(null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verifyNoInteractions(jtiBlocklist);
    }

    // ── register ──

    @Test
    void registerNullBodyReturns400() {
        ResponseEntity<?> response = controller.register(null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(userAccountStore);
    }

    @Test
    void registerTooLongUsernameReturns400() {
        String longName = "u".repeat(65);
        ResponseEntity<?> response = controller.register(
                new RegisterRequest(longName, "password1", null, null, null, null, null));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(userAccountStore, never()).createUser(anyString(), anyString(), any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void registerShortPasswordReturns400() {
        ResponseEntity<?> response = controller.register(
                new RegisterRequest("bob", "short", null, null, null, null, null));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(userAccountStore, never()).createUser(anyString(), anyString(), any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void registerDuplicateUsernameReturns409() {
        when(userAccountStore.createUser(eq("bob"), anyString(), any(), any(), any(), any(), anyBoolean()))
                .thenThrow(new DuplicateUsernameException("bob"));
        ResponseEntity<?> response = controller.register(
                new RegisterRequest("bob", "password1", null, null, null, null, null));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void registerSuccessReturns201AndUserId() {
        when(userAccountStore.createUser(eq("bob"), eq("password1"), any(), any(), any(), any(), anyBoolean()))
                .thenReturn("USER0000000CD");
        ResponseEntity<?> response = controller.register(
                new RegisterRequest("bob", "password1", null, null, null, null, null));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(((RegisterResponse) response.getBody()).userId()).isEqualTo("USER0000000CD");
    }

    // ── change-password ──

    @Test
    void changePasswordMissingCurrentReturns400() {
        ResponseEntity<?> response = controller.changePassword(new ChangePasswordRequest(" ", "newpassword1"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void changePasswordShortNewReturns400() {
        ResponseEntity<?> response = controller.changePassword(new ChangePasswordRequest("current1", "short"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void changePasswordWrongCurrentReturns401AndDoesNotUpdate() {
        bindPrincipal(USER_ID);
        when(userAccountStore.findByUserId(USER_ID)).thenReturn(Optional.of(activeUser()));
        when(userAccountStore.changePassword("alice", "wrong", "newpassword1")).thenReturn(false);

        ResponseEntity<?> response = controller.changePassword(
                new ChangePasswordRequest("wrong", "newpassword1"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void changePasswordSuccessReturns200() {
        bindPrincipal(USER_ID);
        when(userAccountStore.findByUserId(USER_ID)).thenReturn(Optional.of(activeUser()));
        when(userAccountStore.changePassword("alice", "current1", "newpassword1")).thenReturn(true);

        ResponseEntity<?> response = controller.changePassword(
                new ChangePasswordRequest("current1", "newpassword1"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(userAccountStore).changePassword("alice", "current1", "newpassword1");
    }

    // ── users (admin) ──

    @Test
    void listUsersReturnsSummariesWithoutPasswordHash() {
        when(userAccountStore.listUsers()).thenReturn(List.of(activeUser()));

        ResponseEntity<List<UserSummary>> response = controller.listUsers();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        UserSummary summary = response.getBody().getFirst();
        assertThat(summary.userId()).isEqualTo(USER_ID);
        assertThat(summary.username()).isEqualTo("alice");
        // UserSummary intentionally has no password-hash accessor.
    }

    @Test
    void updateUserMissingTargetReturns404() {
        when(userAccountStore.updateAccount(eq("MISSING"), any(), any(), any(), any()))
                .thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.updateUser("MISSING",
                new UpdateUserRequest(false, null, null, null));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void updateUserAppliesUpdateAndReturnsAccount() {
        when(userAccountStore.updateAccount(eq(USER_ID), eq(false), any(), any(), any()))
                .thenReturn(Optional.of(activeUser()));

        ResponseEntity<?> response = controller.updateUser(USER_ID,
                new UpdateUserRequest(false, null, null, null));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((UserSummary) response.getBody()).userId()).isEqualTo(USER_ID);
    }

    // ── api-keys ──

    @Test
    void createApiKeyReturnsRawKeyOnceForCurrentUser() {
        bindPrincipal(USER_ID);
        when(apiKeyStore.create(eq(USER_ID), any(), any()))
                .thenReturn(new ApiKeyCreation("KEY0000000001", "raw-api-key-value"));

        ResponseEntity<CreateApiKeyResponse> response = controller.createApiKey(
                new CreateApiKeyRequest(Set.of("memory:read"), null));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().keyId()).isEqualTo("KEY0000000001");
        assertThat(response.getBody().apiKey()).isEqualTo("raw-api-key-value");
        verify(apiKeyStore).create(eq(USER_ID), any(), any());
    }

    @Test
    void revokeApiKeyMissingReturns404() {
        when(apiKeyStore.revoke("MISSING")).thenReturn(false);

        ResponseEntity<?> response = controller.revokeApiKey("MISSING");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void revokeApiKeySuccessReturns204() {
        when(apiKeyStore.revoke("KEY0000000001")).thenReturn(true);

        ResponseEntity<?> response = controller.revokeApiKey("KEY0000000001");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    private static UserRow activeUser() {
        Instant now = Instant.now();
        return new UserRow(USER_ID, "alice", "{pbkdf2}hash", null, null,
                Set.of("USER"), Set.of("memory:read"),
                false, true, 0, null, null, now, now);
    }
}
