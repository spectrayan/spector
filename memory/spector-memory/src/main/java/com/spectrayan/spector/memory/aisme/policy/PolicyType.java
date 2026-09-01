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
package com.spectrayan.spector.memory.aisme.policy;

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

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
