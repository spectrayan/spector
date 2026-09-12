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
package com.spectrayan.spector.cluster.node;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;

/**
 * Identity of the current Spector instance within a cell (ADR-0034 §15.10, Req R4.4, R4.5).
 *
 * @param cellId the cell identifier (e.g. "us-east-1a")
 * @param nodeId the node identifier (typically pod hostname / ordinal)
 * @param role   the instance operational role
 */
public record NodeIdentity(String cellId, String nodeId, NodeRole role) {

    public NodeIdentity {
        if (role == null) {
            role = NodeRole.DEFAULT;
        }
        if (role != NodeRole.STANDALONE) {
            if (cellId == null || cellId.isBlank()) {
                throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID,
                        "cellId", "cellId is required when node role is " + role);
            }
            if (nodeId == null || nodeId.isBlank()) {
                throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID,
                        "nodeId", "nodeId is required when node role is " + role);
            }
        }
    }

    /**
     * Creates a standalone node identity.
     *
     * @return default standalone identity
     */
    public static NodeIdentity standalone() {
        return new NodeIdentity(null, null, NodeRole.STANDALONE);
    }
}
