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
package com.spectrayan.spector.synapse.agent.graph.coordinator.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PlanStep Model Tests")
class PlanStepTest {

    @Test
    @DisplayName("of — creates plan step with defaults")
    void of_createsStepWithDefaults() {
        PlanStep step = PlanStep.of(1, "Search knowledge base");

        assertThat(step.step()).isEqualTo(1);
        assertThat(step.description()).isEqualTo("Search knowledge base");
        assertThat(step.tool()).isNull();
        assertThat(step.assignedAgent()).isNull();
        assertThat(step.status()).isEqualTo(PlanStep.STATUS_PENDING);
        assertThat(step.result()).isEmpty();
        assertThat(step.flowSpecJson()).isNull();
        assertThat(step.isPending()).isTrue();
        assertThat(step.isCompleted()).isFalse();
        assertThat(step.isFailed()).isFalse();
    }

    @Test
    @DisplayName("withStatus — transitions state and updates result")
    void withStatus_transitionsState() {
        PlanStep step = PlanStep.of(2, "Synthesize answer", "memory_recall", "researcher");
        PlanStep completed = step.withStatus(PlanStep.STATUS_COMPLETED, "Found 3 papers");

        assertThat(completed.step()).isEqualTo(2);
        assertThat(completed.description()).isEqualTo("Synthesize answer");
        assertThat(completed.tool()).isEqualTo("memory_recall");
        assertThat(completed.assignedAgent()).isEqualTo("researcher");
        assertThat(completed.status()).isEqualTo(PlanStep.STATUS_COMPLETED);
        assertThat(completed.result()).isEqualTo("Found 3 papers");
        assertThat(completed.isCompleted()).isTrue();
        assertThat(completed.isPending()).isFalse();
    }

    @Test
    @DisplayName("toMap and fromMap — preserves all fields across map serialization")
    void serialization_preservesFields() {
        PlanStep step = new PlanStep(
                3,
                "Execute dynamic workflow",
                "custom_tool",
                "analyst",
                PlanStep.STATUS_COMPLETED,
                "Success output",
                "{\"version\":\"1.0\"}"
        );

        Map<String, Object> map = step.toMap();
        assertThat(map.get("step")).isEqualTo(3);
        assertThat(map.get("description")).isEqualTo("Execute dynamic workflow");
        assertThat(map.get("tool")).isEqualTo("custom_tool");
        assertThat(map.get("assigned_agent")).isEqualTo("analyst");
        assertThat(map.get("status")).isEqualTo(PlanStep.STATUS_COMPLETED);
        assertThat(map.get("result")).isEqualTo("Success output");
        assertThat(map.get("flow_spec_json")).isEqualTo("{\"version\":\"1.0\"}");

        PlanStep deserialized = PlanStep.fromMap(map);
        assertThat(deserialized).isEqualTo(step);
    }
}
