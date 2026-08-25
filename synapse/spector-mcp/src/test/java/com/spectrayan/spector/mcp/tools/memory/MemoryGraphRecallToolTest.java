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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.spectrayan.spector.mcp.tools.McpToolHandler.McpToolCategory;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.model.GraphRecallOptions;
import com.spectrayan.spector.memory.model.GraphTraversalResult;
import com.spectrayan.spector.memory.model.GraphTraversalResult.DiscoveredEntity;
import com.spectrayan.spector.memory.model.GraphTraversalResult.GroundingMemory;
import com.spectrayan.spector.memory.model.GraphTraversalResult.PathNode;
import com.spectrayan.spector.memory.model.GraphTraversalResult.RelationalPath;

import io.modelcontextprotocol.spec.McpSchema;

/**
 * Unit tests for {@link MemoryGraphRecallTool} (#223, #581).
 */
class MemoryGraphRecallToolTest {

    private SpectorMemory memory;
    private MemoryGraphRecallTool tool;

    @BeforeEach
    void setUp() {
        memory = mock(SpectorMemory.class);
        tool = new MemoryGraphRecallTool(memory);
    }

    @Test
    void metadataAndSchema_areValid() {
        assertThat(tool.name()).isEqualTo("memory_graph_recall");
        assertThat(tool.category()).isEqualTo(McpToolCategory.MEMORY);
        assertThat(tool.isWriteTool()).isFalse();
        assertThat(tool.requiredScopes()).contains(com.spectrayan.spector.commons.security.SpectorScopes.MEMORY_READ);
        assertThat(tool.description()).contains("GraphRAG");

        Map<String, Object> schema = tool.inputSchema();
        assertThat(schema).containsKey("properties");
        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        assertThat(props).containsKeys(
                "start_entity", "query", "target_entity", "max_hops",
                "entity_types", "relation_types", "include_memories", "top_paths",
                "as_of", "include_superseded"
        );
    }

    @Test
    void execute_delegatesToMemoryGraphRecall_andFormatsOutput() throws Exception {
        // Arrange
        Set<DiscoveredEntity> entities = Set.of(
                new DiscoveredEntity("Alice", "PERSON", 2),
                new DiscoveredEntity("Bob", "PERSON", 1),
                new DiscoveredEntity("Project Apollo", "PROJECT", 1)
        );

        List<RelationalPath> paths = List.of(
                new RelationalPath(List.of(
                        new PathNode("Alice", "PERSON", null, null),
                        new PathNode("Bob", "PERSON", "works_with", "FACT"),
                        new PathNode("Project Apollo", "PROJECT", "leads", "FACT")
                ), 2)
        );

        List<GroundingMemory> memories = List.of(
                new GroundingMemory("mem-10", "SEMANTIC", "Alice and Bob collaborate on system architecture.")
        );

        GraphTraversalResult expectedResult = GraphTraversalResult.success(
                "Alice", "PERSON",
                "Project Apollo", "PROJECT",
                3,
                entities,
                paths,
                memories,
                42L
        );

        when(memory.graphRecall(any(GraphRecallOptions.class))).thenReturn(expectedResult);

        // Act
        McpSchema.CallToolResult res = tool.execute(Map.of(
                "start_entity", "Alice",
                "target_entity", "Project Apollo",
                "max_hops", 3,
                "top_paths", 5,
                "entity_types", "PERSON,PROJECT",
                "relation_types", "works_with,leads",
                "as_of", "2025-06-15T00:00:00Z",
                "include_superseded", true,
                "include_memories", true
        ));

        // Assert
        assertThat(res.isError()).isFalse();
        String text = ((McpSchema.TextContent) res.content().get(0)).text();

        assertThat(text).contains("Knowledge Graph Traversal Results");
        assertThat(text).contains("42ms");
        assertThat(text).contains("**Start Entity:** `Alice` [PERSON]");
        assertThat(text).contains("**Target Entity:** `Project Apollo` [PROJECT]");
        assertThat(text).contains("**Max Traversal Depth:** 3 hops");
        assertThat(text).contains("**Discovered Entities:** 3");
        assertThat(text).contains("**Discovered Paths:** 1");
        assertThat(text).contains("**Alice** [PERSON] (2 memory references)");
        assertThat(text).contains("**Bob** [PERSON] (1 memory reference)");
        assertThat(text).contains("**Project Apollo** [PROJECT] (1 memory reference)");
        assertThat(text).contains("`Alice` (PERSON) ──[works_with]──> `Bob` (PERSON) ──[leads]──> `Project Apollo` (PROJECT)");
        assertThat(text).contains("**[mem-10]** (SEMANTIC):");
        assertThat(text).contains("Alice and Bob collaborate on system architecture.");

        // Verify GraphRecallOptions constructed properly
        ArgumentCaptor<GraphRecallOptions> captor = ArgumentCaptor.forClass(GraphRecallOptions.class);
        verify(memory).graphRecall(captor.capture());
        GraphRecallOptions captured = captor.getValue();

        assertThat(captured.startEntity()).isEqualTo("Alice");
        assertThat(captured.targetEntity()).isEqualTo("Project Apollo");
        assertThat(captured.maxHops()).isEqualTo(3);
        assertThat(captured.topPaths()).isEqualTo(5);
        assertThat(captured.entityTypeFilters()).containsExactlyInAnyOrder("person", "project");
        assertThat(captured.relationTypeFilters()).containsExactlyInAnyOrder("works_with", "leads");
        assertThat(captured.asOf()).isEqualTo(Instant.parse("2025-06-15T00:00:00Z"));
        assertThat(captured.includeSuperseded()).isTrue();
        assertThat(captured.includeMemories()).isTrue();
    }

    @Test
    void execute_returnsFormattedError_whenGraphRecallFails() throws Exception {
        when(memory.graphRecall(any(GraphRecallOptions.class)))
                .thenReturn(GraphTraversalResult.empty("Could not resolve starting entity for graph traversal."));

        McpSchema.CallToolResult res = tool.execute(Map.of("start_entity", "NonExistent"));
        assertThat(res.isError()).isFalse();

        String text = ((McpSchema.TextContent) res.content().get(0)).text();
        assertThat(text).contains("❌ Graph traversal failed: Could not resolve starting entity for graph traversal.");
    }
}
