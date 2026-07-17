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
package com.spectrayan.spector.memory.model;

import com.spectrayan.spector.memory.graph.ExtractedEntity;

import java.util.List;

/**
 * Graph expansion parameters for entity-aware recall.
 *
 * @param entityHints              pre-extracted entities for graph traversal (empty = use EntityExtractor)
 * @param graphExpansionThreshold  max direct similarity below which graph expansion triggers (default: 0.40)
 */
public record GraphOptions(
        List<ExtractedEntity> entityHints,
        float graphExpansionThreshold
) {
    /** Default: no entity hints, expand when similarity < 0.40. */
    public static final GraphOptions DEFAULT = new GraphOptions(List.of(), 0.40f);

    /** Returns true if pre-extracted entities are provided. */
    public boolean hasEntityHints() {
        return entityHints != null && !entityHints.isEmpty();
    }
}
