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
package com.spectrayan.spector.bench.longitudinal;

import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.model.CognitiveProfile;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.MemoryType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * In-process Java adapter bridging MemoryArena benchmark executions directly to SpectorMemory engine.
 */
public class MemoryArenaAdapter {

    private static final Logger log = LoggerFactory.getLogger(MemoryArenaAdapter.class);

    private final SpectorMemory memoryEngine;
    private final CognitiveProfile cognitiveProfile;

    public MemoryArenaAdapter(SpectorMemory memoryEngine, CognitiveProfile cognitiveProfile) {
        this.memoryEngine = Objects.requireNonNull(memoryEngine, "memoryEngine cannot be null");
        this.cognitiveProfile = cognitiveProfile != null ? cognitiveProfile : CognitiveProfile.BALANCED;
    }

    /**
     * Stores an experience into Spector memory asynchronously.
     */
    public String remember(String text, float importance, float valence) {
        Objects.requireNonNull(text, "text cannot be null");
        log.debug("MemoryArenaAdapter remember [profile={}]: {}", cognitiveProfile, text);
        return memoryEngine.remember(text, MemoryType.EPISODIC, MemorySource.OBSERVED);
    }

    /**
     * Recalls relevant memories for a task query using configured CognitiveProfile.
     */
    public List<CognitiveResult> recall(String query, int topK) {
        Objects.requireNonNull(query, "query cannot be null");
        log.debug("MemoryArenaAdapter recall [profile={}]: query={}, topK={}", cognitiveProfile, query, topK);
        List<CognitiveResult> results = memoryEngine.recall(query, cognitiveProfile);
        if (results.size() > topK) {
            return results.subList(0, topK);
        }
        return results;
    }

    /**
     * Applies valence reinforcement to a stored memory.
     */
    public void reinforce(String memoryId, byte valenceDelta) {
        Objects.requireNonNull(memoryId, "memoryId cannot be null");
        log.debug("MemoryArenaAdapter reinforce: id={}, delta={}", memoryId, valenceDelta);
        memoryEngine.reinforce(memoryId, valenceDelta);
    }

    /**
     * Suppresses/forgets a specific memory record.
     */
    public void forget(String memoryId) {
        Objects.requireNonNull(memoryId, "memoryId cannot be null");
        log.debug("MemoryArenaAdapter forget: id={}", memoryId);
        memoryEngine.forget(memoryId);
    }

    public CognitiveProfile getCognitiveProfile() {
        return cognitiveProfile;
    }
}
