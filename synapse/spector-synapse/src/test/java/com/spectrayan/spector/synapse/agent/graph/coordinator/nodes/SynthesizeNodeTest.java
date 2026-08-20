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
@DisplayName("SynthesizeNode Tests")
class SynthesizeNodeTest {

    @Mock LlmBridge llmBridge;
    SynthesizeNode synthesizeNode;

    @BeforeEach
    void setUp() {
        synthesizeNode = new SynthesizeNode(llmBridge);
    }

    @Test
    @DisplayName("apply — passes through single step result directly")
    void apply_passesThroughSingleStepResult() {
        PlanStep s1 = PlanStep.of(1, "Step 1").withStatus(PlanStep.STATUS_COMPLETED, "Direct answer output");
        CoordinatorState state = new CoordinatorState(Map.of(
                "task", "Simple question",
                "completed_steps", List.of(s1.toMap())
        ));

        Map<String, Object> updates = synthesizeNode.apply(state);

        assertThat(updates.get("answer")).isEqualTo("Direct answer output");
        assertThat(updates.get("decision")).isEqualTo("DONE");
        assertThat(updates.get("status")).isEqualTo("DONE");
    }

    @Test
    @DisplayName("apply — synthesizes multiple completed step findings via LLM")
    void apply_synthesizesMultipleFindings() {
        when(llmBridge.generate(anyString())).thenReturn("Final consolidated answer combining steps 1 and 2.");

        PlanStep s1 = PlanStep.of(1, "Step 1").withStatus(PlanStep.STATUS_COMPLETED, "Finding 1");
        PlanStep s2 = PlanStep.of(2, "Step 2").withStatus(PlanStep.STATUS_COMPLETED, "Finding 2");

        CoordinatorState state = new CoordinatorState(Map.of(
                "task", "Comprehensive research task",
                "completed_steps", List.of(s1.toMap(), s2.toMap())
        ));

        Map<String, Object> updates = synthesizeNode.apply(state);

        assertThat(updates.get("answer")).isEqualTo("Final consolidated answer combining steps 1 and 2.");
        assertThat(updates.get("decision")).isEqualTo("DONE");
        assertThat(updates.get("status")).isEqualTo("DONE");
    }
}
