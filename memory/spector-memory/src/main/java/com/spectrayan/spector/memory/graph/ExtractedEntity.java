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
package com.spectrayan.spector.memory.graph;
import com.spectrayan.spector.kernel.store.TypeRegistryMemory;

import java.util.List;

/**
 * An entity extracted from memory text, with its type and relations.
 *
 * <p>Returned by {@link EntityExtractor} during ingestion. The entity name
 * is case-insensitive (normalized during graph population).</p>
 *
 * <p><b>Open-schema types:</b> The type field is a free-form string, not
 * constrained to the canonical {@link OntologyConfig#canonicalTypes()} ontology values. Any type
 * string is accepted and auto-registered in the {@link TypeRegistryMemory} at
 * graph population time. This allows domain-specific types (e.g., VEHICLE,
 * RECIPE, MEDICAL_CONDITION) to flow through without being collapsed to OTHER.</p>
 *
 * @param name      entity name (e.g., "Alice", "Project Alpha")
 * @param type      entity type string (e.g., "PERSON", "VEHICLE" — open-schema)
 * @param relations typed edges to other entities mentioned in the same text
 */
public record ExtractedEntity(
        String name,
        String type,
        List<EntityRelation> relations
) {
    /**
     * Creates an entity with no relations.
     */
    public ExtractedEntity(String name, String type) {
        this(name, type, List.of());
    }

    /**
     * Returns the entity type name as an uppercase string for graph storage.
     */
    public String typeName() {
        return type != null && !type.isBlank() ? type.trim().toUpperCase(java.util.Locale.ROOT) : "OTHER";
    }
}
