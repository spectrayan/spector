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

/**
 * Service Provider Interface for probing node readiness and health within a cell (Req R4.1).
 */
@FunctionalInterface
public interface NodeHealthProbe {

    /**
     * Checks if the designated node is currently responding and ready.
     *
     * @param nodeId node identifier to probe
     * @return {@code true} if ready; {@code false} if probe failed
     */
    boolean isNodeReady(String nodeId);
}
