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
import com.spectrayan.spector.synapse.agent.graph.coordinator.CoordinatorState;
import com.spectrayan.spector.synapse.agent.graph.coordinator.model.PlanStep;
import com.spectrayan.spector.synapse.agent.service.CognitiveSoulService;
import com.spectrayan.spector.synapse.bridge.LlmBridge;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PlannerNode Tests")
class PlannerNodeTest {

    @Mock LlmBridge llmBridge;
    @Mock CognitiveSoulService soulService;

    PlannerNode plannerNode;

    @BeforeEach
    void setUp() {
        plannerNode = new PlannerNode(llmBridge, List.of("memory_recall", "vector_search"), soulService);
    }

    @Test
    @DisplayName("apply — parses multi-step JSON array plan from LLM")
    void apply_parsesJsonArrayPlan() {
        when(soulService.listAllAgents()).thenReturn(List.of(
                AgentSoul.builder().id("researcher").name("Researcher").tools(List.of("vector_search")).build()
        ));

        String jsonPlan = """
                ```json
                [
                  {
                    "step": 1,
                    "description": "Recall background facts from memory",
                    "tool": "memory_recall"
                  },
                  {
                    "step": 2,
                    "description": "Perform vector search for recent updates",
                    "tool": "vector_search",
                    "assigned_agent": "researcher"
                  }
                ]
                ```
                """;
        when(llmBridge.generate(anyString())).thenReturn(jsonPlan);

        CoordinatorState state = new CoordinatorState(Map.of(
                "task", "Analyze GraphRAG architectures",
                "iteration", 0
        ));

        Map<String, Object> updates = plannerNode.apply(state);

        assertThat(updates).containsKey("plan_steps");
        assertThat(updates.get("current_step_index")).isEqualTo(0);
        assertThat(updates.get("iteration")).isEqualTo(1);
        assertThat(updates.get("status")).isEqualTo("EXECUTING_STEP");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> steps = (List<Map<String, Object>>) updates.get("plan_steps");
        assertThat(steps).hasSize(2);
        assertThat(steps.get(0).get("description")).isEqualTo("Recall background facts from memory");
        assertThat(steps.get(1).get("assigned_agent")).isEqualTo("researcher");
    }

    @Test
    @DisplayName("apply — wraps legacy FlowSpec JSON object into 1-step plan")
    void apply_wrapsFlowSpecJsonObject() {
        when(soulService.listAllAgents()).thenReturn(List.of());

        String flowSpec = """
                ```json
                {
                  "version": "1.0",
                  "id": "test-flow",
                  "nodes": {}
                }
                ```
                """;
        when(llmBridge.generate(anyString())).thenReturn(flowSpec);

        CoordinatorState state = new CoordinatorState(Map.of("task", "Execute dynamic workflow"));
        Map<String, Object> updates = plannerNode.apply(state);

        assertThat(updates).containsKey("flow_spec_json");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> steps = (List<Map<String, Object>>) updates.get("plan_steps");
        assertThat(steps).hasSize(1);
        assertThat(steps.getFirst().get("flow_spec_json").toString()).contains("test-flow");
    }

    @Test
    @DisplayName("apply — falls back to single direct step when LLM outputs plain text")
    void apply_fallsBackToSingleStepOnPlainText() {
        when(soulService.listAllAgents()).thenReturn(List.of());
        when(llmBridge.generate(anyString())).thenReturn("I will answer your question directly.");

        CoordinatorState state = new CoordinatorState(Map.of("task", "Explain quantum computing"));
        Map<String, Object> updates = plannerNode.apply(state);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> steps = (List<Map<String, Object>>) updates.get("plan_steps");
        assertThat(steps).hasSize(1);
        assertThat(steps.getFirst().get("description")).isEqualTo("Explain quantum computing");
    }
}
