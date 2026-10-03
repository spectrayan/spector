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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.spectrayan.spector.memory.SpectorMemory;

import io.modelcontextprotocol.spec.McpSchema;

@DisplayName("MemoryAssociateTool Test Suite (#969)")
class MemoryAssociateToolTest {

    private SpectorMemory memory;
    private MemoryAssociateTool tool;

    @BeforeEach
    void setUp() {
        memory = mock(SpectorMemory.class);
        tool = new MemoryAssociateTool(memory);
    }

    @Test
    @DisplayName("Hebbian association with default type and weight succeeds")
    void hebbianAssociation_defaultType_success() throws Exception {
        when(memory.associateHebbian("mem-1", "mem-2", 1.0f)).thenReturn(true);

        Map<String, Object> args = Map.of(
                "source_id", "mem-1",
                "target_id", "mem-2"
        );

        McpSchema.CallToolResult result = tool.execute(args);

        assertThat(result.isError()).isFalse();
        String text = extractText(result);
        assertThat(text)
                .contains("Hebbian Association Created")
                .contains("mem-1")
                .contains("mem-2")
                .contains("1.0");

        verify(memory).associateHebbian("mem-1", "mem-2", 1.0f);
    }

    @Test
    @DisplayName("Hebbian association with explicit type and custom weight succeeds")
    void hebbianAssociation_explicitTypeAndWeight_success() throws Exception {
        when(memory.associateHebbian("mem-1", "mem-2", 2.5f)).thenReturn(true);

        Map<String, Object> args = Map.of(
                "type", "hebbian",
                "source_id", "mem-1",
                "target_id", "mem-2",
                "weight", 2.5
        );

        McpSchema.CallToolResult result = tool.execute(args);

        assertThat(result.isError()).isFalse();
        String text = extractText(result);
        assertThat(text)
                .contains("Hebbian Association Created")
                .contains("2.5");

        verify(memory).associateHebbian("mem-1", "mem-2", 2.5f);
    }

    @Test
    @DisplayName("Hebbian association returns error when memory not found in index")
    void hebbianAssociation_memoryNotFound_returnsError() throws Exception {
        when(memory.associateHebbian("mem-1", "mem-missing", 1.0f)).thenReturn(false);

        Map<String, Object> args = Map.of(
                "source_id", "mem-1",
                "target_id", "mem-missing"
        );

        McpSchema.CallToolResult result = tool.execute(args);

        assertThat(result.isError()).isTrue();
        assertThat(extractText(result)).contains("Failed to create Hebbian association");
    }

    @Test
    @DisplayName("Temporal association links predecessor and successor with session ID")
    void temporalAssociation_success() throws Exception {
        when(memory.associateTemporal("mem-prev", "mem-curr", 7)).thenReturn(true);

        Map<String, Object> args = Map.of(
                "type", "temporal",
                "source_id", "mem-prev",
                "target_id", "mem-curr",
                "session_id", 7
        );

        McpSchema.CallToolResult result = tool.execute(args);

        assertThat(result.isError()).isFalse();
        String text = extractText(result);
        assertThat(text)
                .contains("Temporal Association Created")
                .contains("mem-prev")
                .contains("mem-curr")
                .contains("7");

        verify(memory).associateTemporal("mem-prev", "mem-curr", 7);
    }

    @Test
    @DisplayName("Temporal association returns error when memory not found")
    void temporalAssociation_memoryNotFound_returnsError() throws Exception {
        when(memory.associateTemporal("mem-1", "mem-2", 0)).thenReturn(false);

        Map<String, Object> args = Map.of(
                "type", "temporal",
                "source_id", "mem-1",
                "target_id", "mem-2"
        );

        McpSchema.CallToolResult result = tool.execute(args);

        assertThat(result.isError()).isTrue();
        assertThat(extractText(result)).contains("Failed to create temporal association");
    }

    @Test
    @DisplayName("Hyperedge association creates n-ary relation from List of entities")
    void hyperedgeAssociation_listEntities_success() throws Exception {
        when(memory.associateHyperedge(eq(List.of("Alice", "Bob", "Project X")), eq("mem-root"), eq(1.8f)))
                .thenReturn(42);

        Map<String, Object> args = Map.of(
                "type", "hyperedge",
                "entities", List.of("Alice", "Bob", "Project X"),
                "memory_id", "mem-root",
                "weight", 1.8
        );

        McpSchema.CallToolResult result = tool.execute(args);

        assertThat(result.isError()).isFalse();
        String text = extractText(result);
        assertThat(text)
                .contains("Hyperedge Association Created")
                .contains("42")
                .contains("Alice, Bob, Project X")
                .contains("mem-root")
                .contains("1.8");

        verify(memory).associateHyperedge(eq(List.of("Alice", "Bob", "Project X")), eq("mem-root"), eq(1.8f));
    }

    @Test
    @DisplayName("Hyperedge association parses comma-separated entities string")
    void hyperedgeAssociation_commaSeparatedEntities_success() throws Exception {
        when(memory.associateHyperedge(eq(List.of("Alpha", "Beta")), isNull(), eq(1.0f)))
                .thenReturn(99);

        Map<String, Object> args = Map.of(
                "type", "hyperedge",
                "entities", "Alpha, Beta"
        );

        McpSchema.CallToolResult result = tool.execute(args);

        assertThat(result.isError()).isFalse();
        String text = extractText(result);
        assertThat(text)
                .contains("Hyperedge Association Created")
                .contains("99")
                .contains("Alpha, Beta");

        verify(memory).associateHyperedge(eq(List.of("Alpha", "Beta")), isNull(), eq(1.0f));
    }

    @Test
    @DisplayName("Hyperedge association fails when fewer than 2 entities are provided")
    void hyperedgeAssociation_fewerThanTwoEntities_returnsError() throws Exception {
        Map<String, Object> args = Map.of(
                "type", "hyperedge",
                "entities", List.of("SingleEntity")
        );

        McpSchema.CallToolResult result = tool.execute(args);

        assertThat(result.isError()).isTrue();
        assertThat(extractText(result)).contains("requires at least 2 entities");
    }

    @Test
    @DisplayName("Hyperedge association returns error when capacity is exceeded (-1)")
    void hyperedgeAssociation_capacityExceeded_returnsError() throws Exception {
        when(memory.associateHyperedge(anyList(), any(), anyFloat())).thenReturn(-1);

        Map<String, Object> args = Map.of(
                "type", "hyperedge",
                "entities", List.of("NodeA", "NodeB")
        );

        McpSchema.CallToolResult result = tool.execute(args);

        assertThat(result.isError()).isTrue();
        assertThat(extractText(result)).contains("graph capacity exceeded");
    }

    @Test
    @DisplayName("Unsupported association type returns clean error")
    void unsupportedType_returnsError() throws Exception {
        Map<String, Object> args = Map.of(
                "type", "unknown_plane"
        );

        McpSchema.CallToolResult result = tool.execute(args);

        assertThat(result.isError()).isTrue();
        assertThat(extractText(result)).contains("Unsupported association type: 'unknown_plane'");
    }

    @Test
    @DisplayName("Missing required parameters throws ToolArgumentException")
    void missingRequiredParameters_hebbian() {
        Map<String, Object> args = Map.of(
                "type", "hebbian"
                // missing source_id and target_id
        );

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> tool.execute(args))
                .isInstanceOf(com.spectrayan.spector.mcp.tools.McpToolHandler.ToolArgumentException.class)
                .hasMessageContaining("Parameter 'source_id' is required");
    }

    @Test
    @DisplayName("Enterprise supplier constructor resolves memory dynamically")
    void enterpriseSupplierConstructor_success() throws Exception {
        when(memory.associateHebbian("mem-A", "mem-B", 1.0f)).thenReturn(true);

        var enterpriseTool = new MemoryAssociateTool(() -> memory);

        Map<String, Object> args = Map.of(
                "source_id", "mem-A",
                "target_id", "mem-B"
        );

        McpSchema.CallToolResult result = enterpriseTool.execute(args);

        assertThat(result.isError()).isFalse();
        assertThat(extractText(result)).contains("Hebbian Association Created");
    }

    private static String extractText(McpSchema.CallToolResult result) {
        assertThat(result.content()).isNotEmpty();
        return ((McpSchema.TextContent) result.content().get(0)).text();
    }
}
