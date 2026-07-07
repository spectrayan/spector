/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-synapse/LICENSE
 *
 * Change Date: July 6, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.synapse.agent;

import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.RecallOptions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Bridges agent steps (Thoughts, Actions, Observations) directly to Spector's
 * 4-tier cognitive memory layout (WORKING, EPISODIC, SEMANTIC, PROCEDURAL).
 *
 * <p>This service maps the ReAct pattern (Thought → Action → Observation) to
 * the biological memory tiers:</p>
 * <ul>
 *   <li><b>WORKING</b> — active scratchpad for current step</li>
 *   <li><b>EPISODIC</b> — chronological session logs with temporal decay</li>
 *   <li><b>SEMANTIC</b> — permanent consolidated knowledge</li>
 *   <li><b>PROCEDURAL</b> — tool schemas and executable procedures</li>
 * </ul>
 */
@Component
public final class AgentMemoryBridge {

    private static final Logger log = LoggerFactory.getLogger(AgentMemoryBridge.class);

    private final SpectorMemory memory;

    public AgentMemoryBridge(SpectorMemory memory) {
        if (memory == null) {
            throw new IllegalArgumentException("SpectorMemory backend cannot be null");
        }
        this.memory = memory;
    }

    /**
     * Stores a thought into WORKING memory (volatile scratchpad for the active step)
     * and EPISODIC memory (the chronologically decayable session log).
     */
    public CompletableFuture<Void> saveThought(String sessionId, AgentThought thought) {
        String content = "Thought: " + thought.content();
        String sessionTag = "session_" + sessionId;

        // WORKING Memory: Active scratchpad
        CompletableFuture<String> workFut = memory.remember(
                content,
                MemoryType.WORKING,
                MemorySource.INFERRED,
                "agent_thought", sessionTag
        );

        // EPISODIC Memory: Temporal logs
        CompletableFuture<String> epiFut = memory.remember(
                content,
                MemoryType.EPISODIC,
                MemorySource.INFERRED,
                "agent_thought", "session_log", sessionTag
        );

        return CompletableFuture.allOf(workFut, epiFut);
    }

    /**
     * Stores an action execution log into EPISODIC memory.
     */
    public CompletableFuture<Void> saveAction(String sessionId, AgentAction action) {
        String content = String.format("Action: Called tool '%s' with args %s",
                action.toolName(), action.arguments());
        String sessionTag = "session_" + sessionId;

        return memory.remember(
                content,
                MemoryType.EPISODIC,
                MemorySource.INFERRED,
                "agent_action", "session_log", sessionTag
        ).thenAccept(id -> {});
    }

    /**
     * Stores an observation/result log into EPISODIC memory.
     */
    public CompletableFuture<Void> saveObservation(String sessionId, AgentObservation observation) {
        String content = "Observation: " + observation.result();
        String sessionTag = "session_" + sessionId;

        return memory.remember(
                content,
                MemoryType.EPISODIC,
                MemorySource.INFERRED,
                "agent_observation", "session_log", sessionTag
        ).thenAccept(id -> {});
    }

    /**
     * Stores a consolidated fact or generalized concept into SEMANTIC memory.
     * Semantic memory bypasses temporal decay, storing it as permanent knowledge.
     */
    public CompletableFuture<Void> saveLearnedFact(String sessionId, String fact) {
        String sessionTag = "session_" + sessionId;
        log.info("[MemoryBridge] Consolidating permanent semantic fact for session {}: '{}'", sessionId, fact);

        return memory.remember(
                fact,
                MemoryType.SEMANTIC,
                MemorySource.INFERRED,
                "consolidated_fact", "knowledge", sessionTag
                ).thenAccept(id -> {});
    }

    /**
     * Registers a tool's executable schema into PROCEDURAL memory.
     */
    public CompletableFuture<Void> saveToolDefinition(AgentTool tool) {
        String content = String.format("Tool '%s' schema: %s", tool.name(), tool.description());
        return memory.remember(
                content,
                MemoryType.PROCEDURAL,
                MemorySource.PROCEDURAL,
                "tool_definition", tool.name()
        ).thenAccept(id -> {});
    }

    /**
     * Recalls chronological history for the current session from EPISODIC memory.
     */
    public List<CognitiveResult> recallSessionHistory(String sessionId) {
        String sessionTag = "session_" + sessionId;

        RecallOptions options = RecallOptions.builder()
                .memoryTypes(MemoryType.EPISODIC)
                .minImportance(0.0f)
                .build();

        return memory.recall("session_log " + sessionTag, options);
    }

    /**
     * Recalls semantic knowledge (permanent concepts/facts) relevant to a topic.
     */
    public List<CognitiveResult> recallSemanticKnowledge(String topic) {
        RecallOptions options = RecallOptions.builder()
                .memoryTypes(MemoryType.SEMANTIC)
                .minImportance(2.0f)
                .build();

        return memory.recall(topic, options);
    }

    /**
     * Exposes the underlying SpectorMemory instance.
     */
    public SpectorMemory memory() {
        return memory;
    }
}
