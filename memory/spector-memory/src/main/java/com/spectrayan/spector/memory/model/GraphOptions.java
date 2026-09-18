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
    /** Default: no entity hints, expand when similarity &lt; 0.40. */
    public static final GraphOptions DEFAULT = new GraphOptions(List.of(), 0.40f);

    /** Returns true if pre-extracted entities are provided. */
    public boolean hasEntityHints() {
        return entityHints != null && !entityHints.isEmpty();
    }
}
