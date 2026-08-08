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
package com.spectrayan.spector.memory.model;


/**
 * Provides a detailed breakdown of how the importance score was calculated.
 *
 * @param noveltyScore          the base novelty score
 * @param noveltyZScore         the statistical z-score for the novelty
 * @param noveltyOnlyImportance the importance derived solely from novelty
 * @param icnuFusedImportance   the importance after fusing ICNU features
 * @param topicBoost            the boost applied based on topic relevance
 * @param selfRelevanceBoost    the boost applied based on self-relevance
 * @param agentRelevanceBoost   the boost applied based on agent-relevance
 * @param icnuWeightsDesc       description of the weights used for ICNU fusion
 * @param nearestMemoryId       the ID of the nearest memory found, may be null
 * @param nearestDistance       the distance to the nearest memory
 */
public record ImportanceBreakdown(
    float noveltyScore,
    double noveltyZScore,
    float noveltyOnlyImportance,
    float icnuFusedImportance,
    float topicBoost,
    float selfRelevanceBoost,
    float agentRelevanceBoost,
    String icnuWeightsDesc,
    String nearestMemoryId,
    float nearestDistance
) {
    /**
     * Creates an empty breakdown with all neutral values.
     *
     * @return a baseline ImportanceBreakdown
     */
    public static ImportanceBreakdown empty() {
        return new ImportanceBreakdown(0f, 0.0, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, "", null, Float.MAX_VALUE);
    }

    /**
     * Generates a formatted summary suitable for MCP tool responses.
     *
     * @return a formatted multiline string summarizing the importance breakdown
     */
    public String toSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("📊 Importance Breakdown\n");
        sb.append("  Novelty:           ").append(String.format("%.2f", noveltyScore))
                .append(" (z-score=").append(String.format("%.2f", noveltyZScore)).append(")\n");
        sb.append("  Novelty-only:      ").append(String.format("%.2f", noveltyOnlyImportance)).append(" / 10.0\n");
        sb.append("  ICNU-fused:        ").append(String.format("%.2f", icnuFusedImportance)).append(" / 10.0\n");
        if (topicBoost != 1.0f) {
            sb.append("  Topic boost:       ").append(String.format("%.2f", topicBoost)).append("x\n");
        }
        if (selfRelevanceBoost != 1.0f) {
            sb.append("  Self-relevance:    ").append(String.format("%.2f", selfRelevanceBoost)).append("x\n");
        }
        if (agentRelevanceBoost != 1.0f) {
            sb.append("  Agent relevance:   ").append(String.format("%.2f", agentRelevanceBoost)).append("x\n");
        }
        if (nearestMemoryId != null) {
            sb.append("  Nearest:           '").append(nearestMemoryId)
                    .append("' (dist=").append(String.format("%.4f", nearestDistance)).append(")\n");
        }
        sb.append("  ICNU weights:      ").append(icnuWeightsDesc);
        return sb.toString();
    }
}
