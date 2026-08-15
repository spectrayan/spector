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
package com.spectrayan.spector.synapse.connector.api.dto;

import com.spectrayan.spector.synapse.connector.model.CredentialCategory;
import com.spectrayan.spector.synapse.connector.model.CredentialType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Map;

/**
 * Request payload for creating or rotating a credential.
 */
public record CreateCredentialRequest(
        @NotBlank(message = "name is required")
        @Size(max = 128, message = "name must not exceed 128 characters")
        String name,

        @NotNull(message = "category is required")
        CredentialCategory category,

        @NotBlank(message = "provider is required")
        @Size(max = 64, message = "provider must not exceed 64 characters")
        String provider,

        CredentialType credentialType,

        @NotBlank(message = "secret is required")
        String secret,

        Map<String, Object> properties,

        boolean isDefault,

        String description,

        Instant expiresAt
) {}
