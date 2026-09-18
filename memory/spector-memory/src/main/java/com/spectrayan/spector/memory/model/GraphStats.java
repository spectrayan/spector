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

/**
 * Aggregate statistics for the 3-layer cognitive graph subsystem.
 *
 * <p>Returned by {@link com.spectrayan.spector.memory.graph.CognitiveGraphFacade#graphStats()}
 * to provide a single-call summary of Hebbian, temporal, and entity graph state.</p>
 *
 * @param hebbianEdges total edges in the Hebbian co-activation graph
 * @param temporalLinks total linked slots in the temporal causal chain
 * @param entityNodes total entity nodes in the entity-relationship graph
 * @param entityEdges total edges in the entity-relationship graph
 * @since 1.1.0
 */
public record GraphStats(
        int hebbianEdges,
        int temporalLinks,
        int entityNodes,
        int entityEdges
) {}
