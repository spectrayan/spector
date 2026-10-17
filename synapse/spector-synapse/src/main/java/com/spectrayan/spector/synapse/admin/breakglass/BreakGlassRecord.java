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
package com.spectrayan.spector.synapse.admin.breakglass;

import java.time.Instant;

/**
 * Persistent record representing a row in {@code break_glass_requests} (ADR-0087 §5.4, Issue #1060).
 */
public record BreakGlassRecord(
        String id,
        String tenantId,
        String namespaceId,
        String ownerAccountId,
        String requesterAccountId,
        String requesterRole,
        String justification,
        String ticketRef,
        int durationMinutes,
        BreakGlassStatus status,
        String approverAccountId,
        String approvalComment,
        String grantId,
        Instant createdAt,
        Instant approvedAt,
        Instant expiresAt,
        Instant revokedAt,
        String revokedBy,
        String revokeReason
) {
    /**
     * Returns {@code true} if the session is marked {@link BreakGlassStatus#ACTIVE} and its
     * expiry time has passed relative to {@code now}.
     */
    public boolean isOverdue(Instant now) {
        return status == BreakGlassStatus.ACTIVE
                && expiresAt != null
                && !now.isBefore(expiresAt);
    }
}
