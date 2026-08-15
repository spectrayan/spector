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
import com.spectrayan.spector.synapse.connector.model.CredentialRecord;
import com.spectrayan.spector.synapse.connector.model.CredentialType;

import java.time.Instant;
import java.util.Map;

/**
 * Public response DTO for credential metadata and masked preview.
 *
 * <p>Never exposes raw plaintext secrets or encryption keys.</p>
 */
public record CredentialResponse(
        String credentialId,
        String tenantId,
        String userId,
        String name,
        CredentialCategory category,
        String provider,
        CredentialType credentialType,
        String maskedPreview,
        Map<String, Object> properties,
        boolean isDefault,
        String description,
        int version,
        Instant createdAt,
        Instant updatedAt,
        Instant expiresAt,
        Instant lastUsedAt
) {
    public static CredentialResponse fromRecord(CredentialRecord record) {
        return new CredentialResponse(
                record.credentialId(),
                record.tenantId(),
                record.userId(),
                record.name(),
                record.category(),
                record.provider(),
                record.credentialType(),
                record.maskedPreview(),
                record.properties(),
                record.isDefault(),
                record.description(),
                record.version(),
                record.createdAt(),
                record.updatedAt(),
                record.expiresAt(),
                record.lastUsedAt()
        );
    }
}
