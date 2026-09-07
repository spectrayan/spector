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
package com.spectrayan.spector.mcp.tools.memory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.model.enactment.CognitiveAppraisal;
import com.spectrayan.spector.memory.model.enactment.ConfidenceLevel;
import com.spectrayan.spector.memory.model.enactment.EnactMode;
import com.spectrayan.spector.memory.model.enactment.Enactment;
import com.spectrayan.spector.memory.model.enactment.PersonaDeliberation;
import com.spectrayan.spector.memory.model.enactment.SituationFrame;
import com.spectrayan.spector.memory.model.enactment.TradeOffSelection;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PersonaEnactToolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SpectorMemory memory;
    private PersonaEnactTool.Enactor mockEnactor;
    private PersonaEnactTool tool;

    @BeforeEach
    void setUp() {
        memory = mock(SpectorMemory.class);
        mockEnactor = mock(PersonaEnactTool.Enactor.class);
        tool = new PersonaEnactTool(memory, mockEnactor);
    }

    @Test
    @DisplayName("persona_enact tool name is correctly declared")
    void toolName() {
        assertThat(tool.name()).isEqualTo("persona_enact");
    }

    @Test
    @DisplayName("executeMemory without Enactor throws IllegalStateException (Invariant I6)")
    void executeMemory_withoutEnactor_throwsIllegalStateException() {
        PersonaEnactTool bareTool = new PersonaEnactTool(memory);
        Map<String, Object> args = Map.of(
                "problem", "Database connection pool exhausted under spike",
                "acting_soul_id", "jarvis",
                "mode", "REACT"
        );

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> bareTool.executeMemory(memory, args))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADR-0032 Invariant I6");
    }

    @Test
    @DisplayName("executeMemory returns valid JSON enactment result with appraisal and deliberation")
    void executeMemory_reactMode() throws Exception {
        SituationFrame expectedSituation = SituationFrame.of("Database connection pool exhausted under spike");
        Enactment mockEnactment = new Enactment(
                expectedSituation,
                CognitiveAppraisal.neutral(),
                null,
                java.util.List.of(),
                null,
                new PersonaDeliberation("Internal monologue", "Active dogma", TradeOffSelection.balanced("Balance"), java.util.List.of(), "First move"),
                java.util.Set.of("STANCE: investigate"),
                "Hedging: Database connection pool issue observed.",
                ConfidenceLevel.INFERRED,
                "FACT",
                java.util.List.of(),
                java.util.List.of()
        );
        when(mockEnactor.enact(any(SituationFrame.class), anyString(), anyString(), any(EnactMode.class)))
                .thenReturn(mockEnactment);

        Map<String, Object> args = Map.of(
                "problem", "Database connection pool exhausted under spike",
                "acting_soul_id", "jarvis",
                "mode", "REACT"
        );

        McpSchema.CallToolResult result = tool.executeMemory(memory, args);
        assertThat(result).isNotNull();
        assertThat(result.content()).isNotEmpty();

        McpSchema.TextContent textContent = (McpSchema.TextContent) result.content().get(0);
        JsonNode json = MAPPER.readTree(textContent.text());

        assertThat(json.has("situation")).isTrue();
        assertThat(json.get("situation").get("problem").asText()).isEqualTo("Database connection pool exhausted under spike");
        assertThat(json.has("appraisal")).isTrue();
        assertThat(json.get("tense").asText()).isEqualTo("FACT");
        assertThat(json.get("confidence").asText()).isEqualTo("INFERRED");
        assertThat(json.get("utterance").asText()).contains("Hedging");
    }

    @Test
    @DisplayName("executeMemory in SIMULATE mode produces SIM tense and hypothetical simulation prefix")
    void executeMemory_simulateMode() throws Exception {
        SituationFrame expectedSituation = SituationFrame.of("Hypothetical migration to quantum encryption");
        Enactment mockEnactment = new Enactment(
                expectedSituation,
                CognitiveAppraisal.neutral(),
                null,
                java.util.List.of(),
                null,
                new PersonaDeliberation("Internal monologue", "Active dogma", TradeOffSelection.balanced("Balance"), java.util.List.of(), "First move"),
                java.util.Set.of("STANCE: simulate"),
                "[SIMULATION] Quantum encryption hypothetical migration.",
                ConfidenceLevel.INFERRED,
                "SIM",
                java.util.List.of(),
                java.util.List.of()
        );
        when(mockEnactor.enact(any(SituationFrame.class), anyString(), anyString(), any(EnactMode.class)))
                .thenReturn(mockEnactment);

        Map<String, Object> args = Map.of(
                "problem", "Hypothetical migration to quantum encryption",
                "acting_soul_id", "titan",
                "mode", "SIMULATE"
        );

        McpSchema.CallToolResult result = tool.executeMemory(memory, args);
        McpSchema.TextContent textContent = (McpSchema.TextContent) result.content().get(0);
        JsonNode json = MAPPER.readTree(textContent.text());

        assertThat(json.get("tense").asText()).isEqualTo("SIM");
        assertThat(json.get("utterance").asText()).contains("[SIMULATION]");
    }
}
