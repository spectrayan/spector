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

/**
 * A typed relation between two entities extracted from memory text.
 *
 * <p><b>Open-schema types:</b> The relation type is a free-form string,
 * not constrained to the canonical {@link OntologyConfig#canonicalPredicates()} ontology values. Any
 * type string is accepted and auto-registered in the {@link TypeRegistryMemory}
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
