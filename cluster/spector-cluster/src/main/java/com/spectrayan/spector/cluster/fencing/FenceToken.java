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
package com.spectrayan.spector.cluster.fencing;

import java.util.Objects;

/**
 * Immutable descriptor for a monotonic per-namespace fencing token (Req R2.1, Q1, Q3).
 *
 * <p>Advanced only by the authoritative control store; validation is a local integer/string comparison (Req §5).</p>
 *
 * @param namespaceId namespace identifier this fence protects
 * @param epoch       monotonic epoch of this fence
 * @param fenceNumber monotonic sequence number
 */
public record FenceToken(String namespaceId, long epoch, long fenceNumber) {

    public FenceToken {
        Objects.requireNonNull(namespaceId, "namespaceId must not be null");
        if (epoch < 0) {
            throw new IllegalArgumentException("epoch cannot be negative: " + epoch);
        }
        if (fenceNumber < 0) {
            throw new IllegalArgumentException("fenceNumber cannot be negative: " + fenceNumber);
        }
    }

    /**
     * Factory creating a fence token where the fence number tracks the namespace epoch.
     *
     * @param namespaceId namespace identifier
     * @param epoch       epoch value
     * @return fence token
     */
    public static FenceToken of(String namespaceId, long epoch) {
        return new FenceToken(namespaceId, epoch, epoch);
    }

    /**
     * Formats the fence token into its canonical wire string representation.
     *
     * @return wire string representation
     */
    public String toTokenString() {
        return String.valueOf(epoch);
    }

    /**
     * Tests if an incoming wire string matches this fence token.
     *
     * @param tokenString incoming string to test
     * @return {@code true} if matching; {@code false} otherwise
     */
    public boolean matches(String tokenString) {
        if (tokenString == null || tokenString.isBlank()) {
            return false;
        }
        return Objects.equals(toTokenString(), tokenString.trim());
    }
}
