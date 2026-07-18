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

/**
 * Well-known relation types for edges in the entity-relationship graph.
 *
 * <p>Each edge between two entities carries a typed relation that enables
 * directed traversal and semantic filtering during recall.</p>
 *
 * @deprecated Since 1.1.0. Relation types are now open-schema strings managed by
 * {@link TypeRegistry}. The extraction layer ({@link EntityRelation}) accepts
 * any type string, allowing domain-specific relation types (e.g., DIAGNOSED_WITH,
 * INGREDIENT_OF, ENROLLED_IN) without code changes. This enum is retained only for:
 * <ul>
 *   <li>{@link #SEED} — pre-seeding well-known types in {@link TypeRegistry}</li>
 *   <li>{@link #fromOrdinal(int)} — migrating V1 persisted EntityGraph files</li>
 * </ul>
 * New code should use plain {@code String} type names instead of this enum.
 *
 * <h3>Category Groups</h3>
 * <ul>
 *   <li><b>People:</b> MANAGES, REPORTS_TO, KNOWS, ASSIGNED_TO, AUTHORED</li>
 *   <li><b>Work:</b> WORKS_ON, CREATED_BY, OWNS, IMPLEMENTS</li>
 *   <li><b>Structure:</b> PART_OF, CONTAINS, DEPENDS_ON, USES</li>
 *   <li><b>Causality:</b> CAUSES, BLOCKS, SUPERSEDES, PRECEDES, FOLLOWS</li>
 *   <li><b>Location:</b> LOCATED_AT</li>
 *   <li><b>Catch-all:</b> RELATED_TO, OTHER</li>
 * </ul>
 */
@Deprecated(since = "1.1.0", forRemoval = false)
public enum RelationType {

    // ── People & Roles ──
    /** A manages B (team lead → engineer, PM → project). */
    MANAGES,
    /** A reports to B (engineer → team lead). */
    REPORTS_TO,
    /** A knows B (interpersonal knowledge). */
    KNOWS,
    /** A is assigned to B (person → task/ticket). */
    ASSIGNED_TO,
    /** A authored/created B (person → document/artifact). */
    AUTHORED,

    // ── Work & Ownership ──
    /** A works on B (person → project/task). */
    WORKS_ON,
    /** A was created by B (artifact → person/tool). */
    CREATED_BY,
    /** A owns B (team → service, person → repo). */
    OWNS,
    /** A implements B (code → design, service → API). */
    IMPLEMENTS,

    // ── Structural ──
    /** A is part of B (component → system, file → module). */
    PART_OF,
    /** A contains B (module → class, project → task). */
    CONTAINS,
    /** A depends on B (service → library, task → prerequisite). */
    DEPENDS_ON,
    /** A uses B (project → technology, person → tool). */
    USES,

    // ── Causality & Temporal ──
    /** A causes B (action → consequence, decision → outcome). */
    CAUSES,
    /** A blocks B (blocker → blocked task). */
    BLOCKS,
    /** A supersedes/replaces B (new version → old version). */
    SUPERSEDES,
    /** A precedes B in time (event A → event B). */
    PRECEDES,
    /** A follows B in time (event B → event A). */
    FOLLOWS,

    // ── Location ──
    /** A is located at B (entity → location). */
    LOCATED_AT,

    // ── General ──
    /** A is related to B (generic association). */
    RELATED_TO,
    /** Fallback for unrecognized relation types. */
    OTHER;

    /** All well-known relation type names, ordered by enum ordinal for registry seeding. */
    public static final String[] SEED = {
        "MANAGES", "REPORTS_TO", "KNOWS", "ASSIGNED_TO", "AUTHORED",
        "WORKS_ON", "CREATED_BY", "OWNS", "IMPLEMENTS",
        "PART_OF", "CONTAINS", "DEPENDS_ON", "USES",
        "CAUSES", "BLOCKS", "SUPERSEDES", "PRECEDES", "FOLLOWS",
        "LOCATED_AT",
        "RELATED_TO", "OTHER"
    };

    /**
     * Returns the relation type name for a legacy enum ordinal.
     *
     * @param ordinal the enum ordinal (0-based)
     * @return the type name string, or "OTHER" if out of range
     */
    public static String fromOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < SEED.length ? SEED[ordinal] : "OTHER";
    }
}
