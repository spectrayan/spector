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
