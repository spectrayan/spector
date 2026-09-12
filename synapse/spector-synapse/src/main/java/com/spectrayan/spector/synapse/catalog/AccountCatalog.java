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

    default Account getOrCreateAccount(String accountId, AccountProfile profile, PrincipalKind kind) {
        return getOrCreateAccount(accountId, profile, kind, null);
    }

    default Account getOrCreateAccount(String accountId, AccountProfile profile, PrincipalKind kind, String tenantId) {
        return getOrCreateAccount(accountId);
    }

    Account getAccount(String accountId);

    default void assignTenant(String accountId, String tenantId) {}

    default void addOrgMember(String accountId, String orgUnitId) {}

    default void removeOrgMember(String accountId, String orgUnitId) {}

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

    /**
     * Lists all accounts known to the catalog.
     *
     * @return list of all accounts
     */
    default List<Account> listAccounts() {
        return List.of();
    }

    /**
     * Lists all accounts that have an associated non-blank tenant ID.
     *
     * @return list of tenanted accounts
     */
    default List<Account> listTenantedAccounts() {
        return listAccounts().stream()
                .filter(a -> a.tenantId() != null && !a.tenantId().isBlank())
                .toList();
    }

    /**
     * Lists every namespace owned by {@code accountId}, <strong>including tombstoned ones</strong>.
     *
     * <p>Distinct from {@link #listAccessible(String)}, which is an authorization view: it excludes
     * tombstoned namespaces and includes namespaces the account merely holds a grant on. Neither
     * property is wanted when reasoning about files on disk.</p>
     *
     * <p>A tombstoned namespace still has bundle files, and no tombstone garbage collector exists, so
     * migration and tenant-prefix wipe must be able to see it. Skipping them left their directories
     * on the flat layout, outside the tenant prefix, where a tenant wipe would never reach them
     * (Req R9.1).</p>
     *
     * <p>The default implementation degrades to the authorization view for catalogs that have not
     * overridden it, and therefore cannot see tombstoned namespaces.</p>
     *
     * @param accountId the owning account
     * @return every namespace owned by that account, whatever its status
     */
    default List<NamespaceRecord> listOwnedNamespaces(String accountId) {
        return listAccessible(accountId).stream()
                .filter(r -> accountId.equals(r.ownerAccountId()))
                .toList();
    }
}

