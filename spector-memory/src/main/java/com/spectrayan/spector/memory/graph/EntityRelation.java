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
 * A typed relation between two entities extracted from memory text.
 *
 * @param targetEntityName name of the target entity (will be resolved to ID during graph population)
 * @param relationType     the relation type enum value
 */
public record EntityRelation(
        String targetEntityName,
        RelationType relationType
) {
    /**
     * Creates a relation from a type name string, falling back to {@link RelationType#RELATED_TO}.
     */
    public EntityRelation(String targetEntityName, String relationTypeName) {
        this(targetEntityName, parseRelationType(relationTypeName));
    }

    /**
     * Returns the relation type name as a string (for graph storage compatibility).
     */
    public String relationTypeName() {
        return relationType.name();
    }

    private static RelationType parseRelationType(String name) {
        try {
            return RelationType.valueOf(name);
        } catch (IllegalArgumentException e) {
            return RelationType.RELATED_TO;
        }
    }
}
