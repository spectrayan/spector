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
@DisplayName("PlanAdapterNode Tests")
class PlanAdapterNodeTest {

    @Mock LlmBridge llmBridge;
    @Mock CognitiveSoulService soulService;

    PlanAdapterNode adapterNode;

    @BeforeEach
    void setUp() {
        adapterNode = new PlanAdapterNode(llmBridge, List.of("vector_search", "web_search"), soulService);
    }

    @Test
    @DisplayName("apply — preserves completed steps and adapts remaining plan from LLM")
    void apply_preservesCompletedAndAdaptsRemaining() {
        PlanStep s1 = PlanStep.of(1, "Step 1").withStatus(PlanStep.STATUS_COMPLETED, "Completed output 1");
        PlanStep s2 = PlanStep.of(2, "Step 2 (failed)").withStatus(PlanStep.STATUS_FAILED, "ERROR: Rate limit");
        PlanStep s3 = PlanStep.of(3, "Step 3 (unexecuted)");

        String revisedJson = """
                ```json
                [
                  {
                    "step": 2,
                    "description": "Alternative query via vector_search",
                    "tool": "vector_search"
                  },
                  {
                    "step": 3,
                    "description": "Synthesize results"
                  }
                ]
                ```
                """;
        when(llmBridge.generate(anyString())).thenReturn(revisedJson);

        CoordinatorState state = new CoordinatorState(Map.of(
                "task", "Complex research task",
                "plan_steps", List.of(s1.toMap(), s2.toMap(), s3.toMap()),
                "completed_steps", List.of(s1.toMap()),
                "current_step_index", 1,
                "step_result", "ERROR: Rate limit",
                "critique", "Step 2 failed due to rate limit",
                "iteration", 2
        ));

        Map<String, Object> updates = adapterNode.apply(state);

        assertThat(updates.get("current_step_index")).isEqualTo(1);
        assertThat(updates.get("iteration")).isEqualTo(3);
        assertThat(updates.get("status")).isEqualTo("EXECUTING_STEP");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> revised = (List<Map<String, Object>>) updates.get("plan_steps");
        assertThat(revised).hasSize(3);
        // Step 1 preserved
        assertThat(revised.get(0).get("description")).isEqualTo("Step 1");
        // Step 2 revised
        assertThat(revised.get(1).get("description")).isEqualTo("Alternative query via vector_search");
        // Step 3 revised
        assertThat(revised.get(2).get("description")).isEqualTo("Synthesize results");
    }

    @Test
    @DisplayName("apply — creates fallback alternative step when LLM JSON is unparseable")
    void apply_createsFallbackStepOnUnparseableResponse() {
        PlanStep s1 = PlanStep.of(1, "Failing step").withStatus(PlanStep.STATUS_FAILED, "ERROR: Timeout");
        when(llmBridge.generate(anyString())).thenReturn("I recommend trying a simpler search.");

        CoordinatorState state = new CoordinatorState(Map.of(
                "task", "Simple goal",
                "plan_steps", List.of(s1.toMap()),
                "current_step_index", 0,
                "step_result", "ERROR: Timeout",
                "iteration", 1
        ));

        Map<String, Object> updates = adapterNode.apply(state);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> revised = (List<Map<String, Object>>) updates.get("plan_steps");
        assertThat(revised).hasSize(1);
        assertThat(revised.getFirst().get("description")).toString().contains("Alternative approach");
    }
}
