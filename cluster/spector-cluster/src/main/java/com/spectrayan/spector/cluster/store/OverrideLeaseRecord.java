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
package com.spectrayan.spector.cluster.store;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable record representing an explicit namespace override lease in the control store.
 *
 * <p>Pins a namespace to a specific target node, overriding consistent hash ring resolution (Req R3.1).
 * Carries an expiration timestamp to prevent permanent topological drift (Req R3.3).</p>
 *
 * @param namespaceId target namespace identifier
 * @param targetNodeId node identifier designated as the authoritative owner
 * @param epoch       monotonic epoch of this override
 * @param fence       fence token string associated with this override
 * @param expiresAt   timestamp when this override expires
 * @param createdAt   timestamp when this override was created
 */
public record OverrideLeaseRecord(
        String namespaceId,
        String targetNodeId,
        long epoch,
        String fence,
        Instant expiresAt,
        Instant createdAt) {

    public OverrideLeaseRecord(
            String namespaceId,
            String targetNodeId,
            long epoch,
            String fence,
            Instant expiresAt) {
        this(namespaceId, targetNodeId, epoch, fence, expiresAt, Instant.now());
    }

    public OverrideLeaseRecord {
        Objects.requireNonNull(namespaceId, "namespaceId must not be null");
        Objects.requireNonNull(targetNodeId, "targetNodeId must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (namespaceId.isBlank()) {
            throw new IllegalArgumentException("namespaceId must not be blank");
        }
        if (targetNodeId.isBlank()) {
            throw new IllegalArgumentException("targetNodeId must not be blank");
        }
    }

    /**
     * Checks if this override lease has expired.
     *
     * @param now current timestamp to check against
     * @return {@code true} if expired; {@code false} if still active
     */
    public boolean isExpired(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        return now.isAfter(expiresAt) || now.equals(expiresAt);
    }

    /**
     * Returns the elapsed age of this override lease since its creation (Req R3.6).
     *
     * @param now current timestamp to calculate age against
     * @return duration elapsed since creation
     */
    public java.time.Duration age(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        return java.time.Duration.between(createdAt, now);
    }
}
