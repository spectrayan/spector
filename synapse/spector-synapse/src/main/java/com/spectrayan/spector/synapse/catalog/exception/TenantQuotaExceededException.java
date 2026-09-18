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
 * Thrown when an operation exceeds a tenant-level quota.
 */
public class TenantQuotaExceededException extends NamespaceException {

    private final String tenantId;
    private final String detail;

    /**
     * Creates a new tenant quota exceeded exception.
     *
     * @param tenantId the identifier of the tenant
     * @param detail   details regarding the exceeded quota
     */
    public TenantQuotaExceededException(String tenantId, String detail) {
        super(ErrorCode.TENANT_QUOTA_EXCEEDED, "TenantQuotaExceeded", tenantId, detail);
        this.tenantId = tenantId;
        this.detail = detail;
    }

    /**
     * Gets the tenant identifier.
     *
     * @return the tenant ID
     */
    public String getTenantId() {
        return tenantId;
    }

    /**
     * Gets the quota violation details.
     *
     * @return the violation detail message
     */
    public String getDetail() {
        return detail;
    }
}
