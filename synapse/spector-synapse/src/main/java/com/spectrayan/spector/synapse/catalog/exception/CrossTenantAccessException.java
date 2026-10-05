/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.spectrayan.spector.synapse.catalog.exception;

import com.spectrayan.spector.commons.error.ErrorCode;

/**
 * Thrown when an operation attempts to cross tenant isolation boundaries (ADR-0070).
 */
public class CrossTenantAccessException extends NamespaceAccessDeniedException {

    /** Unified taxonomy alias code for security violation per issue #1048. */
    public static final String ERROR_CODE_ALIAS = "SPE-SEC-001";

    private final String targetTenantId;

    public CrossTenantAccessException(String accountId, String namespaceId, String targetTenantId) {
        super(namespaceId, accountId);
        this.targetTenantId = targetTenantId;
    }

    @Override
    public ErrorCode errorCode() {
        return ErrorCode.CROSS_TENANT_ACCESS_DENIED;
    }

    @Override
    public String getMessage() {
        return "[" + ErrorCode.CROSS_TENANT_ACCESS_DENIED.id() + "] Cross-tenant access denied: account '"
                + getPrincipalId() + "' cannot access namespace '" + getNamespaceId()
                + "' belonging to tenant '" + targetTenantId + "'";
    }

    public String getAccountId() {
        return getPrincipalId();
    }

    public String getTargetTenantId() {
        return targetTenantId;
    }
}
