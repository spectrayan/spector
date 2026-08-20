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
@DisplayName("ResultEvaluatorNode Tests")
class ResultEvaluatorNodeTest {

    @Mock LlmBridge llmBridge;
    ResultEvaluatorNode evaluatorNode;

    @BeforeEach
    void setUp() {
        evaluatorNode = new ResultEvaluatorNode(llmBridge);
    }

    @Test
    @DisplayName("apply — decides NEXT_STEP when step succeeds and more steps remain")
    void apply_advancesToNextStep() {
        when(llmBridge.generate(anyString())).thenReturn("DECISION: NEXT_STEP");

        PlanStep s1 = PlanStep.of(1, "Step 1");
        PlanStep s2 = PlanStep.of(2, "Step 2");

        CoordinatorState state = new CoordinatorState(Map.of(
                "task", "Multi-step goal",
                "plan_steps", List.of(s1.toMap(), s2.toMap()),
                "current_step_index", 0,
                "step_result", "Step 1 output",
                "iteration", 1,
                "max_iterations", 5
        ));

        Map<String, Object> updates = evaluatorNode.apply(state);

        assertThat(updates.get("decision")).isEqualTo("NEXT_STEP");
        assertThat(updates.get("plan_decision")).isEqualTo("NEXT_STEP");
        assertThat(updates.get("current_step_index")).isEqualTo(1);
    }

    @Test
    @DisplayName("apply — routes to SYNTHESIZE when last step completes successfully")
    void apply_routesToSynthesizeOnLastStep() {
        when(llmBridge.generate(anyString())).thenReturn("DECISION: DONE");

        PlanStep s1 = PlanStep.of(1, "Step 1");
        CoordinatorState state = new CoordinatorState(Map.of(
                "task", "Single step goal",
                "plan_steps", List.of(s1.toMap()),
                "current_step_index", 0,
                "step_result", "Step 1 final output",
                "iteration", 1,
                "max_iterations", 5
        ));

        Map<String, Object> updates = evaluatorNode.apply(state);

        assertThat(updates.get("decision")).isEqualTo("DONE");
        assertThat(updates.get("plan_decision")).isEqualTo("SYNTHESIZE");
        assertThat(updates.get("status")).isEqualTo("SYNTHESIZING");
    }

    @Test
    @DisplayName("apply — routes to REPLAN on error in step result")
    void apply_routesToReplanOnError() {
        PlanStep s1 = PlanStep.of(1, "Faulty step");
        CoordinatorState state = new CoordinatorState(Map.of(
                "task", "Recoverable goal",
                "plan_steps", List.of(s1.toMap()),
                "current_step_index", 0,
                "step_result", "ERROR: Service unavailable",
                "iteration", 1,
                "max_iterations", 5
        ));

        Map<String, Object> updates = evaluatorNode.apply(state);

        assertThat(updates.get("decision")).isEqualTo("REPLAN");
        assertThat(updates.get("plan_decision")).isEqualTo("REPLAN");
        assertThat(updates.get("critique")).toString().contains("ERROR: Service unavailable");
        assertThat(updates.get("status")).isEqualTo("REPLANNING");
    }

    @Test
    @DisplayName("apply — forces SYNTHESIZE when max iterations reached")
    void apply_forcesSynthesizeOnMaxIterations() {
        CoordinatorState state = new CoordinatorState(Map.of(
                "task", "Long task",
                "iteration", 5,
                "max_iterations", 5,
                "step_result", "Partial result"
        ));

        Map<String, Object> updates = evaluatorNode.apply(state);

        assertThat(updates.get("decision")).isEqualTo("DONE");
        assertThat(updates.get("plan_decision")).isEqualTo("SYNTHESIZE");
    }
}
