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

/**
 * Procedural skill structural classification (ADR-0086 §5.3).
 */
public enum SkillKind {
    /** Single heuristic decision rule (default, semantic-only or legacy). */
    HEURISTIC,

    /** Ordered multi-step execution playbook (prefer mixed episodic + semantic parents). */
    PLAYBOOK,

    /** Approval-backed flow graph specification (deferred Phase 7). */
    GRAPH_TEMPLATE;

    /**
     * Parses a skill kind from string, defaulting to {@link #HEURISTIC} if unrecognized.
     *
     * @param value string value
     * @return matching SkillKind or HEURISTIC
     */
    public static SkillKind from(final String value) {
        if (value == null || value.isBlank()) {
            return HEURISTIC;
        }
        try {
            return SkillKind.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return HEURISTIC;
        }
    }
}
