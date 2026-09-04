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
package com.spectrayan.spector.memory.pathway.pipeline.scan;

import com.spectrayan.spector.memory.cortex.EpisodicMemory;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.RecallOptions;

import java.util.List;

/**
 * Functional interface for scoring episodic memory turns in a partition to a list of cognitive results.
 */
@FunctionalInterface
public interface EpisodicScoreFunction {
    List<CognitiveResult> score(EpisodicMemory episodic, int partitionSeq,
                                float[] queryVector, String rawQuery,
                                RecallOptions options, long nowMs);
}