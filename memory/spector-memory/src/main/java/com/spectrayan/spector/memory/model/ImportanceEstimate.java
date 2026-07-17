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
 * Result of a pre-ingestion importance estimate — computed without side effects.
 *
 * <p>Returned by {@link SpectorMemory#estimateImportance} to give the LLM
 * informed feedback about how a memory would be scored <em>before</em> it
 * commits to ingestion. This enables a "compute, then decide" workflow
 * instead of blind ICNU guessing.</p>
 *
 * <h3>MCP Usage</h3>
 * <pre>{@code
 *   // Step 1: LLM asks Spector to evaluate a memory
 *   var est = memory.estimateImportance("The database crashed after migration",
 *       new IngestionHints(0.7f, 0.3f, 0.9f));
 *
 *   // Step 2: LLM sees: novelty=0.82, fusedImportance=7.8, nearestId="mem-42"
 *   //         Decides to proceed with adjusted urgency.
 *
 *   // Step 3: LLM ingests with confidence
 *   memory.remember("db-crash", "The database crashed...", ...);
 * }</pre>
 *
 * @param noveltyScore          Spector-native novelty score (0.0–1.0, normalized from SurpriseDetector)
 * @param noveltyZScore         raw z-score from the running distance distribution
 * @param fusedImportance       final importance after ICNU fusion with provided hints (0.05–10.0)
 * @param noveltyOnlyImportance importance without any LLM hints (novelty-only baseline)
 * @param nearestMemoryId       ID of the most similar existing memory (null if store is empty)
 * @param nearestDistance       L2 distance to the nearest existing memory
 * @param wouldBeFlashbulb      true if this memory would trigger flashbulb (extreme outlier) pinning
 * @param profileWeightsDesc    human-readable description of active ICNU weights
 */
public record ImportanceEstimate(
        float noveltyScore,
        double noveltyZScore,
        float fusedImportance,
        float noveltyOnlyImportance,
        String nearestMemoryId,
        float nearestDistance,
        boolean wouldBeFlashbulb,
        String profileWeightsDesc
) {

    /**
     * Returns a formatted summary suitable for MCP tool responses.
     */
    public String toSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("📊 Importance Estimate\n");
        sb.append("  Novelty:        ").append(String.format("%.2f", noveltyScore))
                .append(" (z-score=").append(String.format("%.2f", noveltyZScore)).append(")\n");
        sb.append("  Fused:          ").append(String.format("%.2f", fusedImportance)).append(" / 10.0\n");
        sb.append("  Novelty-only:   ").append(String.format("%.2f", noveltyOnlyImportance)).append(" / 10.0\n");
        if (nearestMemoryId != null) {
            sb.append("  Nearest:        '").append(nearestMemoryId)
                    .append("' (distance=").append(String.format("%.4f", nearestDistance)).append(")\n");
        } else {
            sb.append("  Nearest:        (no existing memories)\n");
        }
        if (wouldBeFlashbulb) {
            sb.append("  ⚡ FLASHBULB: Extreme outlier — would be pinned at max importance\n");
        }
        sb.append("  Weights:        ").append(profileWeightsDesc);
        return sb.toString();
    }
}
