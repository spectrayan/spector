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
 * Thrown when a break-glass request violates dual-control approval rules (e.g. self-approval),
 * has expired or been revoked, or attempts a forbidden write/export/MCP operation during an
 * active read-only break-glass session (ADR-0087 §5.4, Issue #1060).
 */
public class BreakGlassAccessDeniedException extends NamespaceAccessDeniedException {

    /** Unified taxonomy alias code for break-glass access denial. */
    public static final String ERROR_CODE_ALIAS = "SPE-SEC-005";

    private final String reason;

    public BreakGlassAccessDeniedException(String namespaceId, String principalId, String reason) {
        super(namespaceId != null ? namespaceId : "unknown", principalId != null ? principalId : "unknown");
        this.reason = reason != null ? reason : "Break-glass access denied";
    }

    @Override
    public ErrorCode errorCode() {
        return ErrorCode.BREAK_GLASS_ACCESS_DENIED;
    }

    @Override
    public String getMessage() {
        return "[" + ErrorCode.BREAK_GLASS_ACCESS_DENIED.id() + " / " + ERROR_CODE_ALIAS + "] Break-glass access denied: " + reason;
    }

    public String getReason() {
        return reason;
    }
}
