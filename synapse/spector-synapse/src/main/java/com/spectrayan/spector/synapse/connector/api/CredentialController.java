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
package com.spectrayan.spector.synapse.connector.api;

import com.spectrayan.spector.synapse.connector.api.dto.CreateCredentialRequest;
import com.spectrayan.spector.synapse.connector.api.dto.CredentialResponse;
import com.spectrayan.spector.synapse.connector.api.dto.UpdateCredentialRequest;
import com.spectrayan.spector.synapse.connector.model.CredentialCategory;
import com.spectrayan.spector.synapse.connector.model.CredentialRecord;
import com.spectrayan.spector.synapse.connector.model.CredentialType;
import com.spectrayan.spector.synapse.connector.repository.JdbcEncryptedCredentialProvider;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * REST API for managing encrypted credentials, BYOK LLM keys, channel tokens,
 * database passwords, and enterprise KMS vault configurations.
 */
@RestController
@RequestMapping("/api/v1/credentials")
@Validated
public class CredentialController {

    private static final Logger log = LoggerFactory.getLogger(CredentialController.class);

    private final JdbcEncryptedCredentialProvider credentialProvider;

    public CredentialController(JdbcEncryptedCredentialProvider credentialProvider) {
        this.credentialProvider = Objects.requireNonNull(credentialProvider, "credentialProvider must not be null");
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CredentialResponse createCredential(
            @RequestHeader(value = "X-Tenant-ID", defaultValue = "default") String tenantId,
            @Valid @RequestBody CreateCredentialRequest request,
            Authentication authentication) {

        String userId = authentication != null ? authentication.getName() : null;
        log.info("[CredentialAPI] Creating credential '{}' for tenant '{}' (provider={}, category={})",
                request.name(), tenantId, request.provider(), request.category());

        CredentialRecord record = credentialProvider.save(
                tenantId,
                userId,
                request.name(),
                request.category(),
                request.provider(),
                request.credentialType() != null ? request.credentialType() : CredentialType.API_KEY,
                request.secret(),
                request.properties(),
                request.isDefault(),
                request.description(),
                request.expiresAt()
        );

        return CredentialResponse.fromRecord(record);
    }

    @GetMapping
    public List<CredentialResponse> listCredentials(
            @RequestHeader(value = "X-Tenant-ID", defaultValue = "default") String tenantId,
            @RequestParam(required = false) String userId) {

        List<CredentialRecord> records;
        if (userId != null && !userId.isBlank()) {
            records = credentialProvider.findByUserId(tenantId, userId);
        } else {
            records = credentialProvider.findByTenantId(tenantId);
        }

        return records.stream()
                .map(CredentialResponse::fromRecord)
                .toList();
    }

    @GetMapping("/{name}")
    public ResponseEntity<CredentialResponse> getCredential(
            @RequestHeader(value = "X-Tenant-ID", defaultValue = "default") String tenantId,
            @PathVariable String name) {

        return credentialProvider.findByName(tenantId, name)
                .map(CredentialResponse::fromRecord)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{name}")
    public ResponseEntity<CredentialResponse> updateCredential(
            @RequestHeader(value = "X-Tenant-ID", defaultValue = "default") String tenantId,
            @PathVariable String name,
            @RequestBody UpdateCredentialRequest request,
            Authentication authentication) {

        Optional<CredentialRecord> existingOpt = credentialProvider.findByName(tenantId, name);
        if (existingOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        CredentialRecord existing = existingOpt.get();
        String userId = authentication != null ? authentication.getName() : existing.userId();

        CredentialCategory category = request.category() != null ? request.category() : existing.category();
        String provider = request.provider() != null ? request.provider() : existing.provider();
        CredentialType type = request.credentialType() != null ? request.credentialType() : existing.credentialType();
        Map<String, Object> props = request.properties() != null ? request.properties() : existing.properties();
        boolean isDefault = request.isDefault() != null ? request.isDefault() : existing.isDefault();
        String description = request.description() != null ? request.description() : existing.description();
        var expiresAt = request.expiresAt() != null ? request.expiresAt() : existing.expiresAt();

        // If secret is updated, use new secret; otherwise decrypt existing secret to preserve it
        String secret = request.secret();
        if (secret == null || secret.isBlank()) {
            secret = credentialProvider.resolve(existing.name(), tenantId)
                    .orElseThrow(() -> new IllegalStateException("Failed to decrypt existing secret for update"));
        }

        CredentialRecord updated = credentialProvider.save(
                tenantId,
                userId,
                name,
                category,
                provider,
                type,
                secret,
                props,
                isDefault,
                description,
                expiresAt
        );

        return ResponseEntity.ok(CredentialResponse.fromRecord(updated));
    }

    @DeleteMapping("/{name}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> deleteCredential(
            @RequestHeader(value = "X-Tenant-ID", defaultValue = "default") String tenantId,
            @PathVariable String name) {

        boolean deleted = credentialProvider.deleteByName(tenantId, name);
        if (!deleted) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{name}/test")
    public ResponseEntity<Map<String, Object>> testCredential(
            @RequestHeader(value = "X-Tenant-ID", defaultValue = "default") String tenantId,
            @PathVariable String name) {

        Optional<CredentialRecord> recordOpt = credentialProvider.findByName(tenantId, name);
        if (recordOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        CredentialRecord record = recordOpt.get();
        Optional<String> secretOpt = credentialProvider.resolve(record.name(), tenantId);
        if (secretOpt.isEmpty() || secretOpt.get().isBlank()) {
            return ResponseEntity.ok(Map.of(
                    "status", "FAILED",
                    "name", record.name(),
                    "provider", record.provider(),
                    "message", "Secret resolution or decryption failed"
            ));
        }

        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "name", record.name(),
                "provider", record.provider(),
                "category", record.category().name(),
                "maskedPreview", record.maskedPreview(),
                "message", "Credential successfully decrypted and validated"
        ));
    }
}
