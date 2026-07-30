/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-memory/LICENSE
 *
 * Change Date: May 27, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.memory.graph;

import java.util.Arrays;

/**
 * Immutable cluster representation for graph coarsening via Kron reduction.
 * Represents an entity hub (representative) and the eliminated leaf/minor entities
 * that were coarsened into it.
 *
 * @param representativeEntityId ID of the cluster hub entity
 * @param memberEntityIds        Array of entity IDs included in this cluster (including representative)
 * @param aggregateWeight        Summed structural weight of the cluster's connections
 */
public record EntityCluster(
    int representativeEntityId,
    int[] memberEntityIds,
    float aggregateWeight
) {
    public EntityCluster {
        memberEntityIds = memberEntityIds != null ? memberEntityIds.clone() : new int[0];
    }

    @Override
    public int[] memberEntityIds() {
        return memberEntityIds.clone();
    }

    @Override
    public String toString() {
        return "EntityCluster{hub=" + representativeEntityId +
            ", members=" + Arrays.toString(memberEntityIds) +
            ", weight=" + aggregateWeight + '}';
    }
}
