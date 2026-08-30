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
 * Service provider interface for the catalog plane. Manages accounts, namespace records,
 * grants, and access authorization. OSS uses a file-backed implementation (account.json,
 * slugs.json, grants.jsonl). Enterprise uses a DB-backed implementation. Both share the
 * same interface and invariants: one OWNER per namespace, slug unique per account, default
 * namespaceId equals accountId.
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
    NamespaceRecord createNamespace(String accountId, String slug, NamespaceType type);

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
