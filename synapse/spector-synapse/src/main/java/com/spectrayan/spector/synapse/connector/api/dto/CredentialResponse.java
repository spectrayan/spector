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
