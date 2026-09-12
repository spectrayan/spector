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
package com.spectrayan.spector.synapse.cluster.exception;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.synapse.error.SynapseException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a request targets a namespace that is not authoritatively owned by this cell node
 * (ADR-0034 §15.2, Req R5.2, R5.4, R5.5).
 *
 * <p>Carries the authoritative {@code ownerId} and ring {@code epoch} so that Phase 2 clients
 * and gateways can route requests to the correct serving node without guessing or stale cached routes.
 * Maps to HTTP 421 Misdirected Request (distinguishable from 404 Not Found and 401/403 Unauthorized).</p>
 */
@ResponseStatus(value = HttpStatus.MISDIRECTED_REQUEST, reason = "Namespace not owned by this cell node")
public class NamespaceNotOwnedException extends SynapseException {

    private final String namespaceId;
    private final String ownerId;
    private final long epoch;

    /**
     * Creates a new NamespaceNotOwnedException.
     *
     * @param namespaceId the requested namespace ID
     * @param ownerId     the authoritative owner node according to the ring
     * @param epoch       the ring generation / epoch
     */
    public NamespaceNotOwnedException(String namespaceId, String ownerId, long epoch) {
        super(ErrorCode.NAMESPACE_NOT_OWNED, namespaceId, ownerId, epoch);
        this.namespaceId = namespaceId;
        this.ownerId = ownerId;
        this.epoch = epoch;
    }

    public String namespaceId() {
        return namespaceId;
    }

    public String ownerId() {
        return ownerId;
    }

    public long epoch() {
        return epoch;
    }
}
