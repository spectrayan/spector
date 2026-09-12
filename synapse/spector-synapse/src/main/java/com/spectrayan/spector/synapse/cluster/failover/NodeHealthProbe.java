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
