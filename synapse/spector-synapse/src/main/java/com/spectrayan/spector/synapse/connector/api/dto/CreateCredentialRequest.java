/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
