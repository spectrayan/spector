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
 * Thrown when an emergency break-glass access request or approval targets a namespace
 * belonging to a BYOK-enabled tenant (ADR-0087 §5.4, Issue #1060).
 *
 * <p>BYOK tenants have customer-controlled encryption keys; break-glass access is unconditionally
 * prohibited regardless of whether the caller holds {@code admin} or {@code super-admin}.</p>
 */
public class BreakGlassByokProhibitedException extends SynapseException {

    /** Unified taxonomy alias code for BYOK break-glass prohibition. */
    public static final String ERROR_CODE_ALIAS = "SPE-SEC-004";

    /** Semantic policy code specified in ADR-0087 §5.4 / Issue #1060. */
    public static final String BYOK_POLICY_CODE = "SPE-SEC-BYOK-NO-BREAK-GLASS";

    private final String tenantId;
    private final String namespaceId;

    public BreakGlassByokProhibitedException(String tenantId, String namespaceId) {
        super(ErrorCode.BREAK_GLASS_BYOK_PROHIBITED, tenantId, namespaceId);
        this.tenantId = tenantId != null ? tenantId : "default";
        this.namespaceId = namespaceId != null ? namespaceId : "unknown";
    }

    @Override
    public String getMessage() {
        return "[" + ErrorCode.BREAK_GLASS_BYOK_PROHIBITED.id() + " / " + ERROR_CODE_ALIAS + " / " + BYOK_POLICY_CODE
                + "] Break-glass access is prohibited for BYOK-enabled tenant '"
                + tenantId + "' (namespace '" + namespaceId + "')";
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getNamespaceId() {
        return namespaceId;
    }
}
