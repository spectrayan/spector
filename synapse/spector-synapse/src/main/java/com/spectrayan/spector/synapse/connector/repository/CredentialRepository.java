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
package com.spectrayan.spector.synapse.connector.repository;

import com.spectrayan.spector.synapse.connector.model.CredentialRecord;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Data access repository for persisted credentials.
 */
public interface CredentialRepository {

    /**
     * Persists or updates a credential entity in the database.
     */
    CredentialRecord save(CredentialRecord record);

    /**
     * Finds a credential by its tenant-scoped user display name.
     */
    Optional<CredentialRecord> findByName(String tenantId, String name);

    /**
     * Finds the default credential for a provider within a tenant.
     */
    Optional<CredentialRecord> findDefaultByProvider(String tenantId, String provider);

    /**
     * Lists all credentials for a tenant.
     */
    List<CredentialRecord> findByTenantId(String tenantId);

    /**
     * Lists all credentials for a specific user within a tenant.
     */
    List<CredentialRecord> findByUserId(String tenantId, String userId);

    /**
     * Clears the default flag for a specific provider in a tenant.
     */
    void clearDefault(String tenantId, String provider);

    /**
     * Deletes a credential by tenant and name.
     */
    boolean deleteByName(String tenantId, String name);

    /**
     * Updates the last used timestamp for a credential.
     */
    void updateLastUsedAt(String credentialId, Instant timestamp);
}
