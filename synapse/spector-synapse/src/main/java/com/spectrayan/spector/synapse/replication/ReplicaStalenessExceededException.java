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
