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
    private PersonaEnactTool tool;

    @BeforeEach
    void setUp() {
        memory = mock(SpectorMemory.class);
        tool = new PersonaEnactTool(memory);
    }

    @Test
    @DisplayName("persona_enact tool name is correctly declared")
    void toolName() {
        assertThat(tool.name()).isEqualTo("persona_enact");
    }

    @Test
    @DisplayName("executeMemory returns valid JSON enactment result with appraisal and deliberation")
    void executeMemory_reactMode() throws Exception {
        when(memory.recall(anyString(), any(RecallOptions.class))).thenReturn(List.of());

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
        when(memory.recall(anyString(), any(RecallOptions.class))).thenReturn(List.of());

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
