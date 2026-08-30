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

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.synapse.catalog.Account;
import com.spectrayan.spector.synapse.catalog.AccountCatalog;
import com.spectrayan.spector.synapse.catalog.NamespaceRecord;
import com.spectrayan.spector.synapse.catalog.NamespaceStatus;
import com.spectrayan.spector.synapse.catalog.exception.NamespaceNotFoundException;
import com.spectrayan.spector.synapse.catalog.exception.NamespaceTombstonedException;
import com.spectrayan.spector.synapse.config.SynapseProperties;

/**
 * Shared binder that resolves an authenticated request to a {@link MemoryBinding}
 * holding the target {@link SpectorMemory} (ADR-0029 §16).
 *
 * <p>Both REST filters and MCP sessions delegate resolution to this component,
 * ensuring consistent resolution order:
 * {@code client signal (header/arg) > session default > account default}.</p>
 */
@Component
public class MemoryRequestBinder {

    private static final Logger log = LoggerFactory.getLogger(MemoryRequestBinder.class);
    private static final String DEFAULT_USER_ID = "default";

    private final AccountCatalog catalog;
    private final MemoryRegistry registry;
    private final SynapseProperties synapseProps;
    private final SpectorMemory sharedMemory;

    public MemoryRequestBinder(
            AccountCatalog catalog,
            MemoryRegistry registry,
            SynapseProperties synapseProps,
            org.springframework.beans.factory.ObjectProvider<SpectorMemory> sharedMemoryProvider) {
        this.catalog = catalog;
        this.registry = registry;
        this.synapseProps = synapseProps;
        this.sharedMemory = sharedMemoryProvider.getIfAvailable();
    }

    /**
     * Binds a memory instance for the given authentication and optional namespace selector.
     *
     * @param auth     the current authentication, or null if unauthenticated
     * @param selector optional namespace slug or namespaceId
     * @return the bound memory context
     */
    public MemoryBinding bind(Authentication auth, Optional<String> selector) {
        if (!synapseProps.auth().enabled() || auth == null || !auth.isAuthenticated()
                || auth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken) {
            return new MemoryBinding(sharedMemory, DEFAULT_USER_ID, DEFAULT_USER_ID, "default");
        }

        String accountId = auth.getName();
        if (accountId == null || accountId.isBlank() || DEFAULT_USER_ID.equals(accountId)) {
            return new MemoryBinding(sharedMemory, DEFAULT_USER_ID, DEFAULT_USER_ID, "default");
        }

        Account account = catalog.getOrCreateAccount(accountId);

        if (selector != null && selector.isPresent() && !selector.get().isBlank()) {
            String slugOrId = selector.get().trim();
            if ("*".equals(slugOrId)) {
                throw new IllegalArgumentException("Wildcard namespace '*' is not supported for memory operations");
            }
            NamespaceRecord record = catalog.resolve(accountId, slugOrId)
                    .orElseThrow(() -> new NamespaceNotFoundException(slugOrId));
            if (record.status() == NamespaceStatus.TOMBSTONED) {
                throw new NamespaceTombstonedException(record.namespaceId());
            }
            catalog.recordAccess(record.namespaceId());
            NamespaceResolver resolver = registry.namespaceResolver();
            SpectorMemory memory = resolver.resolve(accountId, record.namespaceId());
            return new MemoryBinding(memory, accountId, record.namespaceId(), record.slug());
        }

        String defaultNamespaceId = account.defaultNamespaceId();
        SpectorMemory memory = registry.resolveFor(accountId);
        return new MemoryBinding(memory, accountId, defaultNamespaceId, "default");
    }

    /**
     * Unbinds the given memory binding.
     *
     * @param binding the binding to release
     */
    public void unbind(MemoryBinding binding) {
        // Reserved for lease release in Phase 3 parity
        log.trace("[MemoryRequestBinder] unbinding memory for account={}, ns={}",
                binding != null ? binding.accountId() : null,
                binding != null ? binding.namespaceId() : null);
    }
}
