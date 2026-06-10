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

import java.util.List;

/**
 * An entity extracted from memory text, with its type and relations.
 *
 * <p>Returned by {@link EntityExtractor} during ingestion. The entity name
 * is case-insensitive (normalized during graph population).</p>
 *
 * @param name      entity name (e.g., "Alice", "Project Alpha")
 * @param type      entity type enum value
 * @param relations typed edges to other entities mentioned in the same text
 */
public record ExtractedEntity(
        String name,
        EntityType type,
        List<EntityRelation> relations
) {
    /**
     * Creates an entity with no relations.
     */
    public ExtractedEntity(String name, EntityType type) {
        this(name, type, List.of());
    }

    /**
     * Creates an entity from a type name string, falling back to {@link EntityType#OTHER}.
     */
    public ExtractedEntity(String name, String typeName, List<EntityRelation> relations) {
        this(name, parseType(typeName), relations);
    }

    /**
     * Creates an entity from a type name string with no relations.
     */
    public ExtractedEntity(String name, String typeName) {
        this(name, parseType(typeName), List.of());
    }

    /**
     * Returns the entity type name as a string (for graph storage compatibility).
     */
    public String typeName() {
        return type.name();
    }

    private static EntityType parseType(String typeName) {
        try {
            return EntityType.valueOf(typeName);
        } catch (IllegalArgumentException e) {
            return EntityType.OTHER;
        }
    }
}
