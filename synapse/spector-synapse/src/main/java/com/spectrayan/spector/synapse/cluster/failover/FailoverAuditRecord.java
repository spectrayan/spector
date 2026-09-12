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
package com.spectrayan.spector.synapse.cluster.failover;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Structured audit record emitted for every failover promotion (Req R9.6).
 *
 * @param namespaceId target namespace
 * @param fromNodeId  previous owner node identifier
 * @param toNodeId    newly promoted survivor node identifier
 * @param epoch       newly advanced namespace epoch
 * @param fence       fence token string minted for the survivor
 * @param trigger     trigger cause (e.g. READINESS_FAILURE, OBSERVE_ONLY, MANUAL)
 * @param duration    elapsed failover duration
 * @param timestamp   timestamp of failover execution
 */
public record FailoverAuditRecord(
        String namespaceId,
        String fromNodeId,
        String toNodeId,
        long epoch,
        String fence,
        String trigger,
        Duration duration,
        Instant timestamp) {

    public FailoverAuditRecord {
        Objects.requireNonNull(namespaceId, "namespaceId must not be null");
        Objects.requireNonNull(fromNodeId, "fromNodeId must not be null");
        Objects.requireNonNull(toNodeId, "toNodeId must not be null");
        Objects.requireNonNull(trigger, "trigger must not be null");
        Objects.requireNonNull(duration, "duration must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
    }
}
