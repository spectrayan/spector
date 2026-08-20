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

import com.spectrayan.spector.memory.model.AgentSoul;
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

    @Test
    @DisplayName("execute — executes multi-step plan sequentially and synthesizes final answer")
    void execute_multiStepPlan_executesSequentiallyAndSynthesizes() throws Exception {
        CoordinatorGraph graph = CoordinatorGraph.create(llmBridge, dynamicGraphBuilder, List.of("web_search"), soulService, 5);

        String planJson = """
                ```json
                [
                  { "step": 1, "description": "Gather market facts" },
                  { "step": 2, "description": "Synthesize market projection" }
                ]
                ```
                """;

        when(soulService.listAllAgents()).thenReturn(List.of());
        when(llmBridge.generate(any(String.class))).thenAnswer(invocation -> {
            String prompt = invocation.getArgument(0);
            if (prompt.contains("task planner")) {
                return planJson;
            }
            if (prompt.contains("Perform the following step")) {
                if (prompt.contains("Gather market facts")) return "Fact: Market grew by 20%.";
                if (prompt.contains("Synthesize market projection")) return "Projection: Next year will grow by 25%.";
            }
            if (prompt.contains("quality gate evaluator") || prompt.contains("Evaluate whether")) {
                if (prompt.contains("Step 1 of 2")) return "DECISION: NEXT_STEP";
                if (prompt.contains("Step 2 of 2")) return "DECISION: SYNTHESIZE";
            }
            if (prompt.contains("final synthesis engine") || prompt.contains("Synthesize a comprehensive")) {
                return "Consolidated Analysis: Market grew 20% and is projected to grow 25%.";
            }
            return "Default answer";
        });

        var result = graph.execute("Analyze market growth");
        assertThat(result).isInstanceOf(CoordinatorGraph.CoordinatorResult.Success.class);
        var success = (CoordinatorGraph.CoordinatorResult.Success) result;
        assertThat(success.answer()).contains("Consolidated Analysis");
    }

    @Test
    @DisplayName("execute — triggers adaptive replanning on step failure and completes task")
    void execute_adaptiveReplanning_onStepFailure() throws Exception {
        CoordinatorGraph graph = CoordinatorGraph.create(llmBridge, dynamicGraphBuilder, List.of("web_search"), soulService, 5);

        String initialPlan = """
                ```json
                [
                  { "step": 1, "description": "Query external API" }
                ]
                ```
                """;

        String adaptedPlan = """
                ```json
                [
                  { "step": 1, "description": "Query local cache fallback" }
                ]
                ```
                """;

        when(soulService.listAllAgents()).thenReturn(List.of());
        final boolean[] failedOnce = {false};

        when(llmBridge.generate(any(String.class))).thenAnswer(invocation -> {
            String prompt = invocation.getArgument(0);
            if (prompt.contains("task planner")) {
                return initialPlan;
            }
            if (prompt.contains("adaptive replanner")) {
                return adaptedPlan;
            }
            if (prompt.contains("Perform the following step")) {
                if (prompt.contains("Query external API")) {
                    failedOnce[0] = true;
                    return "ERROR: Network timeout on external API";
                }
                if (prompt.contains("Query local cache fallback")) {
                    return "Cache retrieved: System health 99.9%";
                }
            }
            if (prompt.contains("quality gate evaluator")) {
                if (prompt.contains("Cache retrieved")) return "DECISION: SYNTHESIZE";
                return "DECISION: REPLAN\nCRITIQUE: Network timeout";
            }
            if (prompt.contains("final synthesis engine")) {
                return "System health is 99.9% (retrieved from local cache).";
            }
            return "Default";
        });

        var result = graph.execute("Check system health");
        assertThat(result).isInstanceOf(CoordinatorGraph.CoordinatorResult.Success.class);
        var success = (CoordinatorGraph.CoordinatorResult.Success) result;
        assertThat(success.answer()).contains("System health is 99.9%");
        assertThat(failedOnce[0]).isTrue();
    }
}
