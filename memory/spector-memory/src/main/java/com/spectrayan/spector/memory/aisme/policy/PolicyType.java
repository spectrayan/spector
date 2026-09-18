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
package com.spectrayan.spector.memory.aisme.policy;

/**
 * Enumeration of cognitive policy types.
 */
public enum PolicyType {
    /** Biological analog: Novelty-seeking foraging. Cognitive function: Expanding state-space knowledge. */
    EPISTEMIC_EXPLORATION,
    /** Biological analog: Goal-directed consummatory behavior. Cognitive function: Exploiting known rewards. */
    PRAGMATIC_EXPLOITATION,
    /** Biological analog: Active inference dialogue. Cognitive function: Reducing uncertainty via user prompt. */
    CLARIFYING_INTERACTION,
    /** Biological analog: Memory consolidation. Cognitive function: Caching procedures from episodic memory. */
    PROCEDURAL_CRYSTALLIZATION,
    /** Biological analog: Sleep/Rest. Cognitive function: Minimizing metabolic cost and consolidating state. */
    HOMEOSTATIC_REST,
    /** Biological analog: Psychological defense mechanism/reappraisal. Cognitive function: Altering narrative models. */
    NARRATIVE_REFRAMING
}
