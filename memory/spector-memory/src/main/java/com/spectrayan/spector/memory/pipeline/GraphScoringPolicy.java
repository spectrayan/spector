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
package com.spectrayan.spector.memory.pipeline;

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configurable weights and limits for the 3-Layer Cognitive Graph scoring steps.
 *
 * <p>Replaces the previously hardcoded attenuation factors in
 * {@link RecallPipeline} steps 5b–5e. Each parameter controls how strongly
 * graph-derived signals (causal, Hebbian, temporal, entity) influence the
 * final recall score.</p>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 *   var policy = new GraphScoringPolicy(
 *       0.3f, 0.3f, 0.8f, 0.7f, 0.25f, 2, 3, 2);
 *
 *   // Or use defaults:
 *   var policy = GraphScoringPolicy.DEFAULT;
 *
 *   // Wire into SpectorMemory:
 *   SpectorMemory.builder()
 *       .graphScoringPolicy(policy)
 *       .build();
 * }</pre>
 *
 * @param causalBoostWeight         multiplier for STDP predictive strength (Step 5b)
 * @param hebbianBoostFactor        attenuation for Hebbian spreading activation results (Step 5c)
 * @param temporalForwardFactor     score multiplier for forward temporal chain results (Step 5d)
 * @param temporalBackwardFactor    score multiplier for backward temporal chain results (Step 5d)
 * @param entityHopAttenuation      score multiplier for entity graph traversal results (Step 5e)
 * @param hebbianMaxDepth           maximum depth for Hebbian spreading activation (Step 5c)
 * @param temporalMaxHops           maximum hops for temporal chain traversal (Step 5d)
 * @param entityMaxHops             maximum hops for entity graph BFS traversal (Step 5e)
 * @param graphExpansionThreshold   maximum direct similarity score below which graph expansion
 *                                  is triggered (default: 0.40). When the best direct result
 *                                  has similarity ≥ this threshold, graph expansion is skipped
 *                                  to avoid diluting already-strong results with associative noise.
 * @param graphExpansionMode        controls when graph expansion is triggered (default: GATED)
 */
public record GraphScoringPolicy(
        float causalBoostWeight,
        float hebbianBoostFactor,
        float temporalForwardFactor,
        float temporalBackwardFactor,
        float entityHopAttenuation,
        int hebbianMaxDepth,
        int temporalMaxHops,
        int entityMaxHops,
        float graphExpansionThreshold,
        GraphExpansionMode graphExpansionMode
) {

    private static final Logger log = LoggerFactory.getLogger(GraphScoringPolicy.class);

    /** System property key for configuring the graph expansion threshold at runtime. */
    public static final String THRESHOLD_SYSTEM_PROPERTY = com.spectrayan.spector.config.SpectorPropertyConstants.GRAPH_EXPANSION_THRESHOLD_PROPERTY;

    /**
     * Backward-compatible constructor — uses default graph expansion threshold, GATED mode.
     */
    public GraphScoringPolicy(float causalBoostWeight, float hebbianBoostFactor,
                               float temporalForwardFactor, float temporalBackwardFactor,
                               float entityHopAttenuation, int hebbianMaxDepth,
                               int temporalMaxHops, int entityMaxHops) {
        this(causalBoostWeight, hebbianBoostFactor, temporalForwardFactor,
                temporalBackwardFactor, entityHopAttenuation, hebbianMaxDepth,
                temporalMaxHops, entityMaxHops, 0.40f, GraphExpansionMode.GATED);
    }

    /**
     * Backward-compatible constructor — uses custom graph expansion threshold.
     */
    public GraphScoringPolicy(float causalBoostWeight, float hebbianBoostFactor,
                               float temporalForwardFactor, float temporalBackwardFactor,
                               float entityHopAttenuation, int hebbianMaxDepth,
                               int temporalMaxHops, int entityMaxHops,
                               float graphExpansionThreshold) {
        this(causalBoostWeight, hebbianBoostFactor, temporalForwardFactor,
                temporalBackwardFactor, entityHopAttenuation, hebbianMaxDepth,
                temporalMaxHops, entityMaxHops, graphExpansionThreshold, GraphExpansionMode.GATED);
    }

    /**
     * Default policy — reads runtime overrides from system properties.
     *
     * <ul>
     *   <li>{@code spector.benchmark.graphExpansionThreshold} — float (default: 0.40)</li>
     *   <li>{@code spector.memory.graphExpansionMode} — GATED/ALWAYS/ENTITY_ONLY (default: GATED)</li>
     * </ul>
     */
    public static final GraphScoringPolicy DEFAULT = resolveDefault();

    private static GraphScoringPolicy resolveDefault() {
        float threshold = com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_GRAPH_EXPANSION_THRESHOLD;
        String thresholdStr = System.getProperty(THRESHOLD_SYSTEM_PROPERTY);
        if (thresholdStr == null || thresholdStr.isBlank()) {
            thresholdStr = System.getProperty("graphExpansionThreshold");
        }
        if (thresholdStr != null && !thresholdStr.isBlank()) {
            try {
                threshold = Float.parseFloat(thresholdStr);
            } catch (NumberFormatException e) {
                log.debug("Could not parse value, using default: {}", e.getMessage());
            }
        }
        GraphExpansionMode mode = GraphExpansionMode.resolve();

        return new GraphScoringPolicy(
                com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_GRAPH_CAUSAL_BOOST,
                com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_GRAPH_HEBBIAN_BOOST,
                com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_GRAPH_TEMPORAL_FWD,
                com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_GRAPH_TEMPORAL_BWD,
                com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_GRAPH_ENTITY_ATTENUATION,
                3,      // hebbianMaxDepth (increased from 2 for deeper cross-session associations)
                5,      // temporalMaxHops (increased from 3 for wider session coverage)
                2,      // entityMaxHops
                threshold,
                mode
        );
    }

    /**
     * Compact constructor with validation.
     */
    public GraphScoringPolicy {
        if (causalBoostWeight < 0 || causalBoostWeight > 2.0f)
            throw new com.spectrayan.spector.commons.error.SpectorValidationException(
                    com.spectrayan.spector.commons.error.ErrorCode.ARGUMENT_OUT_OF_RANGE,
                    "causalBoostWeight", 0, 2.0, causalBoostWeight);
        if (hebbianBoostFactor < 0 || hebbianBoostFactor > 2.0f)
            throw new com.spectrayan.spector.commons.error.SpectorValidationException(
                    com.spectrayan.spector.commons.error.ErrorCode.ARGUMENT_OUT_OF_RANGE,
                    "hebbianBoostFactor", 0, 2.0, hebbianBoostFactor);
        if (temporalForwardFactor < 0 || temporalForwardFactor > 2.0f)
            throw new com.spectrayan.spector.commons.error.SpectorValidationException(
                    com.spectrayan.spector.commons.error.ErrorCode.ARGUMENT_OUT_OF_RANGE,
                    "temporalForwardFactor", 0, 2.0, temporalForwardFactor);
        if (temporalBackwardFactor < 0 || temporalBackwardFactor > 2.0f)
            throw new com.spectrayan.spector.commons.error.SpectorValidationException(
                    com.spectrayan.spector.commons.error.ErrorCode.ARGUMENT_OUT_OF_RANGE,
                    "temporalBackwardFactor", 0, 2.0, temporalBackwardFactor);
        if (entityHopAttenuation < 0 || entityHopAttenuation > 2.0f)
            throw new com.spectrayan.spector.commons.error.SpectorValidationException(
                    com.spectrayan.spector.commons.error.ErrorCode.ARGUMENT_OUT_OF_RANGE,
                    "entityHopAttenuation", 0, 2.0, entityHopAttenuation);
        if (hebbianMaxDepth < 1 || hebbianMaxDepth > 10)
            throw new com.spectrayan.spector.commons.error.SpectorValidationException(
                    com.spectrayan.spector.commons.error.ErrorCode.ARGUMENT_OUT_OF_RANGE,
                    "hebbianMaxDepth", 1, 10, hebbianMaxDepth);
        if (temporalMaxHops < 1 || temporalMaxHops > 20)
            throw new com.spectrayan.spector.commons.error.SpectorValidationException(
                    com.spectrayan.spector.commons.error.ErrorCode.ARGUMENT_OUT_OF_RANGE,
                    "temporalMaxHops", 1, 20, temporalMaxHops);
        if (entityMaxHops < 1 || entityMaxHops > 10)
            throw new com.spectrayan.spector.commons.error.SpectorValidationException(
                    com.spectrayan.spector.commons.error.ErrorCode.ARGUMENT_OUT_OF_RANGE,
                    "entityMaxHops", 1, 10, entityMaxHops);
        if (graphExpansionThreshold < 0f || graphExpansionThreshold > 100.0f)
            throw new com.spectrayan.spector.commons.error.SpectorValidationException(
                    com.spectrayan.spector.commons.error.ErrorCode.ARGUMENT_OUT_OF_RANGE,
                    "graphExpansionThreshold", 0, 100.0, graphExpansionThreshold);
        java.util.Objects.requireNonNull(graphExpansionMode, "graphExpansionMode");
    }
}

