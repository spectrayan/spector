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
package com.spectrayan.spector.synapse.catalog;

import java.util.List;
import java.util.Optional;

/**
 * Service provider interface for the catalog plane (ADR-0029 §6). Manages accounts,
 * namespace records, grants, and access authorization. The default production implementation
 * is JDBC-backed ({@code JdbcAccountCatalog}). A file-backed implementation exists for
 * legacy/test purposes only and should not be used at runtime. Both share the same interface
 * and invariants: one OWNER per namespace, slug unique per account, default namespaceId
 * equals accountId.
 */
public interface AccountCatalog {

    Account getOrCreateAccount(String accountId);

    Account getAccount(String accountId);

    default NamespaceRecord createNamespace(String accountId, String slug, NamespaceType type) {
        return createNamespace(accountId, slug, type, null, null, null);
    }

    NamespaceRecord createNamespace(String accountId, String slug, NamespaceType type,
            String displayName, String description, NamespaceBias bias);

    Optional<NamespaceRecord> resolve(String accountId, String slugOrId);

    List<NamespaceRecord> listAccessible(String accountId);

    void setDefaultNamespace(String accountId, String namespaceId);

    void addGrant(Grant grant);

    void revokeGrant(String grantId);

    Optional<Grant> authorize(String accountId, String namespaceId, GrantRole minimum);

    boolean authorizeIdentity(String accountId, String bundleId, String regionId, GrantAction action);

    NamespaceRecord updateNamespace(String accountId, String slugOrId,
            String displayName, String description, NamespaceType type, NamespaceBias bias);

    void resetNamespace(String accountId, String slugOrId);

    java.util.List<Grant> listGrants(String accountId, String slugOrId);

    Grant grantNamespace(String callerAccountId, String slugOrId, String granteeAccountId,
            GrantRole role, java.time.Instant expiresAt, GrantConstraints constraints);

    void revokeNamespaceGrant(String callerAccountId, String slugOrId, String grantId);

    NamespaceRecord setLegalHold(String accountId, String slugOrId, boolean legalHold);

    void tombstone(String accountId, String namespaceId);

    void recordAccess(String namespaceId);

    /**
     * Catalog-authoritative org membership for {@code accountId} (ADR-0029 Q17).
     * Token {@code org} may only narrow this set.
     */
    default java.util.List<String> orgUnitIdsForAccount(String accountId) {
        return java.util.List.of();
    }
}
