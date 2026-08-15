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

import com.spectrayan.spector.synapse.connector.api.dto.CreateCredentialRequest;
import com.spectrayan.spector.synapse.connector.api.dto.UpdateCredentialRequest;
import com.spectrayan.spector.synapse.connector.model.CredentialRecord;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Domain service managing lifecycle, business rules, encryption, and secret resolution for credentials.
 */
public interface CredentialService {

    /**
     * Creates and securely persists a new credential under tenant/user scope.
     */
    CredentialRecord createCredential(String tenantId, String userId, CreateCredentialRequest request);

    /**
     * Updates an existing credential, preserving existing secret if omitted.
     */
    Optional<CredentialRecord> updateCredential(String tenantId, String userId, String name, UpdateCredentialRequest request);

    /**
     * Retrieves credential metadata by name.
     */
    Optional<CredentialRecord> getCredential(String tenantId, String name);

    /**
     * Lists credentials for tenant or user.
     */
    List<CredentialRecord> listCredentials(String tenantId, String userId);

    /**
     * Deletes a credential and invalidates secret caches.
     */
    boolean deleteCredential(String tenantId, String name);

    /**
     * Resolves and decrypts a secret reference string for a tenant.
     */
    Optional<String> resolveSecret(String credentialRef, String tenantId);

    /**
     * Tests and probes connectivity and validity for a stored credential.
     */
    Map<String, Object> testCredential(String tenantId, String name);
}
