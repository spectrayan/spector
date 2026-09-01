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

import java.util.List;

/**
 * Structured causal chain representing the multi-hop reasoning path for a "why" or "what caused" query.
 *
 * @param targetEntity             the focal entity that was queried (e.g., "Deployment Failure")
 * @param direction                direction of reasoning (BACKWARD_WHY or FORWARD_EFFECTS)
 * @param steps                    ordered sequence of causal steps from query entity to root cause (or origin to effect)
 * @param rootCauseOrEffect        the identified terminal root cause or ultimate downstream effect
 * @param confidence               cumulative confidence across the causal chain (0.0 to 1.0)
 * @param explanation              structured Markdown formatted explanation of the causal pathway
 *
 * @since 1.1.0
 */
public record CausalChain(
        String targetEntity,
        Direction direction,
        List<CausalStep> steps,
        String rootCauseOrEffect,
        float confidence,
        String explanation
) {
    public enum Direction {
        /** Backward causal reasoning: "Why did X happen?" / "What caused X?" */
        BACKWARD_WHY,
        /** Forward causal reasoning: "What did X cause?" / "What resulted from X?" */
        FORWARD_EFFECTS
    }

    public boolean isEmpty() {
        return steps == null || steps.isEmpty();
    }

    public int hopCount() {
        return steps != null ? steps.size() : 0;
    }

    public static CausalChain empty(String targetEntity, Direction direction, String reason) {
        return new CausalChain(
                targetEntity,
                direction,
                List.of(),
                targetEntity,
                0.0f,
                reason != null ? reason : "No causal links found for '" + targetEntity + "'"
        );
    }
}
