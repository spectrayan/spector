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

import com.spectrayan.spector.memory.kernel.layout.FixedEngramLayout;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.RecallOptions;
import java.lang.foreign.MemorySegment;
import java.util.List;

/**
 * Functional interface for scoring a memory slab slice to a list of cognitive results.
 */
@FunctionalInterface
public interface SlabScoreFunction {
    List<CognitiveResult> score(MemorySegment segment, int recordCount, FixedEngramLayout layout,
                                float[] queryVector, RecallOptions options, long nowMs,
                                MemoryType type, long baseOffset, int partitionSeq);
}
