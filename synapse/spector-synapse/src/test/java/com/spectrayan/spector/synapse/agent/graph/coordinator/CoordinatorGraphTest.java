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
import com.spectrayan.spector.synapse.agent.ToolRegistry;
import com.spectrayan.spector.synapse.agent.graph.AgenticChatGraph;
import com.spectrayan.spector.synapse.agent.graph.CognitiveState;
import com.spectrayan.spector.synapse.agent.graph.DynamicGraphBuilder;
import com.spectrayan.spector.synapse.agent.service.CognitiveSoulService;
import com.spectrayan.spector.synapse.bridge.LlmBridge;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit/Integration tests for {@link CoordinatorGraph} and Multi-Agent hub-spoke delegation.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CoordinatorGraph — Multi-Agent Delegation Tests")
class CoordinatorGraphTest {

    @Mock LlmBridge llmBridge;
    @Mock ToolRegistry toolRegistry;
    @Mock CognitiveSoulService soulService;
    @Mock AgenticChatGraph agenticChatGraph;

    AgentSelector agentSelector;
    DynamicGraphBuilder dynamicGraphBuilder;
    CoordinatorGraph coordinatorGraph;

    @BeforeEach
    void setUp() throws Exception {
        agentSelector = new AgentSelector(soulService);
        dynamicGraphBuilder = new DynamicGraphBuilder(llmBridge, toolRegistry, agentSelector, agenticChatGraph, soulService);
        coordinatorGraph = CoordinatorGraph.create(llmBridge, dynamicGraphBuilder, List.of("web_search"), soulService, 3);
    }

    @Test
    @DisplayName("execute — executes planner, delegates to child agent in compiled subgraph, collects results")
    void execute_delegatesToChildAgent() {
        // 1. Mock available agents listing
        AgentSoul researcherSoul = AgentSoul.builder()
                .id("researcher")
                .name("Researcher Agent")
                .description("Specializes in deep research")
                .tools(List.of("web_search"))
                .build();
        when(soulService.listAllAgents()).thenReturn(List.of(researcherSoul));
        when(soulService.loadAgentSoul("researcher")).thenReturn(Optional.of(researcherSoul));

        // 2. Mock planner node generating a FlowSpec plan to delegate to researcher
        String flowSpecJson = """
                {
                  "version": "1.0",
                  "id": "research-flow",
                  "name": "Research Task Flow",
                  "entry_point": "research_node",
                  "nodes": {
                    "research_node": {
                      "type": "AGENT",
                      "description": "Perform web search for tech trends",
                      "agent": "researcher"
                    }
                  },
                  "edges": [
                    { "from": "research_node", "to": "END", "condition": "always" }
                  ]
                }
                """;
        // Prompt includes:Available agents: ... and asks for FlowSpec JSON.
        // We mock llmBridge.generate to return the JSON spec.
        when(llmBridge.generate(any(String.class))).thenAnswer(invocation -> {
            String prompt = invocation.getArgument(0);
            if (prompt.contains("task planner")) {
                return "```json\n" + flowSpecJson + "\n```";
            }
            if (prompt.contains("Evaluate whether") || prompt.contains("Assess whether")) {
                return "DONE";
            }
            return "";
        });

        // 3. Mock the child agent chat execution returning the subtask answer
        when(agenticChatGraph.chat(eq(researcherSoul), any(String.class)))
                .thenReturn("Java 25 was released with advanced virtual threads capabilities.");

        // 4. Run coordinator execution directly on the compiled graph to check state channels
        var finalState = coordinatorGraph.compiledGraph().invoke(Map.of(
                "task", "Research Java 25 features",
                "query", "Research Java 25 features",
                "original_query", "Research Java 25 features"
        )).orElseThrow();

        // 5. Assert child results were successfully propagated and aggregated in the hub's state
        List<Map<String, Object>> childResults = finalState.childResults();
        assertThat(childResults).hasSize(1);
        assertThat(childResults.getFirst().get("agent")).isEqualTo("researcher");
        assertThat(childResults.getFirst().get("result"))
                .isEqualTo("Java 25 was released with advanced virtual threads capabilities.");

        // 6. Verify success wrapper method works
        var result = coordinatorGraph.execute("Research Java 25 features");
        assertThat(result).isInstanceOf(CoordinatorGraph.CoordinatorResult.Success.class);
        var success = (CoordinatorGraph.CoordinatorResult.Success) result;
        assertThat(success.answer()).contains("Java 25");
        assertThat(success.iterations()).isEqualTo(1);
    }
}
