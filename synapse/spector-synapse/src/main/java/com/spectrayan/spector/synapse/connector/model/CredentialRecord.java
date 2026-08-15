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
package com.spectrayan.spector.synapse.connector.model;

import java.time.Instant;
import java.util.Map;

/**
 * Immutable entity representing a stored credential row in the {@code credentials} table.
 */
public record CredentialRecord(
        String credentialId,
        String tenantId,
        String userId,
        String name,
        CredentialCategory category,
        String provider,
        CredentialType credentialType,
        String ciphertext,
        String iv,
        String authTag,
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
    public static Builder builder(String credentialId, String tenantId, String name,
                                  CredentialCategory category, String provider) {
        return new Builder(credentialId, tenantId, name, category, provider);
    }

    public static class Builder {
        private final String credentialId;
        private final String tenantId;
        private final String name;
        private final CredentialCategory category;
        private final String provider;
        private String userId;
        private CredentialType credentialType = CredentialType.API_KEY;
        private String ciphertext;
        private String iv;
        private String authTag;
        private String maskedPreview;
        private Map<String, Object> properties = Map.of();
        private boolean isDefault = false;
        private String description;
        private int version = 1;
        private Instant createdAt = Instant.now();
        private Instant updatedAt = Instant.now();
        private Instant expiresAt;
        private Instant lastUsedAt;

        public Builder(String credentialId, String tenantId, String name,
                       CredentialCategory category, String provider) {
            this.credentialId = credentialId;
            this.tenantId = tenantId;
            this.name = name;
            this.category = category;
            this.provider = provider;
        }

        public Builder userId(String userId) { this.userId = userId; return this; }
        public Builder credentialType(CredentialType type) { this.credentialType = type; return this; }
        public Builder ciphertext(String ciphertext) { this.ciphertext = ciphertext; return this; }
        public Builder iv(String iv) { this.iv = iv; return this; }
        public Builder authTag(String authTag) { this.authTag = authTag; return this; }
        public Builder maskedPreview(String preview) { this.maskedPreview = preview; return this; }
        public Builder properties(Map<String, Object> props) { this.properties = props != null ? props : Map.of(); return this; }
        public Builder isDefault(boolean isDefault) { this.isDefault = isDefault; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder version(int version) { this.version = version; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder updatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }
        public Builder expiresAt(Instant expiresAt) { this.expiresAt = expiresAt; return this; }
        public Builder lastUsedAt(Instant lastUsedAt) { this.lastUsedAt = lastUsedAt; return this; }

        public CredentialRecord build() {
            return new CredentialRecord(
                    credentialId, tenantId, userId, name, category, provider, credentialType,
                    ciphertext, iv, authTag, maskedPreview, properties, isDefault, description,
                    version, createdAt, updatedAt, expiresAt, lastUsedAt
            );
        }
    }
}
