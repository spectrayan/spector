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
package com.spectrayan.spector.synapse.memory;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.spectrayan.spector.synapse.catalog.*;

/**
 * Passthrough catalog that maps {@code accountId → defaultNamespaceId = accountId}
 * without writing any catalog files. Used for backward compatibility in tests and
 * when the catalog plane is not yet initialized.
 *
 * <p>This produces identical behavior to the pre-Phase-1 {@code MemoryRegistry}:
 * every accountId resolves to a SpectorMemory at
 * {@code StorageLayout.namespaceDirSharded(basePath, accountId)}.</p>
 */
final class PassthroughCatalog implements AccountCatalog {

    @Override
    public Account getOrCreateAccount(String accountId) {
        return new Account(
                accountId,
                PrincipalKind.HUMAN,
                AccountProfile.HUMAN_SOLO,
                null,
                AccountQuotas.forProfile(AccountProfile.HUMAN_SOLO),
                AccountFlags.forProfile(AccountProfile.HUMAN_SOLO),
                accountId,  // defaultNamespaceId == accountId
                Instant.now()
        );
    }

    @Override
    public Account getAccount(String accountId) {
        return getOrCreateAccount(accountId);
    }

    @Override
    public NamespaceRecord createNamespace(String accountId, String slug, NamespaceType type) {
        throw new UnsupportedOperationException("PassthroughCatalog does not support createNamespace");
    }

    @Override
    public Optional<NamespaceRecord> resolve(String accountId, String slugOrId) {
        return Optional.of(new NamespaceRecord(
                accountId, "default", accountId, NamespaceType.DEFAULT,
                NamespaceStatus.ACTIVE, null, null, null, Instant.now(), Instant.now()
        ));
    }

    @Override
    public List<NamespaceRecord> listAccessible(String accountId) {
        return List.of(new NamespaceRecord(
                accountId, "default", accountId, NamespaceType.DEFAULT,
                NamespaceStatus.ACTIVE, null, null, null, Instant.now(), Instant.now()
        ));
    }

    @Override
    public void setDefaultNamespace(String accountId, String namespaceId) {
        // no-op
    }

    @Override
    public void addGrant(Grant grant) {
        // no-op
    }

    @Override
    public void revokeGrant(String grantId) {
        // no-op
    }

    @Override
    public Optional<Grant> authorize(String accountId, String namespaceId, GrantRole minimumRole) {
        return Optional.empty();
    }

    @Override
    public boolean authorizeIdentity(String accountId, String bundleId, String regionId, GrantAction action) {
        return false;
    }

    @Override
    public void tombstone(String accountId, String namespaceId) {
        // no-op
    }

    @Override
    public void recordAccess(String namespaceId) {
        // no-op
    }
}
