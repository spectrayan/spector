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
import com.spectrayan.spector.synapse.connector.model.CredentialRecord;
import com.spectrayan.spector.synapse.connector.service.CredentialService;
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

/**
 * REST API presentation layer for managing encrypted credentials, BYOK keys,
 * channel tokens, and enterprise vault configurations.
 *
 * <p>Delegates domain business logic, cryptographic operations, and persistence
 * to {@link CredentialService}.</p>
 */
@RestController
@RequestMapping("/api/v1/credentials")
@Validated
public class CredentialController {

    private static final Logger log = LoggerFactory.getLogger(CredentialController.class);

    private final CredentialService credentialService;

    public CredentialController(CredentialService credentialService) {
        this.credentialService = Objects.requireNonNull(credentialService, "CredentialService must not be null");
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CredentialResponse createCredential(
            @RequestHeader(value = "X-Tenant-ID", defaultValue = "default") String tenantId,
            @Valid @RequestBody CreateCredentialRequest request,
            Authentication authentication) {

        String userId = authentication != null ? authentication.getName() : null;
        log.info("[CredentialAPI] REST create credential '{}' for tenant '{}'", request.name(), tenantId);

        CredentialRecord record = credentialService.createCredential(tenantId, userId, request);
        return CredentialResponse.fromRecord(record);
    }

    @GetMapping
    public List<CredentialResponse> listCredentials(
            @RequestHeader(value = "X-Tenant-ID", defaultValue = "default") String tenantId,
            @RequestParam(required = false) String userId) {

        return credentialService.listCredentials(tenantId, userId).stream()
                .map(CredentialResponse::fromRecord)
                .toList();
    }

    @GetMapping("/{name}")
    public ResponseEntity<CredentialResponse> getCredential(
            @RequestHeader(value = "X-Tenant-ID", defaultValue = "default") String tenantId,
            @PathVariable String name) {

        return credentialService.getCredential(tenantId, name)
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

        String userId = authentication != null ? authentication.getName() : null;
        return credentialService.updateCredential(tenantId, userId, name, request)
                .map(CredentialResponse::fromRecord)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{name}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> deleteCredential(
            @RequestHeader(value = "X-Tenant-ID", defaultValue = "default") String tenantId,
            @PathVariable String name) {

        boolean deleted = credentialService.deleteCredential(tenantId, name);
        if (!deleted) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{name}/test")
    public ResponseEntity<Map<String, Object>> testCredential(
            @RequestHeader(value = "X-Tenant-ID", defaultValue = "default") String tenantId,
            @PathVariable String name) {

        Map<String, Object> result = credentialService.testCredential(tenantId, name);
        if ("NOT_FOUND".equals(result.get("status"))) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(result);
    }
}
