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

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.synapse.error.SynapseException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a replica recall request is refused because the replica's staleness exceeds
 * {@code maxReplicaLag} (ADR-0034 §10, §15.5, Invariant N7, Req R10.4).
 *
 * <p>Invariant N7: Refuse rather than serve stale recall when bound is exceeded.</p>
 */
@ResponseStatus(value = HttpStatus.PRECONDITION_FAILED, reason = "Replica freshness bound exceeded")
public class ReplicaStalenessExceededException extends SynapseException {

    private final String namespaceId;
    private final long currentLagMs;
    private final long maxLagMs;

    public ReplicaStalenessExceededException(String namespaceId, long currentLagMs, long maxLagMs) {
        super(ErrorCode.REPLICA_STALENESS_EXCEEDED, namespaceId, currentLagMs, maxLagMs);
        this.namespaceId = namespaceId;
        this.currentLagMs = currentLagMs;
        this.maxLagMs = maxLagMs;
    }

    public String namespaceId() {
        return namespaceId;
    }

    public long currentLagMs() {
        return currentLagMs;
    }

    public long maxLagMs() {
        return maxLagMs;
    }

    public String getNamespaceId() {
        return namespaceId;
    }

    public long getCurrentLagMs() {
        return currentLagMs;
    }

    public long getMaxLagMs() {
        return maxLagMs;
    }
}
