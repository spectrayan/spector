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
package com.spectrayan.spector.memory.pathway.pipeline.scan;

import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.kernel.layout.FixedEngramLayout;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.RecallOptions;

import java.util.List;

/**
 * Functional interface for scoring a memory slab slice to a list of cognitive results.
 */
@FunctionalInterface
public interface SlabScoreFunction {
    List<CognitiveResult> score(int partitionSeq, MemoryType type, FixedEngramLayout layout,
                                int recordCount, long baseOffset, float[] queryVector,
                                RecallOptions options, long nowMs);
}
