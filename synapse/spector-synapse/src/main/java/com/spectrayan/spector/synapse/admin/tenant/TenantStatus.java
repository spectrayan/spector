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

/**
 * Lifecycle state of a tenant in the platform catalog (ADR-0087, Issue #1055).
 */
public enum TenantStatus {

    /** Normal operational state; principals can authenticate and access namespaces. */
    ACTIVE,

    /** Administratively suspended; principals belonging to this tenant are blocked from authenticating and operating. */
    SUSPENDED,

    /** Soft-deleted tenant; permanently deactivated pending data retention expiry. */
    DELETED;

    /**
     * Parses a status string case-insensitively, defaulting to {@link #ACTIVE} when null or blank.
     */
    public static TenantStatus fromString(String value) {
        if (value == null || value.isBlank()) {
            return ACTIVE;
        }
        return TenantStatus.valueOf(value.trim().toUpperCase());
    }
}
