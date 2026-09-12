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
 * Immutable descriptor for an elected cell coordinator lease.
 *
 * <p>Lease expiration is evaluated against store-provided timestamps or explicit instant checks,
 * enforcing store-enforced expiration (Req R1.2, R1.5).</p>
 *
 * @param holderNodeId node identifier currently holding the lease
 * @param acquiredAt   timestamp when the lease was acquired or renewed
 * @param expiresAt    timestamp when the lease expires
 * @param leaseVersion monotonic lease transition counter
 */
public record CoordinatorLease(
        String holderNodeId,
        Instant acquiredAt,
        Instant expiresAt,
        long leaseVersion) {

    public CoordinatorLease {
        Objects.requireNonNull(holderNodeId, "holderNodeId must not be null");
        Objects.requireNonNull(acquiredAt, "acquiredAt must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        if (expiresAt.isBefore(acquiredAt)) {
            throw new IllegalArgumentException("expiresAt (" + expiresAt + ") cannot be before acquiredAt (" + acquiredAt + ")");
        }
    }

    /**
     * Checks if this lease has expired relative to the given timestamp.
     *
     * @param now current timestamp to compare against
     * @return {@code true} if expired; {@code false} otherwise
     */
    public boolean isExpired(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        return now.isAfter(expiresAt) || now.equals(expiresAt);
    }

    /**
     * Checks if this lease is currently held by the specified node and not expired.
     *
     * @param nodeId node identifier to check
     * @param now    current timestamp
     * @return {@code true} if actively held by nodeId; {@code false} otherwise
     */
    public boolean isHeldBy(String nodeId, Instant now) {
        return !isExpired(now) && Objects.equals(this.holderNodeId, nodeId);
    }
}
