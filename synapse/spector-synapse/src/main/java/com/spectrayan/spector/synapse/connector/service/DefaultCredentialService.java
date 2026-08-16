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
package com.spectrayan.spector.synapse.connector.service;

import com.spectrayan.spector.synapse.config.cache.SynapseCacheConstants;
import com.spectrayan.spector.synapse.connector.api.dto.CreateCredentialRequest;
import com.spectrayan.spector.synapse.connector.api.dto.UpdateCredentialRequest;
import com.spectrayan.spector.synapse.connector.model.CredentialCategory;
import com.spectrayan.spector.synapse.connector.model.CredentialRecord;
import com.spectrayan.spector.synapse.connector.model.CredentialType;
import com.spectrayan.spector.synapse.connector.repository.CredentialRepository;
import com.spectrayan.spector.synapse.security.crypto.AesGcmCipher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Default implementation of {@link CredentialService} providing encryption orchestration,
 * secret caching, and business logic.
 */
@Service
public class DefaultCredentialService implements CredentialService {

    private static final Logger log = LoggerFactory.getLogger(DefaultCredentialService.class);

    private final CredentialRepository repository;
    private final AesGcmCipher cipher;

    public DefaultCredentialService(CredentialRepository repository, AesGcmCipher cipher) {
        this.repository = Objects.requireNonNull(repository, "CredentialRepository must not be null");
        this.cipher = Objects.requireNonNull(cipher, "AesGcmCipher must not be null");
    }

    @Override
    @CacheEvict(value = SynapseCacheConstants.CACHE_DECRYPTED_SECRETS, allEntries = true)
    public CredentialRecord createCredential(String tenantId, String userId, CreateCredentialRequest request) {
        Objects.requireNonNull(request, "CreateCredentialRequest must not be null");
        String effectiveTenant = tenantId != null && !tenantId.isBlank() ? tenantId : "default";
        String normalizedName = request.name().trim().toLowerCase();
        String normalizedProvider = request.provider().trim().toLowerCase();
        CredentialType type = request.credentialType() != null ? request.credentialType() : CredentialType.API_KEY;

        // Manage default flag
        if (request.isDefault()) {
            repository.clearDefault(effectiveTenant, normalizedProvider);
        }

        AesGcmCipher.EncryptedPayload encrypted = cipher.encrypt(request.secret(), effectiveTenant);
        String credentialId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        CredentialRecord record = CredentialRecord.builder(credentialId, effectiveTenant, normalizedName,
                        request.category(), normalizedProvider)
                .userId(userId)
                .credentialType(type)
                .ciphertext(encrypted.ciphertext())
                .iv(encrypted.iv())
                .authTag(encrypted.authTag())
                .maskedPreview(encrypted.maskedPreview())
                .properties(request.properties())
                .isDefault(request.isDefault())
                .description(request.description())
                .createdAt(now)
                .updatedAt(now)
                .expiresAt(request.expiresAt())
                .build();

        CredentialRecord saved = repository.save(record);
        log.info("[CredentialService] Saved credential '{}' (tenant={}, provider={}, category={}, default={})",
                normalizedName, effectiveTenant, normalizedProvider, request.category(), request.isDefault());
        return saved;
    }

    @Override
    @CacheEvict(value = SynapseCacheConstants.CACHE_DECRYPTED_SECRETS, allEntries = true)
    public Optional<CredentialRecord> updateCredential(String tenantId, String userId, String name, UpdateCredentialRequest request) {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(request, "UpdateCredentialRequest must not be null");
        String effectiveTenant = tenantId != null && !tenantId.isBlank() ? tenantId : "default";
        String normalizedName = name.trim().toLowerCase();

        Optional<CredentialRecord> existingOpt = repository.findByName(effectiveTenant, normalizedName);
        if (existingOpt.isEmpty()) {
            return Optional.empty();
        }

        CredentialRecord existing = existingOpt.get();
        String effectiveUserId = userId != null ? userId : existing.userId();
        CredentialCategory category = request.category() != null ? request.category() : existing.category();
        String provider = request.provider() != null ? request.provider().trim().toLowerCase() : existing.provider();
        CredentialType type = request.credentialType() != null ? request.credentialType() : existing.credentialType();
        Map<String, Object> props = request.properties() != null ? request.properties() : existing.properties();
        boolean isDefault = request.isDefault() != null ? request.isDefault() : existing.isDefault();
        String description = request.description() != null ? request.description() : existing.description();
        Instant expiresAt = request.expiresAt() != null ? request.expiresAt() : existing.expiresAt();

        if (isDefault && !existing.isDefault()) {
            repository.clearDefault(effectiveTenant, provider);
        }

        String ciphertext = existing.ciphertext();
        String iv = existing.iv();
        String authTag = existing.authTag();
        String maskedPreview = existing.maskedPreview();

        if (request.secret() != null && !request.secret().isBlank()) {
            AesGcmCipher.EncryptedPayload encrypted = cipher.encrypt(request.secret(), effectiveTenant);
            ciphertext = encrypted.ciphertext();
            iv = encrypted.iv();
            authTag = encrypted.authTag();
            maskedPreview = encrypted.maskedPreview();
        }

        CredentialRecord updated = CredentialRecord.builder(existing.credentialId(), effectiveTenant, normalizedName,
                        category, provider)
                .userId(effectiveUserId)
                .credentialType(type)
                .ciphertext(ciphertext)
                .iv(iv)
                .authTag(authTag)
                .maskedPreview(maskedPreview)
                .properties(props)
                .isDefault(isDefault)
                .description(description)
                .version(existing.version() + 1)
                .createdAt(existing.createdAt())
                .updatedAt(Instant.now())
                .expiresAt(expiresAt)
                .lastUsedAt(existing.lastUsedAt())
                .build();

        CredentialRecord saved = repository.save(updated);
        log.info("[CredentialService] Updated credential '{}' (tenant={}, version={})", normalizedName, effectiveTenant, saved.version());
        return Optional.of(saved);
    }

    @Override
    public Optional<CredentialRecord> getCredential(String tenantId, String name) {
        String effectiveTenant = tenantId != null ? tenantId : "default";
        return repository.findByName(effectiveTenant, name);
    }

    @Override
    public List<CredentialRecord> listCredentials(String tenantId, String userId) {
        String effectiveTenant = tenantId != null ? tenantId : "default";
        if (userId != null && !userId.isBlank()) {
            return repository.findByUserId(effectiveTenant, userId);
        }
        return repository.findByTenantId(effectiveTenant);
    }

    @Override
    @CacheEvict(value = SynapseCacheConstants.CACHE_DECRYPTED_SECRETS, allEntries = true)
    public boolean deleteCredential(String tenantId, String name) {
        String effectiveTenant = tenantId != null ? tenantId : "default";
        String normalizedName = name.trim().toLowerCase();
        return repository.deleteByName(effectiveTenant, normalizedName);
    }

    @Override
    @Cacheable(value = SynapseCacheConstants.CACHE_DECRYPTED_SECRETS, key = "#tenantId + ':' + #credentialRef.toLowerCase()")
    public Optional<String> resolveSecret(String credentialRef, String tenantId) {
        if (credentialRef == null || credentialRef.isBlank()) {
            return Optional.empty();
        }

        String effectiveTenant = tenantId != null ? tenantId : "default";
        String parsedName = credentialRef.trim();

        if (parsedName.startsWith("tenant:")) {
            parsedName = parsedName.substring(7);
            if (parsedName.startsWith("current:")) {
                parsedName = parsedName.substring(8);
            } else if (parsedName.contains(":")) {
                int colonIdx = parsedName.indexOf(':');
                effectiveTenant = parsedName.substring(0, colonIdx);
                parsedName = parsedName.substring(colonIdx + 1);
            }
        } else if (parsedName.startsWith("user:")) {
            parsedName = parsedName.substring(5);
            if (parsedName.contains(":")) {
                int colonIdx = parsedName.indexOf(':');
                parsedName = parsedName.substring(colonIdx + 1);
            }
        }

        String normalizedName = parsedName.toLowerCase();

        Optional<CredentialRecord> recordOpt = repository.findByName(effectiveTenant, normalizedName);
        if (recordOpt.isEmpty()) {
            recordOpt = repository.findDefaultByProvider(effectiveTenant, normalizedName);
        }

        if (recordOpt.isEmpty()) {
            return Optional.empty();
        }

        CredentialRecord record = recordOpt.get();
        try {
            String decrypted = cipher.decrypt(record.ciphertext(), record.iv(), effectiveTenant);
            repository.updateLastUsedAt(record.credentialId(), Instant.now());
            return Optional.of(decrypted);
        } catch (Exception e) {
            log.error("[CredentialService] Failed decrypting credential '{}' for tenant '{}': {}",
                    normalizedName, effectiveTenant, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Map<String, Object> testCredential(String tenantId, String name) {
        String effectiveTenant = tenantId != null ? tenantId : "default";
        Optional<CredentialRecord> recordOpt = repository.findByName(effectiveTenant, name);
        if (recordOpt.isEmpty()) {
            return Map.of("status", "NOT_FOUND", "message", "Credential not found: " + name);
        }

        CredentialRecord record = recordOpt.get();
        Optional<String> decrypted = resolveSecret(name, effectiveTenant);
        if (decrypted.isEmpty() || decrypted.get().isBlank()) {
            return Map.of(
                    "status", "FAILED",
                    "name", record.name(),
                    "provider", record.provider(),
                    "message", "Secret resolution or decryption failed"
            );
        }

        return Map.of(
                "status", "SUCCESS",
                "name", record.name(),
                "provider", record.provider(),
                "category", record.category().name(),
                "maskedPreview", record.maskedPreview(),
                "message", "Credential successfully decrypted and validated"
        );
    }
}
