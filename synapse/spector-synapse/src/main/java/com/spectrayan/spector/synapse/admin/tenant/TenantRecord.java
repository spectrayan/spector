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
package com.spectrayan.spector.synapse.admin.tenant;

import java.time.Instant;

/**
 * Persistent catalog record representing a tenant in the {@code tenants} table (ADR-0087, Issue #1055).
 */
public record TenantRecord(
        String tenantId,
        String name,
        String description,
        TenantStatus status,
        String plan,
        boolean legalHold,
        boolean byokEnabled,
        boolean breakGlassApproverRequired,
        int breakGlassMaxDurationMinutes,
        int maxAccounts,
        int maxNamespaces,
        long maxStorageBytes,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt
) {
}
