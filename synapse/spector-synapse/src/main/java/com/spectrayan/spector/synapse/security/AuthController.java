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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.spectrayan.spector.synapse.config.SynapseProperties;
import com.spectrayan.spector.synapse.config.SynapseProperties.AuthProperties;
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
 * REST controller for the identity lifecycle at {@code /api/v1/auth} (Requirement 12).
 *
 * <p>This slice implements the session endpoints — {@code POST /login}, {@code POST /refresh}, and
 * {@code POST /logout} (Requirements 2.1–2.4, 2.6, 2.7, 12.3, 12.4, 19.2). Account and API-key
 * management ({@code register}, {@code change-password}, {@code users}, {@code api-keys}) are added
 * to this same controller by a later task; the shared collaborators are injected here so those
 * endpoints can be layered in without restructuring.</p>
 *
 * <p><strong>Login</strong> delegates credential verification to the {@link AuthenticationManager}
 * (a {@code DaoAuthenticationProvider} over the JDBC user store + PBKDF2 encoder). On success it
 * mints an HS256 access token whose {@code sub} is the User_Id (never the username) with the
 * User's scope/role claims, and a refresh token whose only-stored form is a SHA-256 hash. Every
 * failure — bad password, unknown username, locked, or disabled account — yields a byte-identical
 * HTTP 401 so the response cannot be used to enumerate accounts (Requirements 2.3, 2.6, 19.2). The
 * submitted plaintext password is confined to the transient request object and is never persisted
 * or logged (Requirement 2.3).</p>
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    /**
     * Uniform login-failure message. Identical for every failure mode (unknown user, bad password,
     * locked, disabled) so the response never reveals whether an account exists (Requirement 19.2).
     */
    private static final String UNIFORM_LOGIN_FAILURE = "Invalid credentials";

    /** Minimum/maximum login-handle length enforced at registration (Requirements 12.1, 12.12). */
    private static final int USERNAME_MIN = 1;
    private static final int USERNAME_MAX = 64;

    /** Minimum/maximum password length enforced at registration and change (Requirements 12.1, 12.2). */
    private static final int PASSWORD_MIN = 8;
    private static final int PASSWORD_MAX = 128;

    /** Default role granted to a new user when the registration request omits {@code roles}. */
    private static final Set<String> DEFAULT_ROLES = Set.of("USER");

    private final AuthenticationManager authenticationManager;
    private final ServerAccessTokenMinter tokenMinter;
    private final RefreshTokenStore refreshTokenStore;
    private final JtiBlocklist jtiBlocklist;
    private final UserAccountStore userAccountStore;
    private final ApiKeyStore apiKeyStore;
    private final AuthProperties auth;

    /**
     * Creates the controller.
     *
     * @param authenticationManager the manager verifying username/password logins
     * @param tokenMinter           the HS256 access-token minter
     * @param refreshTokenStore     the refresh-token store (persists only SHA-256 hashes)
     * @param jtiBlocklist          the {@code jti} blocklist consulted on logout
     * @param userAccountStore      the account store (used to resolve scopes/roles on refresh)
     * @param apiKeyStore           the per-user API-key store (issue/revoke)
     * @param properties            bound {@code spector.*} configuration (refresh TTL)
     */
    public AuthController(AuthenticationManager authenticationManager,
                          ServerAccessTokenMinter tokenMinter,
                          RefreshTokenStore refreshTokenStore,
                          JtiBlocklist jtiBlocklist,
                          UserAccountStore userAccountStore,
                          ApiKeyStore apiKeyStore,
                          SynapseProperties properties) {
        this.authenticationManager = authenticationManager;
        this.tokenMinter = tokenMinter;
        this.refreshTokenStore = refreshTokenStore;
        this.jtiBlocklist = jtiBlocklist;
        this.userAccountStore = userAccountStore;
        this.apiKeyStore = apiKeyStore;
        this.auth = properties.auth();
    }

    /**
     * Authenticates a username/password login and issues an access token plus a refresh token.
     *
     * <ul>
     *   <li>HTTP 400 when the username or password field is missing/blank — no credential
     *       verification is attempted (Requirement 2.7).</li>
     *   <li>HTTP 200 with an HS256 access token ({@code sub = }User_Id, scope/roles claims,
     *       3600s TTL) and a 30-day refresh token on success (Requirements 2.1, 2.2).</li>
     *   <li>HTTP 401 with a uniform message on any authentication failure — bad/unknown
     *       credentials, locked, or disabled — without revealing account existence and without
     *       retaining or logging the plaintext password (Requirements 2.3, 2.6, 19.2).</li>
     * </ul>
     *
     * @param request the login request body
     * @return the token response, or a uniform error response
     */
    @PostMapping(value = "/login", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> login(@RequestBody(required = false) LoginRequest request) {
        if (request == null || isBlank(request.username())) {
            return badRequest("username is required");
        }
        if (isBlank(request.password())) {
            return badRequest("password is required");
        }

        Authentication authentication;
        try {
            // codeql[java/user-controlled-bypass]
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        } catch (AuthenticationException e) {
            // Uniform failure: never distinguish unknown-user / bad-password / locked / disabled,
            // and never log the submitted password (Requirements 2.3, 2.6, 19.2).
            log.debug("[Auth] Login failed for a submitted username (uniform 401)");
            return unauthorized(UNIFORM_LOGIN_FAILURE);
        }

        String userId = authentication.getName();
        // codeql[java/user-controlled-bypass]
        MintedAccessToken access = tokenMinter.mintFromAuthorities(userId, authentication.getAuthorities());

        Instant refreshExpiresAt = Instant.now().plus(auth.refresh().ttl());
        String refreshToken = refreshTokenStore.create(userId, refreshExpiresAt);

        log.debug("[Auth] Login succeeded for user {}", userId);
        LoginResponse body = new LoginResponse(
                access.token(),
                AuthDto.BEARER,
                access.expiresInSeconds(),
                refreshToken,
                auth.refresh().ttl().toSeconds());
        return ResponseEntity.ok(body);
    }

    /**
     * Exchanges a valid refresh token for a new access token (Requirements 12.3, 12.9).
     *
     * <p>The presented raw token is SHA-256 hashed and looked up via
     * {@link RefreshTokenStore#findActive(String)}, which matches only non-revoked, non-expired
     * rows. When active, a new access token valid for {@code spector.auth.jwt.ttl} is minted from
     * the owning User's current scopes/roles; otherwise (missing, revoked, or expired token, or an
     * account that no longer exists) the request is denied with HTTP 401 and no token is issued.</p>
     *
     * @param request the refresh request body
     * @return a new access token, or an error response
     */
    @PostMapping(value = "/refresh", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> refresh(@RequestBody(required = false) RefreshRequest request) {
        if (request == null || isBlank(request.refreshToken())) {
            return badRequest("refresh_token is required");
        }

        String hash = RefreshTokenStore.sha256Hex(request.refreshToken());
        Optional<RefreshTokenStore.RefreshTokenRow> active = refreshTokenStore.findActive(hash);
        if (active.isEmpty()) {
            log.debug("[Auth] Refresh rejected: token is missing, revoked, or expired");
            return unauthorized("Invalid refresh token");
        }

        String userId = active.get().userId();
        Optional<UserRow> user = userAccountStore.findByUserId(userId);
        if (user.isEmpty() || !user.get().active()) {
            // Fail closed: never issue a token for a vanished or disabled account (Requirement 19.1).
            log.debug("[Auth] Refresh rejected: owning account is absent or inactive");
            return unauthorized("Invalid refresh token");
        }

        MintedAccessToken access = tokenMinter.mint(userId, user.get().scopes(), user.get().roles());
        log.debug("[Auth] Refreshed access token for user {}", userId);
        return ResponseEntity.ok(new RefreshResponse(access.token(), AuthDto.BEARER, access.expiresInSeconds()));
    }

    /**
     * Logs out the current session by adding the presented access token's {@code jti} to the
     * blocklist so any later request bearing that {@code jti} is rejected (Requirement 12.4).
     *
     * <p>When the caller authenticated with a server-issued JWT, its {@code jti} (and expiry, so the
     * blocklist row can be purged once the token would have expired anyway) is recorded. Logout is
     * idempotent: a request without a JWT {@code jti} (e.g. an API-key session) simply returns 200
     * with nothing to revoke.</p>
     *
     * @param jwt the authenticated JWT principal, or {@code null} for non-JWT sessions
     * @return HTTP 200
     */
    @PostMapping(value = "/logout", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> logout(@AuthenticationPrincipal Jwt jwt) {
        if (jwt != null) {
            String jti = jwt.getId();
            if (jti != null && !jti.isBlank()) {
                jtiBlocklist.add(jti, jwt.getExpiresAt());
                log.debug("[Auth] Logout blocklisted jti {}", jti);
            }
        }
        return ResponseEntity.ok().build();
    }

    /**
     * Registers a new user account (Requirements 12.1, 12.12, 16.4).
     *
     * <p>Scope-gated to callers holding the {@code ADMIN} role. The design lists registration as
     * "scope-gated"; the seeded default administrator carries the {@code ADMIN} role (not a
     * dedicated {@code admin:users} scope), so {@code hasRole('ADMIN')} is chosen here to keep the
     * endpoint usable out of the box while remaining consistent with the {@code users} endpoints.</p>
     *
     * <ul>
     *   <li>HTTP 400 when the username is outside {@value #USERNAME_MIN}–{@value #USERNAME_MAX}
     *       characters or the password is outside {@value #PASSWORD_MIN}–{@value #PASSWORD_MAX}
     *       characters — no user is created (Requirement 12.12).</li>
     *   <li>HTTP 409 when the username already exists (case-insensitive) — no user is created
     *       (Requirement 12.12).</li>
     *   <li>HTTP 201 with the generated 13-character TSID User_Id on success (Requirement 12.1).</li>
     * </ul>
     *
     * @param request the registration request body
     * @return the generated User_Id, or an error response
     */
    @PostMapping(value = "/register", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> register(@RequestBody(required = false) RegisterRequest request) {
        if (request == null) {
            return badRequest("username and password are required");
        }
        String username = request.username();
        String password = request.password();
        if (username == null || username.length() < USERNAME_MIN || username.length() > USERNAME_MAX) {
            return badRequest("username must be 1-64 characters");
        }
        if (password == null || password.length() < PASSWORD_MIN || password.length() > PASSWORD_MAX) {
            return badRequest("password must be 8-128 characters");
        }

        Set<String> roles = request.roles() != null && !request.roles().isEmpty()
                ? request.roles() : DEFAULT_ROLES;
        boolean mustChange = request.mustChangePassword() != null && request.mustChangePassword();
        try {
            String userId = userAccountStore.createUser(username, password, request.email(),
                    request.displayName(), roles, request.scopes(), mustChange);
            log.info("[Auth] Registered new user id={}", userId);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new RegisterResponse(userId));
        } catch (DuplicateUsernameException e) {
            log.debug("[Auth] Registration rejected: username already in use");
            return conflict("username already in use");
        }
    }

    /**
     * Changes the authenticated caller's password (Requirements 12.2, 12.8).
     *
     * <p>The current password is verified against the stored hash before any change. On success the
     * new hash is stored and the must-change-password flag is cleared (the store performs both).</p>
     *
     * <ul>
     *   <li>HTTP 400 when the new password is outside {@value #PASSWORD_MIN}–{@value #PASSWORD_MAX}
     *       characters, or the current password is missing.</li>
     *   <li>HTTP 401 when the current password does not match the stored hash — the stored hash is
     *       left unchanged (Requirement 12.8).</li>
     *   <li>HTTP 200 on a successful change (Requirement 12.2).</li>
     * </ul>
     *
     * @param request the change-password request body
     * @return HTTP 200 on success, or an error response
     */
    @PostMapping(value = "/change-password", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> changePassword(@RequestBody(required = false) ChangePasswordRequest request) {
        if (request == null || isBlank(request.currentPassword())) {
            return badRequest("current_password is required");
        }
        String newPassword = request.newPassword();
        if (newPassword == null || newPassword.length() < PASSWORD_MIN || newPassword.length() > PASSWORD_MAX) {
            return badRequest("new_password must be 8-128 characters");
        }

        String userId = SecurityUtils.getUserId();
        Optional<UserRow> user = userAccountStore.findByUserId(userId);
        if (user.isEmpty()) {
            // Fail closed: an authenticated principal with no backing account cannot change one.
            return unauthorized("Invalid credentials");
        }

        boolean changed = userAccountStore.changePassword(
                user.get().username(), request.currentPassword(), newPassword);
        if (!changed) {
            log.debug("[Auth] Change-password rejected: current password did not match");
            return unauthorized("Invalid credentials");
        }
        log.info("[Auth] Password changed for user id={}", userId);
        return ResponseEntity.ok().build();
    }

    /**
     * Lists all user accounts (Requirement 12.5). Restricted to callers holding the {@code ADMIN}
     * role. Each row is projected onto {@link UserSummary}, which omits the password hash.
     *
     * @return the list of user summaries (never carrying password hashes)
     */
    @GetMapping(value = "/users", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserSummary>> listUsers() {
        List<UserSummary> users = userAccountStore.listUsers().stream()
                .map(UserSummary::from)
                .toList();
        return ResponseEntity.ok(users);
    }

    /**
     * Updates a targeted user account (Requirement 12.11). Restricted to callers holding the
     * {@code ADMIN} role. Only the supplied ({@code non-null}) fields are changed.
     *
     * <ul>
     *   <li>HTTP 404 when the targeted user does not exist — no account is modified.</li>
     *   <li>HTTP 200 with the updated account (as a {@link UserSummary}) on success.</li>
     * </ul>
     *
     * @param id      the target User_Id (TSID)
     * @param request the update request body
     * @return the updated account summary, or a 404 error response
     */
    @PutMapping(value = "/users/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateUser(@PathVariable("id") String id,
                                        @RequestBody(required = false) UpdateUserRequest request) {
        UpdateUserRequest update = request != null
                ? request : new UpdateUserRequest(null, null, null, null);
        Optional<UserRow> updated = userAccountStore.updateAccount(
                id, update.active(), update.roles(), update.scopes(), update.displayName());
        if (updated.isEmpty()) {
            return notFound("user not found");
        }
        log.info("[Auth] Admin updated account for user id={}", id);
        return ResponseEntity.ok(UserSummary.from(updated.get()));
    }

    /**
     * Issues a per-user API key for the authenticated caller (Requirement 12.6).
     *
     * <p>The raw key value is returned in the response exactly once and is not recoverable from
     * storage — only its SHA-256 hash is persisted (the store performs the hashing). The key is
     * owned by the authenticated User_Id, never a client-supplied identity.</p>
     *
     * @param request the API-key creation request body (may be {@code null})
     * @return the raw key and its id (HTTP 201)
     */
    @PostMapping(value = "/api-keys", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CreateApiKeyResponse> createApiKey(
            @RequestBody(required = false) CreateApiKeyRequest request) {
        String userId = SecurityUtils.getUserId();
        Set<String> scopes = request != null ? request.scopes() : null;
        Instant expiresAt = request != null ? request.expiresAt() : null;

        ApiKeyCreation created = apiKeyStore.create(userId, scopes, expiresAt);
        log.info("[Auth] Issued API key {} for user id={}", created.keyId(), userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateApiKeyResponse(created.keyId(), created.rawKey()));
    }

    /**
     * Revokes an API key by id (Requirement 12.6). Revoked keys never authenticate again.
     *
     * @param id the 13-character TSID of the key to revoke
     * @return HTTP 204 on success, or HTTP 404 when no such key exists
     */
    @DeleteMapping(value = "/api-keys/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> revokeApiKey(@PathVariable("id") String id) {
        boolean revoked = apiKeyStore.revoke(id);
        if (!revoked) {
            return notFound("api key not found");
        }
        log.info("[Auth] Revoked API key {}", id);
        return ResponseEntity.noContent().build();
    }

    // ── Internal helpers ──

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static ResponseEntity<ErrorResponse> badRequest(String message) {
        return ResponseEntity.badRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ErrorResponse(400, "Bad Request", message));
    }

    private static ResponseEntity<ErrorResponse> unauthorized(String message) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ErrorResponse(401, "Unauthorized", message));
    }

    private static ResponseEntity<ErrorResponse> notFound(String message) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ErrorResponse(404, "Not Found", message));
    }

    private static ResponseEntity<ErrorResponse> conflict(String message) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ErrorResponse(409, "Conflict", message));
    }
}
