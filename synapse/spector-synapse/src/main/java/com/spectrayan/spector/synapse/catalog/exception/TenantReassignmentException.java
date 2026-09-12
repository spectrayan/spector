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
package com.spectrayan.spector.synapse.catalog.exception;

import com.spectrayan.spector.commons.error.ErrorCode;

/**
 * Thrown when an attempt is made to reassign an account's tenantId while the account owns
 * namespaces (ADR-0034 D4, Req R2.6). Data movement across tenant trees is deferred to
 * ADR-0034 Phase 4, where the namespace mover is needed for failover anyway.
 */
public class TenantReassignmentException extends NamespaceException {

    private final String accountId;
    private final String currentTenantId;
    private final String targetTenantId;

    public TenantReassignmentException(String accountId, String currentTenantId, String targetTenantId) {
        super(ErrorCode.API_CONFLICT, "TENANT_REASSIGNMENT_REFUSED",
                "Cannot reassign tenant for account %s from '%s' to '%s': account owns namespaces. Data movement is deferred to ADR-0034 Phase 4."
                        .formatted(accountId, currentTenantId, targetTenantId));
        this.accountId = accountId;
        this.currentTenantId = currentTenantId;
        this.targetTenantId = targetTenantId;
    }

    public String accountId() {
        return accountId;
    }

    public String currentTenantId() {
        return currentTenantId;
    }

    public String targetTenantId() {
        return targetTenantId;
    }
}
