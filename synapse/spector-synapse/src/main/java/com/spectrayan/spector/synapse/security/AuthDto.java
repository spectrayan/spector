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
import java.util.Set;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request/response data-transfer objects for the {@code /api/v1/auth} identity endpoints.
 *
 * <p>All DTOs are immutable Java records. Token fields follow the OAuth2 snake_case wire
 * convention ({@code access_token}, {@code refresh_token}, {@code token_type},
 * {@code expires_in}) so REST clients and the Cortex UI can consume them directly. The plaintext
 * password submitted at login lives only in the transient {@link LoginRequest} and is never
 * echoed back, persisted, or logged (Requirements 2.3, 19.6).</p>
 */
public final class AuthDto {

    private AuthDto() {}

    /** Bearer token type constant used in token responses. */
    public static final String BEARER = "Bearer";

    /**
     * Login request body.
     *
     * @param username the login handle (required)
     * @param password the plaintext password (required; never persisted or logged)
     */
    public record LoginRequest(
            @NotBlank(message = "username is required")
            @Size(min = 1, max = 64, message = "username length must be between 1 and 64 characters")
            @JsonProperty("username") String username,

            @NotBlank(message = "password is required")
            @Size(min = 1, max = 128, message = "password length must be between 1 and 128 characters")
            @JsonProperty("password") String password
    ) {}

    /**
     * Successful login response carrying the server-issued access token and a refresh token.
     *
     * @param accessToken            the HS256 access token (Requirement 2.1)
     * @param tokenType              always {@code "Bearer"}
     * @param expiresIn              access-token lifetime in seconds (3600s default)
     * @param refreshToken           the raw refresh token, returned exactly once
     * @param refreshExpiresIn       refresh-token lifetime in seconds (2,592,000s default)
     */
    public record LoginResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("token_type") String tokenType,
            @JsonProperty("expires_in") long expiresIn,
            @JsonProperty("refresh_token") String refreshToken,
            @JsonProperty("refresh_expires_in") long refreshExpiresIn
    ) {}

    /**
     * Refresh request body.
     *
     * @param refreshToken the raw refresh token previously issued at login
     */
    public record RefreshRequest(
            @NotBlank(message = "refresh_token is required")
            @JsonProperty("refresh_token") String refreshToken
    ) {}

    /**
     * Successful refresh response carrying a newly minted access token (Requirement 12.3).
     *
     * @param accessToken the newly minted HS256 access token
     * @param tokenType   always {@code "Bearer"}
     * @param expiresIn   access-token lifetime in seconds
     */
    public record RefreshResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("token_type") String tokenType,
            @JsonProperty("expires_in") long expiresIn
    ) {}

    /**
     * Account-registration request body (Requirement 12.1). Only {@code username} and
     * {@code password} are required; {@code roles}/{@code scopes}/{@code email}/{@code displayName}
     * and {@code mustChangePassword} are optional provisioning hints. The plaintext password is
     * confined to this transient object — never persisted in the clear or logged.
     *
     * @param username           unique login handle (1–64 characters)
     * @param password           plaintext password (8–128 characters)
     * @param email              optional contact email
     * @param displayName        optional human-readable name
     * @param roles              optional role names to grant (defaults to {@code USER})
     * @param scopes             optional scope names to grant
     * @param mustChangePassword whether the new user must change the password on next login
     */
    public record RegisterRequest(
            @NotBlank(message = "username is required")
            @Size(min = 1, max = 64, message = "username length must be between 1 and 64 characters")
            @JsonProperty("username") String username,

            @NotBlank(message = "password is required")
            @Size(min = 8, max = 128, message = "password length must be between 8 and 128 characters")
            @JsonProperty("password") String password,

            @JsonProperty("email") String email,
            @JsonProperty("display_name") String displayName,
            @JsonProperty("roles") Set<String> roles,
            @JsonProperty("scopes") Set<String> scopes,
            @JsonProperty("must_change_password") Boolean mustChangePassword
    ) {}

    /**
     * Registration response carrying the generated 13-character TSID User_Id (Requirement 12.1).
     *
     * @param userId the generated User_Id (TSID)
     */
    public record RegisterResponse(
            @JsonProperty("user_id") String userId
    ) {}

    /**
     * Change-password request body for the authenticated user (Requirements 12.2, 12.8).
     *
     * @param currentPassword the caller's current password (verified before any change)
     * @param newPassword     the replacement password (8–128 characters)
     */
    public record ChangePasswordRequest(
            @NotBlank(message = "current_password is required")
            @JsonProperty("current_password") String currentPassword,

            @NotBlank(message = "new_password is required")
            @Size(min = 8, max = 128, message = "new_password length must be between 8 and 128 characters")
            @JsonProperty("new_password") String newPassword
    ) {}

    /**
     * Admin request body to update a targeted user account (Requirement 12.11). Every field is
     * optional; a {@code null} field leaves the corresponding column unchanged.
     *
     * @param active      new enabled/disabled state, or {@code null} to leave unchanged
     * @param roles       replacement role set, or {@code null} to leave unchanged
     * @param scopes      replacement scope set, or {@code null} to leave unchanged
     * @param displayName replacement display name, or {@code null} to leave unchanged
     */
    public record UpdateUserRequest(
            @JsonProperty("active") Boolean active,
            @JsonProperty("roles") Set<String> roles,
            @JsonProperty("scopes") Set<String> scopes,
            @JsonProperty("display_name") String displayName
    ) {}

    /**
     * Safe user projection returned by the admin {@code users} endpoints. Deliberately excludes the
     * {@code password_hash} column so credentials never leave the store (Requirement 14.1).
     *
     * @param userId             the 13-character TSID principal
     * @param username           the login handle
     * @param email              optional contact email
     * @param displayName        optional display name
     * @param roles              granted role names
     * @param scopes             granted scope names
     * @param mustChangePassword whether the user must change the password on next login
     * @param active             whether the account is enabled
     * @param failedLoginCount   consecutive failed-login counter
     * @param lockedUntil        lockout expiry, or {@code null} when not locked
     * @param lastLoginAt        last successful login instant, or {@code null}
     * @param createdAt          row creation instant
     * @param updatedAt          row last-modification instant
     */
    public record UserSummary(
            @JsonProperty("user_id") String userId,
            @JsonProperty("username") String username,
            @JsonProperty("email") String email,
            @JsonProperty("display_name") String displayName,
            @JsonProperty("roles") Set<String> roles,
            @JsonProperty("scopes") Set<String> scopes,
            @JsonProperty("must_change_password") boolean mustChangePassword,
            @JsonProperty("active") boolean active,
            @JsonProperty("failed_login_count") int failedLoginCount,
            @JsonProperty("locked_until") Instant lockedUntil,
            @JsonProperty("last_login_at") Instant lastLoginAt,
            @JsonProperty("created_at") Instant createdAt,
            @JsonProperty("updated_at") Instant updatedAt
    ) {
        /**
         * Projects a {@link UserRow} onto the safe summary, dropping the password hash.
         *
         * @param row the persisted user row
         * @return a summary carrying every field except the password hash
         */
        public static UserSummary from(UserRow row) {
            return new UserSummary(
                    row.userId(), row.username(), row.email(), row.displayName(),
                    row.roles(), row.scopes(), row.mustChangePassword(), row.active(),
                    row.failedLoginCount(), row.lockedUntil(), row.lastLoginAt(),
                    row.createdAt(), row.updatedAt());
        }
    }

    /**
     * Request body to issue a per-user API key (Requirement 12.6).
     *
     * @param scopes    optional scopes to grant the key
     * @param expiresAt optional expiry instant, or {@code null} for a non-expiring key
     */
    public record CreateApiKeyRequest(
            @JsonProperty("scopes") Set<String> scopes,
            @JsonProperty("expires_at") Instant expiresAt
    ) {}

    /**
     * Response for a newly issued API key. The raw {@code apiKey} value is returned exactly once and
     * is not recoverable from storage (only its SHA-256 hash is persisted) (Requirement 12.6).
     *
     * @param keyId  the 13-character TSID identifying the key row
     * @param apiKey the raw API key value (shown exactly once)
     */
    public record CreateApiKeyResponse(
            @JsonProperty("key_id") String keyId,
            @JsonProperty("api_key") String apiKey
    ) {}
}
