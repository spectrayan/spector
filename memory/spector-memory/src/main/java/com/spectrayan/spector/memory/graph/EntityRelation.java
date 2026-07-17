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
 * <p><b>Open-schema types:</b> The relation type is a free-form string,
 * not constrained to the well-known {@link RelationType} enum values. Any
 * type string is accepted and auto-registered in the {@link TypeRegistry}
 * at graph population time.</p>
 *
 * @param targetEntityName name of the target entity (will be resolved to ID during graph population)
 * @param relationType     the relation type string (e.g., "MANAGES", "AUTHORED" — open-schema)
 */
public record EntityRelation(
        String targetEntityName,
        String relationType
) {
    /**
     * Returns the relation type name as an uppercase string for graph storage.
     */
    public String relationTypeName() {
        return relationType != null && !relationType.isBlank()
                ? relationType.trim().toUpperCase(java.util.Locale.ROOT) : "RELATED_TO";
    }
}
