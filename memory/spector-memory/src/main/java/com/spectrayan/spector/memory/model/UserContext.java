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

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

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
