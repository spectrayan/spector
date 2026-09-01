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
package com.spectrayan.spector.memory.graph.causal;

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

/**
 * Immutable representation of a single directed causal step in a multi-hop causal chain.
 *
 * @param sourceEntity source entity name
 * @param relation     the causal relationship predicate (e.g., "CAUSED_BY", "LED_TO", "TRIGGERED")
 * @param targetEntity target entity name
 * @param weight       confidence/strength weight of the causal edge (0.0 to 1.0)
 * @param memoryIdx    memory graph slot where this causal relationship was asserted (-1 if not linked)
 * @param memoryId     associated memory ID string (null if unknown)
 * @param snippet      supporting text snippet from the memory (null if unknown)
 *
 * @since 1.1.0
 */
public record CausalStep(
        String sourceEntity,
        String relation,
        String targetEntity,
        float weight,
        int memoryIdx,
        String memoryId,
        String snippet
) {
    public String describe() {
        return String.format("%s --[%s]--> %s", sourceEntity, relation, targetEntity);
    }
}
