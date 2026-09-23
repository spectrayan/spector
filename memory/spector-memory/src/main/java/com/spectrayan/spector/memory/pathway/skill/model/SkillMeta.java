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
package com.spectrayan.spector.memory.pathway.skill.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Metadata frontmatter for crystallized procedural skills (ADR-0086 §5.3, §5.4).
 *
 * @param schema     schema identifier (e.g. "spector.skill.v1")
 * @param name       canonical skill identifier name (e.g. "null-check-auth-validator")
 * @param kind       structural category (HEURISTIC, PLAYBOOK, GRAPH_TEMPLATE)
 * @param confidence prior utility / confidence at mint time (0.0 to 1.0)
 * @param tools      referenced tool identifiers required by this skill
 * @param parents    lineage parents mapped by tier (e.g. "episodic" -> [tsids], "semantic" -> [tsids])
 */
public record SkillMeta(
        String schema,
        String name,
        SkillKind kind,
        float confidence,
        List<String> tools,
        Map<String, List<String>> parents
) {
    public static final String SCHEMA_V1 = "spector.skill.v1";

    public SkillMeta {
        schema = (schema != null && !schema.isBlank()) ? schema : SCHEMA_V1;
        kind = kind != null ? kind : SkillKind.HEURISTIC;
        tools = tools != null ? List.copyOf(tools) : List.of();
        parents = parents != null ? Collections.unmodifiableMap(parents) : Map.of();
    }
}
