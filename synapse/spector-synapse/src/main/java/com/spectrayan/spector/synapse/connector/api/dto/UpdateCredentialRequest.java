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

import java.time.Instant;
import java.util.Map;

/**
 * Request payload for updating an existing credential.
 */
public record UpdateCredentialRequest(
        CredentialCategory category,
        String provider,
        CredentialType credentialType,
        String secret,
        Map<String, Object> properties,
        Boolean isDefault,
        String description,
        Instant expiresAt
) {}
