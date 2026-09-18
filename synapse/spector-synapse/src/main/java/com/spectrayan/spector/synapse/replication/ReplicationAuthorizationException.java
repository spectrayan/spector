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
package com.spectrayan.spector.synapse.replication;

/**
 * Thrown when a replication peer fails authorization or attempts to ship a snapshot
 * for a tenant not in the replica's allow-list (Req R6.3, R6.5).
 */
public class ReplicationAuthorizationException extends RuntimeException {

    public static final String SANITIZED_PEER_MESSAGE = "UNAUTHORIZED: Peer is not authorized to replicate to this replica";

    public ReplicationAuthorizationException(String message) {
        super(message);
    }

    public ReplicationAuthorizationException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Factory for sanitized exception suitable for returning across the network wire to peers
     * without leaking tenant identifiers (Req R6.5).
     *
     * @return sanitized authorization exception
     */
    public static ReplicationAuthorizationException sanitized() {
        return new ReplicationAuthorizationException(SANITIZED_PEER_MESSAGE);
    }
}
