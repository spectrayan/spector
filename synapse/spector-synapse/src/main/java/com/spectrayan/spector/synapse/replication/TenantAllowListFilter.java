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
package com.spectrayan.spector.synapse.replication;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Enforces multi-tenant isolation on the replica by validating tenant identifiers
 * against a configured allow-list and tracking rejected frames (ADR-0034 §10.2, Req R6.3, R6.5, R11.3).
 */
public class TenantAllowListFilter {

    private static final Logger log = LoggerFactory.getLogger(TenantAllowListFilter.class);

    private final Set<String> allowedTenants;
    private final AtomicLong framesRejected = new AtomicLong(0);

    /**
     * Creates an allow-list filter with the specified set of allowed tenant IDs.
     *
     * @param allowedTenants set of permitted tenant identifiers (must not be null)
     */
    public TenantAllowListFilter(Set<String> allowedTenants) {
        Objects.requireNonNull(allowedTenants, "allowedTenants must not be null");
        this.allowedTenants = Collections.unmodifiableSet(new HashSet<>(allowedTenants));
    }

    /**
     * Checks if a tenant is permitted to replicate to this replica.
     * If unauthorized, increments {@code frames.rejected} and logs at WARN/ERROR with the tenant ID.
     *
     * @param tenantId tenant identifier to check
     * @return true if permitted, false if rejected
     */
    public boolean isAllowed(String tenantId) {
        if (tenantId != null && allowedTenants.contains(tenantId.trim())) {
            return true;
        }

        framesRejected.incrementAndGet();
        log.warn("Replication frame rejected: unauthorized tenant '{}' not in replica allow-list (total rejected={})",
                tenantId, framesRejected.get());
        return false;
    }

    /**
     * Verifies authorization for the given tenant ID.
     *
     * @param tenantId tenant identifier to verify
     * @throws ReplicationAuthorizationException if the tenant is unauthorized, with sanitized message (Req R6.5)
     */
    public void checkAuthorization(String tenantId) {
        if (!isAllowed(tenantId)) {
            // Throw sanitized exception without leaking tenantId to peer (Req R6.5)
            throw ReplicationAuthorizationException.sanitized();
        }
    }

    /**
     * Returns the total count of rejected replication frames (Req R11.3).
     *
     * @return count of rejected frames
     */
    public long getFramesRejected() {
        return framesRejected.get();
    }

    /**
     * Returns the configured allowed tenants.
     *
     * @return unmodifiable set of allowed tenants
     */
    public Set<String> getAllowedTenants() {
        return allowedTenants;
    }
}
