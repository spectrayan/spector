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
import com.spectrayan.spector.kernel.api.MemoryType;

import java.util.List;
import java.util.Map;

/**
 * Structured context assembled from recall results for downstream consumers.
 */
public record UserContext(
    String personaSummary,
    List<TemporalBelief> beliefs,
    List<MemoryChunk> relevantChunks,
    List<CausalNarrative> narratives,
    Map<String, String> metadata
) {
    public record TemporalBelief(String subject, String predicate, String object,
                                  long validFrom, long validTo, float confidence) {}
    public record MemoryChunk(String id, String text, MemoryType type, float score) {}
    public record CausalNarrative(String summary, List<String> memoryIds) {}
}
