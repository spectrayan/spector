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
