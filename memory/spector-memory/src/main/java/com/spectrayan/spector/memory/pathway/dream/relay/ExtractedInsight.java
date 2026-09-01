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
package com.spectrayan.spector.memory.pathway.dream.relay;

import java.util.List;

/**
 * Record representing the distilled cognitive residue of a dream (insight/rule/relation).
 * Biological analog: Semantic knowledge extracted from episodic memories during sleep-dependent consolidation.
 *
 * @since 1.4.0
 */
public record ExtractedInsight(
    String id,
    String insightText,
    float[] embedding,
    InsightType type,
    List<String> sourceMemoryIds,
    float confidence,
    float expectedFreeEnergy
) {
    public enum InsightType {
        SEMANTIC,
        PROCEDURAL,
        CONTRADICTION
    }
}
