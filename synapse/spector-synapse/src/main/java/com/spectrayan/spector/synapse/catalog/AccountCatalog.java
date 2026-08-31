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

    /**
     * Gets an existing account or creates a new one if it does not exist for the given account ID.
     *
     * @param accountId the account identifier
     * @return the existing or newly created account
     */
    Account getOrCreateAccount(String accountId);

    /**
     * Retrieves an account by its account identifier.
     *
     * @param accountId the account identifier
     * @return the account matching the identifier
     */
    Account getAccount(String accountId);

    /**
     * Creates a new namespace record for the given account with the specified slug and type.
     *
     * @param accountId the account identifier
     * @param slug      the human-readable slug for the namespace, unique within the account
     * @param type      the namespace type
     * @return the newly created namespace record
     */
    default NamespaceRecord createNamespace(String accountId, String slug, NamespaceType type) {
        return createNamespace(accountId, slug, type, null, null, null);
    }

    /**
     * Creates a new namespace record for the given account with full initial metadata.
     *
     * @param accountId   the account identifier
     * @param slug        the human-readable slug for the namespace, unique within the account
     * @param type        the namespace type
     * @param displayName human-readable display name, or null
     * @param description human-readable description, or null
     * @param bias        domain bias overlay, or null
     * @return the newly created namespace record
     */
    NamespaceRecord createNamespace(String accountId, String slug, NamespaceType type,
            String displayName, String description, NamespaceBias bias);

    /**
     * Resolves a namespace record by account ID and slug or namespace ID.
     *
     * @param accountId the account identifier
     * @param slugOrId  the namespace slug or namespace identifier
     * @return an {@link Optional} containing the namespace record if resolved, or empty otherwise
     */
    Optional<NamespaceRecord> resolve(String accountId, String slugOrId);

    /**
     * Lists all accessible namespace records for a given account.
     *
     * @param accountId the account identifier
     * @return a list of accessible namespace records
     */
    List<NamespaceRecord> listAccessible(String accountId);

    /**
     * Sets the default namespace ID for the specified account.
     *
     * @param accountId   the account identifier
     * @param namespaceId the namespace identifier to set as default
     */
    void setDefaultNamespace(String accountId, String namespaceId);

    /**
     * Adds an access grant.
     *
     * @param grant the grant to add
     */
    void addGrant(Grant grant);

    /**
     * Revokes a grant by its grant identifier.
     *
     * @param grantId the identifier of the grant to revoke
     */
    void revokeGrant(String grantId);

    /**
     * Checks authorization for an account on a namespace requiring at least the minimum grant role.
     *
     * @param accountId   the account identifier
     * @param namespaceId the namespace identifier
     * @param minimum     the minimum grant role required
     * @return an {@link Optional} containing the matching {@link Grant} if authorized, or empty otherwise
     */
    Optional<Grant> authorize(String accountId, String namespaceId, GrantRole minimum);

    /**
     * Checks authorization for an account on an identity bundle and region for a specific grant action.
     *
     * @param accountId the account identifier
     * @param bundleId  the identity bundle identifier
     * @param regionId  the region identifier
     * @param action    the grant action to check
     * @return {@code true} if authorized; {@code false} otherwise
     */
    boolean authorizeIdentity(String accountId, String bundleId, String regionId, GrantAction action);

    /**
     * Updates an existing namespace record's mutable metadata (display name, description, type, bias).
     *
     * @param accountId   the account identifier
     * @param slugOrId    the namespace slug or namespace identifier
     * @param displayName the new display name, or null to keep/clear
     * @param description the new description, or null to keep/clear
     * @param type        the new namespace type, or null to keep existing
     * @param bias        the new namespace bias, or null to keep/clear
     * @return the updated namespace record
     */
    NamespaceRecord updateNamespace(String accountId, String slugOrId,
            String displayName, String description, NamespaceType type, NamespaceBias bias);

    /**
     * Resets a namespace by clearing its memory state while preserving catalog registration.
     * Allowed on default and non-default namespaces.
     *
     * @param accountId the account identifier
     * @param slugOrId  the namespace slug or namespace identifier
     */
    void resetNamespace(String accountId, String slugOrId);

    /**
     * Lists all active grants on a namespace. Requires caller to have at least {@link GrantRole#ADMIN} on the namespace.
     *
     * @param accountId the calling account identifier
     * @param slugOrId  the namespace slug or namespace identifier
     * @return list of active grants on the namespace
     */
    java.util.List<Grant> listGrants(String accountId, String slugOrId);

    /**
     * Creates or updates a grant on a namespace. Requires caller to have at least {@link GrantRole#ADMIN} on the namespace.
     *
     * @param callerAccountId  the calling account identifier
     * @param slugOrId         the namespace slug or namespace identifier
     * @param granteeAccountId the account identifier receiving the grant
     * @param role             the grant role being granted (READER, WRITER, ADMIN)
     * @param expiresAt        optional expiration timestamp, or null for no expiry
     * @param constraints      optional grant constraints, nullable
     * @return the created {@link Grant}
     */
    Grant grantNamespace(String callerAccountId, String slugOrId, String granteeAccountId,
            GrantRole role, java.time.Instant expiresAt, GrantConstraints constraints);

    /**
     * Revokes a grant on a namespace. Requires caller to have at least {@link GrantRole#ADMIN} on the namespace.
     *
     * @param callerAccountId the calling account identifier
     * @param slugOrId        the namespace slug or namespace identifier
     * @param grantId         the grant identifier to revoke
     */
    void revokeNamespaceGrant(String callerAccountId, String slugOrId, String grantId);

    /**
     * Toggles legal hold status for a namespace. When legal hold is true, the namespace
     * cannot be deleted, tombstoned, reset, or purged (ADR-0029 §19, §23). Requires at least ADMIN on the namespace.
     *
     * @param accountId calling account identifier
     * @param slugOrId  namespace slug or identifier
     * @param legalHold true to place under legal hold, false to release
     * @return updated namespace record
     */
    NamespaceRecord setLegalHold(String accountId, String slugOrId, boolean legalHold);

    /**
     * Marks a namespace as tombstoned (soft-deleted) for an account.
     *
     * @param accountId   the account identifier
     * @param namespaceId the namespace identifier to tombstone
     */
    void tombstone(String accountId, String namespaceId);

    /**
     * Records access for the given namespace ID, updating last-accessed timestamps and metrics.
     *
     * @param namespaceId the namespace identifier
     */
    void recordAccess(String namespaceId);
}
