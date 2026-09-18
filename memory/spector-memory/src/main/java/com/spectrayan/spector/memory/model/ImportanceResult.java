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
package com.spectrayan.spector.memory.model;

/**
 * Represents the result of an importance computation from an ImportanceProvider.
 * This record replaces the former ImportanceEstimate record.
 *
 * @param importance  the computed importance score (typically 0.0 to 10.0)
 * @param isFlashbulb true if the memory represents a flashbulb event pinned at max importance
 * @param breakdown   the detailed breakdown of how the importance score was derived
 */
public record ImportanceResult(
    float importance,
    boolean isFlashbulb,
    ImportanceBreakdown breakdown
) {
    /**
     * Creates a baseline result with neutral importance and an empty breakdown.
     *
     * @return a baseline ImportanceResult
     */
    public static ImportanceResult baseline() {
        return new ImportanceResult(1.0f, false, ImportanceBreakdown.empty());
    }

    /**
     * Generates a formatted summary suitable for MCP tool responses.
     *
     * @return a formatted multiline string summarizing the result
     */
    public String toSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("📊 Importance Result\n");
        sb.append("  Importance:   ").append(String.format("%.2f", importance)).append(" / 10.0\n");
        if (isFlashbulb) {
            sb.append("  ⚡ FLASHBULB: Extreme outlier — pinned at max importance\n");
        }
        sb.append(breakdown.toSummary());
        return sb.toString();
    }
}
