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
