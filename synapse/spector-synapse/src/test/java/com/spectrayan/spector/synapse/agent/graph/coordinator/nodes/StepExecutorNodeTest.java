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
package com.spectrayan.spector.synapse.agent.graph.coordinator.nodes;

import com.spectrayan.spector.memory.model.AgentSoul;
import com.spectrayan.spector.synapse.agent.graph.AgenticChatGraph;
import com.spectrayan.spector.synapse.agent.graph.CognitiveState;
import com.spectrayan.spector.synapse.agent.graph.DynamicGraphBuilder;
import com.spectrayan.spector.synapse.agent.graph.coordinator.CoordinatorState;
import com.spectrayan.spector.synapse.agent.graph.coordinator.model.PlanStep;
import com.spectrayan.spector.synapse.agent.service.CognitiveSoulService;
import com.spectrayan.spector.synapse.bridge.LlmBridge;
import org.bsc.langgraph4j.CompiledGraph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("StepExecutorNode Tests")
class StepExecutorNodeTest {

    @Mock DynamicGraphBuilder dynamicBuilder;
    @Mock LlmBridge llmBridge;
    @Mock CognitiveSoulService soulService;
    @Mock AgenticChatGraph agenticChatGraph;
    @Mock CompiledGraph<CognitiveState> compiledSubgraph;

    StepExecutorNode executorNode;

    @BeforeEach
    void setUp() {
        executorNode = new StepExecutorNode(dynamicBuilder, llmBridge, soulService, agenticChatGraph);
    }

    @Test
    @DisplayName("apply — executes step via specialized agent delegation")
    void apply_delegatesToAgent() {
        AgentSoul soul = AgentSoul.builder().id("analyst").name("Analyst").build();
        when(soulService.loadAgentSoul("analyst")).thenReturn(Optional.of(soul));
        when(agenticChatGraph.chat(eq(soul), eq("Analyze market data"))).thenReturn("Market grew 15%");

        PlanStep step1 = PlanStep.of(1, "Analyze market data", null, "analyst");
        CoordinatorState state = new CoordinatorState(Map.of(
                "task", "Market analysis",
                "plan_steps", List.of(step1.toMap()),
                "current_step_index", 0
        ));

        Map<String, Object> updates = executorNode.apply(state);

        assertThat(updates.get("step_result")).isEqualTo("Market grew 15%");
        assertThat(updates.get("status")).isEqualTo("EVALUATING_STEP");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> completed = (List<Map<String, Object>>) updates.get("completed_steps");
        assertThat(completed).hasSize(1);
        assertThat(completed.getFirst().get("status")).isEqualTo(PlanStep.STATUS_COMPLETED);
    }

    @Test
    @DisplayName("apply — executes step via dynamic subgraph FlowSpec")
    void apply_executesSubgraph() throws Exception {
        when(dynamicBuilder.buildFromJson(any())).thenReturn(compiledSubgraph);
        CognitiveState subState = new CognitiveState(Map.of("answer", "Subgraph executed successfully"));
        when(compiledSubgraph.invoke(anyMap())).thenReturn(Optional.of(subState));

        PlanStep step1 = new PlanStep(1, "Dynamic search", null, null, PlanStep.STATUS_PENDING, "", "{\"version\":\"1.0\"}");
        CoordinatorState state = new CoordinatorState(Map.of(
                "task", "Dynamic search",
                "plan_steps", List.of(step1.toMap()),
                "current_step_index", 0
        ));

        Map<String, Object> updates = executorNode.apply(state);

        assertThat(updates.get("step_result")).isEqualTo("Subgraph executed successfully");
    }

    @Test
    @DisplayName("apply — catches exceptions and marks step as FAILED")
    void apply_catchesExceptionAndMarksFailed() {
        AgentSoul soul = AgentSoul.builder().id("failing-agent").name("Failing").build();
        when(soulService.loadAgentSoul("failing-agent")).thenReturn(Optional.of(soul));
        when(agenticChatGraph.chat(any(), any())).thenThrow(new RuntimeException("Connection timed out"));

        PlanStep step = PlanStep.of(1, "Faulty operation", null, "failing-agent");
        CoordinatorState state = new CoordinatorState(Map.of(
                "task", "Faulty task",
                "plan_steps", List.of(step.toMap()),
                "current_step_index", 0
        ));

        Map<String, Object> updates = executorNode.apply(state);

        assertThat(updates.get("step_result")).toString().contains("ERROR: Connection timed out");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> completed = (List<Map<String, Object>>) updates.get("completed_steps");
        assertThat(completed.getFirst().get("status")).isEqualTo(PlanStep.STATUS_FAILED);
    }
}
