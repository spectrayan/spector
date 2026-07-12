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
package com.spectrayan.spector.node.api.dto;

/**
 * An edge in the memory graph response.
 *
 * <p>Represents a connection between two memories or between a memory
 * and an entity. The {@code type} determines visual rendering:</p>
 * <ul>
 *   <li>{@code HEBBIAN} — solid white line (co-recall association)</li>
 *   <li>{@code TEMPORAL} — dashed cyan line (session sequence)</li>
 *   <li>{@code ENTITY} — amber line with label (entity relationship)</li>
 * </ul>
 *
 * @param fromId   source memory ID
 * @param toId     target memory ID
 * @param type     edge type: HEBBIAN, TEMPORAL, or ENTITY
 * @param relation relation label (only for ENTITY edges, e.g., "MANAGES")
 * @param weight   association strength (controls line thickness/opacity)
 */
public record GraphEdgeDto(
    String fromId,
    String toId,
    String type,
    String relation,
    float weight
) {}
