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
package com.spectrayan.spector.synapse.agent.graph.coordinator;

import com.spectrayan.spector.memory.model.AgentSoul;
import com.spectrayan.spector.synapse.agent.graph.AgenticChatGraph;
import com.spectrayan.spector.synapse.agent.graph.CognitiveState;
import org.bsc.langgraph4j.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Node that delegates execution to another agent.
 */
public class AgentDelegationNode implements NodeAction<CognitiveState> {

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

        Map<String, Object> response = new HashMap<>();
        response.put("context", List.of("[" + (soul.name() != null ? soul.name() : "Agent") + "] " + result));
        response.put("answer", result);

        Map<String, String> child = new HashMap<>();
        child.put("agent", soul.id() != null ? soul.id() : "");
        child.put("name", soul.name() != null ? soul.name() : "");
        child.put("task", subtask);
        child.put("result", result);

        response.put("child_results", List.of(child));
        return response;
    }
}
