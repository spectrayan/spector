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

/**
 * Roles a Spector instance can assume within a cluster cell (ADR-0034 §15.10, Req R4.1).
 *
 * <ul>
 *   <li>{@link #STANDALONE}: Default single-node or embedded instance. Owns all namespaces; takes no cluster dependency.</li>
 *   <li>{@link #OWNER}: Active cluster member eligible to own and write namespaces via consistent hashing.</li>
 *   <li>{@link #REPLICA}: Passive warm replica node applying snapshots (Phase 3). Refuses writes in Phase 1.</li>
 *   <li>{@link #GATEWAY}: Ingress routing proxy node (Phase 2). Refuses local execution in Phase 1.</li>
 * </ul>
 */
public enum NodeRole {
    STANDALONE,
    OWNER,
    REPLICA,
    GATEWAY;

    public static final NodeRole DEFAULT = STANDALONE;

    /**
     * Parses a role string case-insensitively, falling back to {@link #DEFAULT} if null or blank.
     *
     * @param value raw role string
     * @return parsed role
     */
    public static NodeRole fromString(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT;
        }
        return NodeRole.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
    }
}
