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
package com.spectrayan.spector.synapse.cluster.fencing;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.synapse.error.SynapseException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when an incoming write request carries a stale, missing, or mismatched fence token (Req R2.3, Q2).
 *
 * <p>Distinct from {@code NamespaceNotOwnedException} and {@code StaleRouteException}, indicating that
 * the writer's lease or fence has been superseded by a higher epoch in the cluster.</p>
 */
@ResponseStatus(value = HttpStatus.CONFLICT, reason = "Write refused: fence token superseded or mismatched")
public class FencedException extends SynapseException {

    private final String namespaceId;
    private final String incomingFence;
    private final long activeEpoch;

    public FencedException(String namespaceId, String incomingFence, long activeEpoch) {
        super(ErrorCode.FENCED, incomingFence != null ? incomingFence : "<none>", namespaceId, activeEpoch);
        this.namespaceId = namespaceId;
        this.incomingFence = incomingFence;
        this.activeEpoch = activeEpoch;
    }

    public String namespaceId() {
        return namespaceId;
    }

    public String getNamespaceId() {
        return namespaceId;
    }

    public String incomingFence() {
        return incomingFence;
    }

    public String getIncomingFence() {
        return incomingFence;
    }

    public long activeEpoch() {
        return activeEpoch;
    }

    public long getActiveEpoch() {
        return activeEpoch;
    }
}
