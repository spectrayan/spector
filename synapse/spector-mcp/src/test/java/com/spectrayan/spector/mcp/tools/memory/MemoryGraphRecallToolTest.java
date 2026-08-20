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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.SpectorMemoryAdmin;
import com.spectrayan.spector.memory.graph.EntityDirectory;
import com.spectrayan.spector.memory.graph.HyperEntityGraphMemory;
import com.spectrayan.spector.memory.graph.HyperEntityGraphMemory.HyperEdge;
import com.spectrayan.spector.memory.graph.HyperEntityGraphMemory.HyperEdgeVertex;
import com.spectrayan.spector.memory.graph.TypeRegistryMemory;
import com.spectrayan.spector.memory.index.MemoryIndex;
import com.spectrayan.spector.mcp.tools.McpToolHandler.McpToolCategory;
import com.spectrayan.spector.memory.model.CognitiveRecord;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.temporal.TemporalFact;
import com.spectrayan.spector.memory.temporal.TemporalKnowledgeGraph;

import io.modelcontextprotocol.spec.McpSchema;

/**
 * Unit tests for {@link MemoryGraphRecallTool} (#223).
 */
class MemoryGraphRecallToolTest {

    private SpectorMemory memory;
    private SpectorMemoryAdmin admin;
    private EntityDirectory entityDirectory;
    private HyperEntityGraphMemory hyperEntityGraph;
    private TemporalKnowledgeGraph temporalKnowledgeGraph;
    private MemoryIndex memoryIndex;
    private TypeRegistryMemory predicateRegistry;

    private MemoryGraphRecallTool tool;

    @BeforeEach
    void setUp() {
        memory = mock(SpectorMemory.class);
        admin = mock(SpectorMemoryAdmin.class);
        entityDirectory = mock(EntityDirectory.class);
        hyperEntityGraph = mock(HyperEntityGraphMemory.class);
        temporalKnowledgeGraph = mock(TemporalKnowledgeGraph.class);
        memoryIndex = mock(MemoryIndex.class);
        predicateRegistry = mock(TypeRegistryMemory.class);

        when(memory.admin()).thenReturn(admin);
        when(admin.entityDirectory()).thenReturn(entityDirectory);
        when(admin.hyperEntityGraph()).thenReturn(hyperEntityGraph);
        when(admin.temporalKnowledgeGraph()).thenReturn(temporalKnowledgeGraph);
        when(admin.index()).thenReturn(memoryIndex);
        when(temporalKnowledgeGraph.predicateRegistry()).thenReturn(predicateRegistry);
        when(temporalKnowledgeGraph.retractedFactIds()).thenReturn(Set.of());

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
        assertThat(props).containsKeys("start_entity", "query", "target_entity", "max_hops", "entity_types", "relation_types", "include_memories", "top_paths");
    }

    @Test
    void execute_returnsError_whenAdminOrDirectoryMissing() throws Exception {
        when(memory.admin()).thenReturn(null);
        McpSchema.CallToolResult res = tool.execute(null, Map.of("start_entity", "Alice"));
        assertThat(res.isError()).isTrue();

        when(memory.admin()).thenReturn(admin);
        when(admin.entityDirectory()).thenReturn(null);
        McpSchema.CallToolResult res2 = tool.execute(null, Map.of("start_entity", "Alice"));
        assertThat(res2.isError()).isTrue();
    }

    @Test
    void execute_returnsDiagnostics_whenEntityNotFound() throws Exception {
        when(entityDirectory.nameIndex()).thenReturn(Map.of("Bob", 1, "Project Apollo", 2));
        when(entityDirectory.entityType(1)).thenReturn("PERSON");
        when(entityDirectory.entityType(2)).thenReturn("PROJECT");

        McpSchema.CallToolResult res = tool.execute(null, Map.of("start_entity", "Alice"));
        assertThat(res.isError()).isFalse();
        String text = ((McpSchema.TextContent) res.content().get(0)).text();
        assertThat(text).contains("Could not resolve starting entity");
        assertThat(text).contains("Available Sample Entities");
        assertThat(text).contains("Bob");
    }

    @Test
    void execute_multiHopTraversal_viaTemporalKnowledgeGraphTriples() throws Exception {
        // Entity setup: 0 = Alice (PERSON), 1 = Bob (PERSON), 2 = Project Apollo (PROJECT)
        when(entityDirectory.nameIndex()).thenReturn(Map.of("Alice", 0, "Bob", 1, "Project Apollo", 2));
        when(entityDirectory.entityName(0)).thenReturn("Alice");
        when(entityDirectory.entityType(0)).thenReturn("PERSON");
        when(entityDirectory.memoriesForEntity(0)).thenReturn(new int[]{10});

        when(entityDirectory.entityName(1)).thenReturn("Bob");
        when(entityDirectory.entityType(1)).thenReturn("PERSON");
        when(entityDirectory.memoriesForEntity(1)).thenReturn(new int[]{11});

        when(entityDirectory.entityName(2)).thenReturn("Project Apollo");
        when(entityDirectory.entityType(2)).thenReturn("PROJECT");
        when(entityDirectory.memoriesForEntity(2)).thenReturn(new int[]{12});

        // Predicate setup
        when(predicateRegistry.nameOf(100)).thenReturn("works_with");
        when(predicateRegistry.nameOf(101)).thenReturn("leads");

        // Facts:
        // Alice (0) --works_with(100)--> Bob (1)
        TemporalFact fact1 = new TemporalFact(1, 0, 100, 1, -1, (short) 0, 1000L, Long.MAX_VALUE, 2000L, 0.9f, -1, (byte) 0);
        // Bob (1) --leads(101)--> Project Apollo (2)
        TemporalFact fact2 = new TemporalFact(2, 1, 101, 2, -1, (short) 0, 1000L, Long.MAX_VALUE, 2000L, 0.95f, -1, (byte) 0);

        when(temporalKnowledgeGraph.readFactsForEntity(0)).thenReturn(List.of(fact1));
        when(temporalKnowledgeGraph.readFactsForEntity(1)).thenReturn(List.of(fact2));
        when(temporalKnowledgeGraph.readFactsForEntity(2)).thenReturn(List.of());

        // Memory index slot resolution
        CognitiveRecord r1 = mock(CognitiveRecord.class);
        when(r1.text()).thenReturn("Alice and Bob collaborate on system architecture.");
        when(r1.memoryType()).thenReturn(MemoryType.SEMANTIC);
        when(memory.inspect("mem-10")).thenReturn(r1);

        McpSchema.CallToolResult res = tool.execute(null, Map.of(
                "start_entity", "Alice",
                "max_hops", 3
        ));

        assertThat(res.isError()).isFalse();
        String text = ((McpSchema.TextContent) res.content().get(0)).text();
        assertThat(text).contains("Knowledge Graph Traversal Results");
        assertThat(text).contains("**Alice** [PERSON]");
        assertThat(text).contains("**Bob** [PERSON]");
        assertThat(text).contains("**Project Apollo** [PROJECT]");
        assertThat(text).contains("`Alice` (PERSON)");
        assertThat(text).contains("`Bob` (PERSON)");
        assertThat(text).contains("`Project Apollo` (PROJECT)");
    }

    @Test
    void execute_targetEntityPathfinding_andTypeFiltering() throws Exception {
        when(entityDirectory.nameIndex()).thenReturn(Map.of("Alice", 0, "Bob", 1, "Charlie", 2, "Project Apollo", 3));
        when(entityDirectory.entityName(0)).thenReturn("Alice");
        when(entityDirectory.entityType(0)).thenReturn("PERSON");
        when(entityDirectory.memoriesForEntity(0)).thenReturn(new int[]{});

        when(entityDirectory.entityName(1)).thenReturn("Bob");
        when(entityDirectory.entityType(1)).thenReturn("PERSON");
        when(entityDirectory.memoriesForEntity(1)).thenReturn(new int[]{});

        when(entityDirectory.entityName(2)).thenReturn("Charlie");
        when(entityDirectory.entityType(2)).thenReturn("BOT");
        when(entityDirectory.memoriesForEntity(2)).thenReturn(new int[]{});

        when(entityDirectory.entityName(3)).thenReturn("Project Apollo");
        when(entityDirectory.entityType(3)).thenReturn("PROJECT");
        when(entityDirectory.memoriesForEntity(3)).thenReturn(new int[]{});

        when(predicateRegistry.nameOf(anyInt())).thenReturn("connects_to");

        TemporalFact f1 = new TemporalFact(1, 0, 50, 1, -1, (short) 0, 1000L, Long.MAX_VALUE, 2000L, 0.9f, -1, (byte) 0);
        TemporalFact f2 = new TemporalFact(2, 0, 50, 2, -1, (short) 0, 1000L, Long.MAX_VALUE, 2000L, 0.9f, -1, (byte) 0);
        TemporalFact f3 = new TemporalFact(3, 1, 50, 3, -1, (short) 0, 1000L, Long.MAX_VALUE, 2000L, 0.9f, -1, (byte) 0);

        when(temporalKnowledgeGraph.readFactsForEntity(0)).thenReturn(List.of(f1, f2));
        when(temporalKnowledgeGraph.readFactsForEntity(1)).thenReturn(List.of(f3));
        when(temporalKnowledgeGraph.readFactsForEntity(2)).thenReturn(List.of());
        when(temporalKnowledgeGraph.readFactsForEntity(3)).thenReturn(List.of());

        // Point to point from Alice to Project Apollo, filtering out BOT entities
        McpSchema.CallToolResult res = tool.execute(null, Map.of(
                "start_entity", "Alice",
                "target_entity", "Project Apollo",
                "entity_types", "PERSON,PROJECT",
                "max_hops", 3
        ));

        assertThat(res.isError()).isFalse();
        String text = ((McpSchema.TextContent) res.content().get(0)).text();
        assertThat(text).contains("**Target Entity:** `Project Apollo`");
        assertThat(text).contains("`Alice` (PERSON)");
        assertThat(text).contains("`Bob` (PERSON)");
        assertThat(text).contains("`Project Apollo` (PROJECT)");
        assertThat(text).doesNotContain("Charlie");
    }

    @Test
    void execute_cooccurrenceTraversal_viaHyperEntityGraph() throws Exception {
        when(entityDirectory.nameIndex()).thenReturn(Map.of("Sarah Chen", 0, "David", 1));
        when(entityDirectory.entityName(0)).thenReturn("Sarah Chen");
        when(entityDirectory.entityType(0)).thenReturn("PERSON");
        when(entityDirectory.memoriesForEntity(0)).thenReturn(new int[]{5});

        when(entityDirectory.entityName(1)).thenReturn("David");
        when(entityDirectory.entityType(1)).thenReturn("PERSON");
        when(entityDirectory.memoriesForEntity(1)).thenReturn(new int[]{5});

        HyperEdge hedge = new HyperEdge(1, 0, 1.0f, 5, 2000L, List.of(
                new HyperEdgeVertex(0, 1),
                new HyperEdgeVertex(1, 2)
        ));

        when(hyperEntityGraph.findHyperedgesForEntity(0)).thenReturn(List.of(hedge));
        when(hyperEntityGraph.findHyperedgesForEntity(1)).thenReturn(List.of());

        McpSchema.CallToolResult res = tool.execute(null, Map.of(
                "query", "What can you find about Sarah Chen and her connections?",
                "max_hops", 2
        ));

        assertThat(res.isError()).isFalse();
        String text = ((McpSchema.TextContent) res.content().get(0)).text();
        assertThat(text).contains("**Sarah Chen** [PERSON]");
        assertThat(text).contains("**David** [PERSON]");
        assertThat(text).contains("`Sarah Chen` (PERSON)");
        assertThat(text).contains("`David` (PERSON)");
        assertThat(text).contains("SHARED_MEMORY");
    }
}
