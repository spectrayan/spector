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
package com.spectrayan.spector.synapse.connector.service;

import com.spectrayan.spector.synapse.config.cache.SynapseCacheConstants;
import com.spectrayan.spector.synapse.connector.api.dto.CreateCredentialRequest;
import com.spectrayan.spector.synapse.connector.api.dto.UpdateCredentialRequest;
import com.spectrayan.spector.synapse.connector.model.CredentialCategory;
import com.spectrayan.spector.synapse.connector.model.CredentialRecord;
import com.spectrayan.spector.synapse.connector.model.CredentialType;
import com.spectrayan.spector.synapse.connector.repository.CredentialRepository;
import com.spectrayan.spector.synapse.security.SecurityUtils;
import com.spectrayan.spector.synapse.security.crypto.AesGcmCipher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
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
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder KEY_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final CredentialRepository repository;
    private final AesGcmCipher cipher;

    public DefaultCredentialService(CredentialRepository repository, AesGcmCipher cipher) {
        this.repository = Objects.requireNonNull(repository, "CredentialRepository must not be null");
        this.cipher = Objects.requireNonNull(cipher, "AesGcmCipher must not be null");
    }

    public static String generateRawApiKey() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return "sk_spec_" + KEY_ENCODER.encodeToString(bytes);
    }

    public static String sha256Hex(String raw) {
        if (raw == null) return null;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    @Override
    @CacheEvict(value = SynapseCacheConstants.CACHE_DECRYPTED_SECRETS, allEntries = true)
    public CredentialRecord createCredential(String tenantId, String userId, CreateCredentialRequest request) {
        Objects.requireNonNull(request, "CreateCredentialRequest must not be null");
        String effectiveTenant = tenantId != null && !tenantId.isBlank() ? tenantId : "default";
        String normalizedName = request.name().trim().toLowerCase();
        String normalizedProvider = request.provider().trim().toLowerCase();
        CredentialType type = request.credentialType() != null ? request.credentialType() : CredentialType.API_KEY;

        validateElevatedScopeAssignment(request.properties());

        String secret = request.secret();
        String generatedRawKey = null;
        boolean isAuthOrApiKey = request.category() == CredentialCategory.AUTH || type == CredentialType.API_KEY;

        if (secret == null || secret.isBlank()) {
            if (isAuthOrApiKey) {
                generatedRawKey = generateRawApiKey();
                secret = generatedRawKey;
            } else {
                throw new IllegalArgumentException("secret is required for category " + request.category());
            }
        }

        String keyHash = null;
        if (isAuthOrApiKey || request.category() == CredentialCategory.AUTH) {
            keyHash = sha256Hex(secret);
        }

        // Manage default flag
        boolean isDefaultFlag = Boolean.TRUE.equals(request.isDefault());
        if (isDefaultFlag) {
            repository.clearDefault(effectiveTenant, normalizedProvider);
        }

        AesGcmCipher.EncryptedPayload encrypted = cipher.encrypt(secret, effectiveTenant);
        String credentialId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        CredentialRecord record = CredentialRecord.builder(credentialId, effectiveTenant, normalizedName,
                        request.category(), normalizedProvider)
                .userId(userId)
                .credentialType(type)
                .keyHash(keyHash)
                .rawSecret(generatedRawKey != null ? generatedRawKey : (request.category() == CredentialCategory.AUTH ? secret : null))
                .ciphertext(encrypted.ciphertext())
                .iv(encrypted.iv())
                .authTag(encrypted.authTag())
                .maskedPreview(encrypted.maskedPreview())
                .properties(request.properties())
                .isDefault(isDefaultFlag)
                .description(request.description())
                .createdAt(now)
                .updatedAt(now)
                .expiresAt(request.expiresAt())
                .build();

        CredentialRecord saved = repository.save(record);
        log.info("[CredentialService] Saved credential '{}' (tenant={}, provider={}, category={}, default={})",
                normalizedName, effectiveTenant, normalizedProvider, request.category(), isDefaultFlag);

        if (record.rawSecret() != null) {
            return new CredentialRecord(
                    saved.credentialId(), saved.tenantId(), saved.userId(), saved.name(),
                    saved.category(), saved.provider(), saved.credentialType(), saved.keyHash(),
                    saved.ciphertext(), saved.iv(), saved.authTag(), saved.maskedPreview(),
                    saved.properties(), saved.isDefault(), saved.description(), saved.version(),
                    saved.createdAt(), saved.updatedAt(), saved.expiresAt(), saved.lastUsedAt(),
                    record.rawSecret()
            );
        }
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

        validateElevatedScopeAssignment(props);

        if (isDefault && !existing.isDefault()) {
            repository.clearDefault(effectiveTenant, provider);
        }

        String ciphertext = existing.ciphertext();
        String iv = existing.iv();
        String authTag = existing.authTag();
        String maskedPreview = existing.maskedPreview();
        String keyHash = existing.keyHash();

        if (request.secret() != null && !request.secret().isBlank()) {
            AesGcmCipher.EncryptedPayload encrypted = cipher.encrypt(request.secret(), effectiveTenant);
            ciphertext = encrypted.ciphertext();
            iv = encrypted.iv();
            authTag = encrypted.authTag();
            maskedPreview = encrypted.maskedPreview();
            if (category == CredentialCategory.AUTH || type == CredentialType.API_KEY || keyHash != null) {
                keyHash = sha256Hex(request.secret());
            }
        }

        CredentialRecord updated = CredentialRecord.builder(existing.credentialId(), effectiveTenant, normalizedName,
                        category, provider)
                .userId(effectiveUserId)
                .credentialType(type)
                .keyHash(keyHash)
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
    public Optional<CredentialRecord> getCredentialWithAuthorization(String tenantId, String callerUserId, String name) {
        String effectiveTenant = tenantId != null ? tenantId : "default";
        Optional<CredentialRecord> recordOpt = repository.findByName(effectiveTenant, name);
        if (recordOpt.isEmpty()) {
            return Optional.empty();
        }
        checkOwnership(recordOpt.get(), effectiveTenant, callerUserId);
        return recordOpt;
    }

    @Override
    @CacheEvict(value = SynapseCacheConstants.CACHE_DECRYPTED_SECRETS, allEntries = true)
    public Optional<CredentialRecord> updateCredentialWithAuthorization(String tenantId, String callerUserId, String name, UpdateCredentialRequest request) {
        String effectiveTenant = tenantId != null ? tenantId : "default";
        String normalizedName = name.trim().toLowerCase();
        Optional<CredentialRecord> existingOpt = repository.findByName(effectiveTenant, normalizedName);
        if (existingOpt.isEmpty()) {
            return Optional.empty();
        }
        checkOwnership(existingOpt.get(), effectiveTenant, callerUserId);
        return updateCredential(effectiveTenant, callerUserId, name, request);
    }

    @Override
    @CacheEvict(value = SynapseCacheConstants.CACHE_DECRYPTED_SECRETS, allEntries = true)
    public boolean deleteCredentialWithAuthorization(String tenantId, String callerUserId, String name) {
        String effectiveTenant = tenantId != null ? tenantId : "default";
        String normalizedName = name.trim().toLowerCase();
        Optional<CredentialRecord> existingOpt = repository.findByName(effectiveTenant, normalizedName);
        if (existingOpt.isEmpty()) {
            return false;
        }
        checkOwnership(existingOpt.get(), effectiveTenant, callerUserId);
        return deleteCredential(effectiveTenant, name);
    }

    @Override
    public Map<String, Object> testCredentialWithAuthorization(String tenantId, String callerUserId, String name) {
        String effectiveTenant = tenantId != null ? tenantId : "default";
        Optional<CredentialRecord> recordOpt = repository.findByName(effectiveTenant, name);
        if (recordOpt.isEmpty()) {
            return Map.of("status", "NOT_FOUND", "message", "Credential not found: " + name);
        }
        checkOwnership(recordOpt.get(), effectiveTenant, callerUserId);
        return testCredential(effectiveTenant, name);
    }

    @Override
    public void checkOwnership(CredentialRecord record, String callerTenant, String callerUserId) {
        if (record == null) {
            return;
        }

        if (SecurityUtils.isSuperAdmin()) {
            return;
        }

        String effectiveCallerTenant = callerTenant != null && !callerTenant.isBlank() ? callerTenant : SecurityUtils.getTenantId();

        if (SecurityUtils.isAdmin()) {
            if (effectiveCallerTenant != null && !effectiveCallerTenant.equalsIgnoreCase(record.tenantId())) {
                throw new org.springframework.security.access.AccessDeniedException(
                        "[SPE-820-001 / SPE-SEC-001] Cross-tenant access denied: tenant '" + effectiveCallerTenant
                                + "' cannot access credential '" + record.name() + "' belonging to tenant '" + record.tenantId() + "'");
            }
            return;
        }

        // Regular user: must belong to same tenant AND match owning userId
        if (effectiveCallerTenant != null && !effectiveCallerTenant.equalsIgnoreCase(record.tenantId())) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "[SPE-820-001 / SPE-SEC-001] Cross-tenant access denied: tenant '" + effectiveCallerTenant
                            + "' cannot access credential '" + record.name() + "' belonging to tenant '" + record.tenantId() + "'");
        }

        String effectiveCallerUser = callerUserId != null && !callerUserId.isBlank() ? callerUserId : SecurityUtils.getUserId();
        if (effectiveCallerUser == null || record.userId() == null || !effectiveCallerUser.equals(record.userId())) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "[SPE-820-001 / SPE-SEC-001] Cross-user access denied: user '" + effectiveCallerUser
                            + "' cannot access credential '" + record.name() + "' owned by '" + record.userId() + "'");
        }
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
    public List<CredentialRecord> listCredentialsFleetWide(String tenantId, String userId) {
        if (tenantId != null && !tenantId.isBlank()) {
            if (userId != null && !userId.isBlank()) {
                return repository.findByUserId(tenantId, userId);
            }
            return repository.findByTenantId(tenantId);
        }
        if (userId != null && !userId.isBlank()) {
            return repository.findAllByUserId(userId);
        }
        return repository.findAll();
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

    private void validateElevatedScopeAssignment(Map<String, Object> properties) {
        if (properties == null || !properties.containsKey("scopes")) {
            return;
        }
        Object scopesObj = properties.get("scopes");
        List<String> scopes = new ArrayList<>();
        if (scopesObj instanceof java.util.Collection<?> col) {
            for (Object o : col) {
                if (o != null) scopes.add(o.toString());
            }
        } else if (scopesObj instanceof String s) {
            for (String part : s.split("[,\\s]+")) {
                if (!part.isBlank()) scopes.add(part.trim());
            }
        }
        for (String scope : scopes) {
            if (isSuperAdminTarget(scope)) {
                if (!SecurityUtils.isSuperAdmin()) {
                    throw new org.springframework.security.access.AccessDeniedException(
                            "[SPE-820-001 / SPE-SEC-001] Super-admin role required to assign super-admin scope");
                }
            } else if (isAdminTarget(scope)) {
                if (!SecurityUtils.isAdmin() && !SecurityUtils.isSuperAdmin()) {
                    throw new org.springframework.security.access.AccessDeniedException(
                            "[SPE-820-001 / SPE-SEC-001] Admin role required to assign admin scope");
                }
            }
        }
    }

    private static boolean isSuperAdminTarget(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String trimmed = value.trim();
        String norm = trimmed.replaceAll("(?<=[a-z0-9])(?=[A-Z])", "-").toLowerCase().replace('_', '-');
        while (norm.startsWith("role-") || norm.startsWith("scope-")) {
            if (norm.startsWith("role-")) {
                norm = norm.substring(5).trim();
            } else {
                norm = norm.substring(6).trim();
            }
        }
        return norm.equals("super-admin") || norm.equals("spector:super-admin") || norm.equals("spector:admin");
    }

    private static boolean isAdminTarget(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        if (isSuperAdminTarget(value)) {
            return true;
        }
        String trimmed = value.trim();
        String norm = trimmed.replaceAll("(?<=[a-z0-9])(?=[A-Z])", "-").toLowerCase().replace('_', '-');
        while (norm.startsWith("role-") || norm.startsWith("scope-")) {
            if (norm.startsWith("role-")) {
                norm = norm.substring(5).trim();
            } else {
                norm = norm.substring(6).trim();
            }
        }
        return norm.equals("admin") || norm.equals("spector:namespace:admin");
    }
}
