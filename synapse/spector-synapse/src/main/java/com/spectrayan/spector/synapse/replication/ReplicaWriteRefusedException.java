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
 * Thrown when a write request is dispatched to a replica.
 * A replica unconditionally refuses writes (ADR-0034 §10, §15.5, Invariant N2, Req R10.6).
 *
 * <p>Invariant N2: A replica never writes. Even if a write carries {@code X-Spector-Allow-Replica: true},
 * that header widens read consistency, never write routing.</p>
 */
@ResponseStatus(value = HttpStatus.METHOD_NOT_ALLOWED, reason = "Replicas unconditionally refuse write operations")
public class ReplicaWriteRefusedException extends SynapseException {

    private final String namespaceId;

    public ReplicaWriteRefusedException(String namespaceId) {
        super(ErrorCode.REPLICA_WRITE_REFUSED, namespaceId);
        this.namespaceId = namespaceId;
    }

    public String namespaceId() {
        return namespaceId;
    }

    public String getNamespaceId() {
        return namespaceId;
    }
}
