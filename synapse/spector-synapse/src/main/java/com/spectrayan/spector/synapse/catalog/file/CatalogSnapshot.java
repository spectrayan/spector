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
package com.spectrayan.spector.synapse.catalog.file;

import com.spectrayan.spector.synapse.catalog.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Immutable read snapshot of one account's catalog state.
 * Cached with mtime invalidation.
 *
 * @param account           the account
 * @param slugToNamespaceId immutable map from slugs.json
 * @param liveGrants        parsed from grants.jsonl, only live/non-expired
 * @param mtimeNanos        file modification time for invalidation
 */
public record CatalogSnapshot(
        Account account,
        Map<String, String> slugToNamespaceId,
        List<Grant> liveGrants,
        long mtimeNanos) {

    public CatalogSnapshot {
        slugToNamespaceId = Map.copyOf(slugToNamespaceId);
        liveGrants = List.copyOf(liveGrants);
    }

    /**
     * Resolves a slug or namespace ID to a namespace ID.
     *
     * @param slugOrId the slug or namespace ID
     * @return the namespace ID, or empty if not found
     */
    public Optional<String> resolveSlug(String slugOrId) {
        if (slugToNamespaceId.containsKey(slugOrId)) {
            return Optional.of(slugToNamespaceId.get(slugOrId));
        }
        if (slugToNamespaceId.containsValue(slugOrId)) {
            return Optional.of(slugOrId);
        }
        return Optional.empty();
    }

    /**
     * Finds a matching grant for the given minimum role.
     *
     * @param accountId   the account ID
     * @param namespaceId the namespace ID
     * @param minimumRole the minimum required role
     * @return the matching grant, or empty if none found
     */
    public Optional<Grant> findGrant(String accountId, String namespaceId, GrantRole minimumRole) {
        if (accountId.equals(account.id()) && namespaceId.equals(account.defaultNamespaceId())) {
            return Optional.of(new Grant(
                    accountId + "-owner-default",
                    GrantObjectType.NAMESPACE,
                    namespaceId,
                    accountId,
                    PrincipalType.ACCOUNT,
                    GrantRole.OWNER,
                    Set.of(GrantAction.READ, GrantAction.WRITE, GrantAction.ADMIN),
                    accountId,
                    Instant.now(),
                    null,
                    null
            ));
        }

        for (Grant grant : liveGrants) {
            if (grant.objectId().equals(namespaceId) && grant.principalId().equals(accountId)) {
                if (grant.role().isAtLeast(minimumRole)) {
                    return Optional.of(grant);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Returns a list of accessible namespace records for the given account.
     *
     * @param accountId the account ID
     * @return list of accessible namespace records
     */
    public List<NamespaceRecord> accessibleNamespaces(String accountId) {
        List<NamespaceRecord> namespaces = new ArrayList<>();
        Set<String> accessibleIds = new HashSet<>();

        if (accountId.equals(account.id()) && account.defaultNamespaceId() != null) {
            accessibleIds.add(account.defaultNamespaceId());
        }

        for (Grant grant : liveGrants) {
            if (grant.principalId().equals(accountId) && grant.objectType() == GrantObjectType.NAMESPACE) {
                accessibleIds.add(grant.objectId());
            }
        }

        for (Map.Entry<String, String> entry : slugToNamespaceId.entrySet()) {
            if (accessibleIds.contains(entry.getValue())) {
                namespaces.add(new NamespaceRecord(
                        entry.getValue(),
                        entry.getKey(),
                        account.id(),
                        NamespaceType.DEFAULT,
                        NamespaceStatus.ACTIVE,
                        null,   // displayName
                        null,   // description
                        null,   // bias
                        Instant.now(),
                        Instant.now()
                ));
            }
        }

        return namespaces;
    }
}
