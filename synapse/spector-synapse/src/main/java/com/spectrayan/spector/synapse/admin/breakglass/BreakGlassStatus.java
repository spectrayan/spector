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

/**
 * Lifecycle states of an emergency break-glass namespace access session (ADR-0087 §5.4, Issue #1060).
 */
public enum BreakGlassStatus {
    /** Request has been submitted and awaits approval by a second distinct administrator. */
    PENDING_APPROVAL,
    /** Approved and currently active (`now < expiresAt`); time-boxed read-only grant is in effect. */
    ACTIVE,
    /** Request was rejected prior to activation. */
    REJECTED,
    /** Session was manually revoked by an administrator, the requester, or the namespace owner. */
    REVOKED,
    /** Session reached its `expiresAt` timestamp and was automatically revoked. */
    EXPIRED;

    public static BreakGlassStatus fromString(String value) {
        if (value == null || value.isBlank()) {
            return PENDING_APPROVAL;
        }
        try {
            return BreakGlassStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return PENDING_APPROVAL;
        }
    }
}
