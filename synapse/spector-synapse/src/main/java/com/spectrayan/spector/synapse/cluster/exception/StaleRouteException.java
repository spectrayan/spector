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
 * Thrown when an incoming request carries an epoch that is older than the owner's active epoch
 * for the targeted namespace (ADR-0034 §8.3, Req R7.3).
 *
 * <p>Carries the authoritative {@code ownerId}, the client's {@code staleEpoch}, and the owner's
 * current {@code activeEpoch}. Signals to gateways and clients that their cached routing entry is stale
 * and must be invalidated and re-resolved. Maps to HTTP 421 Misdirected Request.</p>
 */
@ResponseStatus(value = HttpStatus.MISDIRECTED_REQUEST, reason = "Stale routing epoch")
public class StaleRouteException extends SynapseException {

    private final String namespaceId;
    private final String ownerId;
    private final long staleEpoch;
    private final long activeEpoch;

    /**
     * Creates a new StaleRouteException.
     *
     * @param namespaceId the requested namespace ID
     * @param ownerId     the authoritative owner node
     * @param staleEpoch  the stale epoch from the incoming request
     * @param activeEpoch the current active epoch on the owner node
     */
    public StaleRouteException(String namespaceId, String ownerId, long staleEpoch, long activeEpoch) {
        super(ErrorCode.STALE_ROUTE, staleEpoch, namespaceId, ownerId, activeEpoch);
        this.namespaceId = namespaceId;
        this.ownerId = ownerId;
        this.staleEpoch = staleEpoch;
        this.activeEpoch = activeEpoch;
    }

    public String namespaceId() {
        return namespaceId;
    }

    public String ownerId() {
        return ownerId;
    }

    public long staleEpoch() {
        return staleEpoch;
    }

    public long activeEpoch() {
        return activeEpoch;
    }

    /**
     * Machine-readable error details for G45.
     */
    public java.util.Map<String, Object> details() {
        return java.util.Map.of(
                "namespace", namespaceId != null ? namespaceId : "",
                "owner", ownerId != null ? ownerId : "",
                "epoch", activeEpoch,
                "staleEpoch", staleEpoch
        );
    }
}
