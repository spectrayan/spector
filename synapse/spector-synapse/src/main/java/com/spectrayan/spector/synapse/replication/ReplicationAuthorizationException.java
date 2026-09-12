/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-synapse/LICENSE
 *
 * Change Date: July 6, 2030
 * Change License: Apache License, Version 2.0
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
