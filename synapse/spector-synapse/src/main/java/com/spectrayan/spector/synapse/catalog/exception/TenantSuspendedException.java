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
import com.spectrayan.spector.synapse.error.SynapseException;

/**
 * Thrown when an authentication or API operation is attempted by a principal belonging
 * to a suspended or deleted tenant (ADR-0070, ADR-0087, Issue #1055).
 */
public class TenantSuspendedException extends SynapseException {

    /** Unified taxonomy alias code for suspended tenant rejection. */
    public static final String ERROR_CODE_ALIAS = "SPE-SEC-003";

    private final String tenantId;

    public TenantSuspendedException(String tenantId) {
        super(ErrorCode.TENANT_SUSPENDED, tenantId);
        this.tenantId = tenantId != null ? tenantId : "default";
    }

    @Override
    public String getMessage() {
        return "[" + ErrorCode.TENANT_SUSPENDED.id() + " / " + ERROR_CODE_ALIAS + "] Tenant '"
                + tenantId + "' is suspended — authentication and access are blocked";
    }

    public String getTenantId() {
        return tenantId;
    }
}
