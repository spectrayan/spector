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
package com.spectrayan.spector.synapse.agent.graph.coordinator;

import com.spectrayan.spector.synapse.agent.AgentSoul;
import com.spectrayan.spector.synapse.agent.graph.AgenticChatGraph;
import com.spectrayan.spector.synapse.agent.graph.CognitiveState;
import org.bsc.langgraph4j.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Node action that delegates task execution to a specialized child agent via AgenticChatGraph.
 */
public final class AgentDelegationNode implements NodeAction<CognitiveState> {

    private static final Logger log = LoggerFactory.getLogger(AgentDelegationNode.class);

    private final AgenticChatGraph agenticChatGraph;
    private final AgentSoul soul;

    public AgentDelegationNode(AgenticChatGraph agenticChatGraph, AgentSoul soul) {
        this.agenticChatGraph = agenticChatGraph;
        this.soul = soul;
    }

    @Override
    public Map<String, Object> apply(CognitiveState state) {
        String subtask = state.query().isEmpty() ? state.originalQuery() : state.query();
        log.info("[AgentDelegationNode] Delegating subtask to agent '{}' (ID={}): '{}'",
                soul.name(), soul.id(), subtask);

        String result = agenticChatGraph.chat(soul, subtask);
        log.info("[AgentDelegationNode] Agent '{}' completed execution (result length={})",
                soul.name(), result.length());

        return Map.of(
                "context", List.of("[" + soul.name() + "] " + result),
                "answer", result,
                "child_results", List.of(Map.of(
                        "agent", soul.id(),
                        "name", soul.name(),
                        "task", subtask,
                        "result", result
                ))
        );
    }
}
