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
package com.spectrayan.spector.synapse.agent.tools;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.ObjectProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.synapse.agent.AgentTool;

/**
 * Unit tests for {@link VectorSearchTool}.
 */
@ExtendWith(MockitoExtension.class)
class VectorSearchToolTest {

    @Mock
    private ObjectProvider<SpectorMemory> memoryProvider;

    @Mock
    private SpectorMemory memory;

    private VectorSearchTool tool;

    @BeforeEach
    void setUp() {
        lenient().when(memoryProvider.getIfAvailable()).thenReturn(memory);
        tool = new VectorSearchTool(memoryProvider);
    }

    @Test
    void testName() {
        assertEquals("vector_search", tool.name());
    }

    @Test
    void testCategory() {
        assertEquals(AgentTool.ToolCategory.MEMORY, tool.category());
    }

    @Test
    void testIsReadOnly() {
        assertFalse(tool.isWriteTool());
    }

    @Test
    void testDescription() {
        assertNotNull(tool.description());
        assertTrue(tool.description().contains("vector search"));
    }

    @Test
    void testParameterSchemaHasRequiredParams() {
        Map<String, Object> schema = tool.parameterSchema();
        assertNotNull(schema);

        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertTrue(properties.containsKey("query"));
        assertTrue(properties.containsKey("top_k"));
        assertTrue(properties.containsKey("min_similarity"));
        assertTrue(properties.containsKey("tier"));
        assertTrue(properties.containsKey("time_range"));
        assertTrue(properties.containsKey("tags"));

        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) schema.get("required");
        assertTrue(required.contains("query"));
    }

    @Test
    void testExecuteWithBasicQuery() {
        Map<String, Object> args = Map.of("query", "test query");

        CognitiveResult result = mock(CognitiveResult.class);
        when(result.score()).thenReturn(0.85f);
        when(result.text()).thenReturn("Test memory");

        when(memory.recall(eq("test query"), any(RecallOptions.class))).thenReturn(List.of(result));

        String response = tool.execute(args);
        assertNotNull(response);
        assertTrue(response.contains("Vector Search Results"));
        assertTrue(response.contains("Test memory"));
    }

    @Test
    void testExecuteWithTierFilter() {
        Map<String, Object> args = Map.of(
                "query", "test query",
                "tier", "SEMANTIC");

        CognitiveResult result = mock(CognitiveResult.class);
        when(result.score()).thenReturn(0.85f);
        when(result.text()).thenReturn("Semantic memory");
        when(result.memoryType()).thenReturn(MemoryType.SEMANTIC);

        when(memory.recall(eq("test query"), any(RecallOptions.class))).thenReturn(List.of(result));

        String response = tool.execute(args);
        assertTrue(response.contains("Semantic memory"));
        assertTrue(response.contains("SEMANTIC"));
    }

    @Test
    void testExecuteWithTimeRangeLast24h() {
        Map<String, Object> args = Map.of(
                "query", "test query",
                "time_range", "last_24h");

        CognitiveResult result = mock(CognitiveResult.class);
        when(result.score()).thenReturn(0.85f);
        when(result.text()).thenReturn("Recent memory");

        when(memory.recall(eq("test query"), any(RecallOptions.class))).thenReturn(List.of(result));

        String response = tool.execute(args);
        assertTrue(response.contains("Recent memory"));
    }

    @Test
    void testExecuteWithMinSimilarityFilter() {
        Map<String, Object> args = Map.of(
                "query", "test query",
                "min_similarity", 0.9);

        CognitiveResult highScore = mock(CognitiveResult.class);
        when(highScore.score()).thenReturn(0.95f);
        when(highScore.text()).thenReturn("High score memory");

        CognitiveResult lowScore = mock(CognitiveResult.class);
        when(lowScore.score()).thenReturn(0.65f);

        when(memory.recall(eq("test query"), any(RecallOptions.class)))
                .thenReturn(List.of(highScore, lowScore));

        String response = tool.execute(args);
        assertTrue(response.contains("High score memory"));
        assertFalse(response.contains("Low score memory"));
    }

    @Test
    void testExecuteWithTagsFilter() {
        Map<String, Object> args = Map.of(
                "query", "test query",
                "tags", "debugging,database");

        CognitiveResult result = mock(CognitiveResult.class);
        when(result.score()).thenReturn(0.85f);
        when(result.text()).thenReturn("Tagged memory");

        when(memory.recall(eq("test query"), any(RecallOptions.class))).thenReturn(List.of(result));

        String response = tool.execute(args);
        assertTrue(response.contains("Tagged memory"));
    }

    @Test
    void testEmptyResults() {
        Map<String, Object> args = Map.of("query", "nonexistent");
        when(memory.recall(eq("nonexistent"), any(RecallOptions.class))).thenReturn(List.of());

        String response = tool.execute(args);
        assertTrue(response.contains("No memories found"));
    }

    @Test
    void testResultsBelowMinSimilarity() {
        Map<String, Object> args = Map.of(
                "query", "test query",
                "min_similarity", 0.8);

        CognitiveResult lowScore = mock(CognitiveResult.class);
        when(lowScore.score()).thenReturn(0.5f);

        when(memory.recall(eq("test query"), any(RecallOptions.class)))
                .thenReturn(List.of(lowScore));

        String response = tool.execute(args);
        assertTrue(response.contains("none met min_similarity"));
    }

    @Test
    void testExecuteWhenMemoryEngineNotAvailable() {
        // Arrange
        reset(memoryProvider);
        when(memoryProvider.getIfAvailable()).thenReturn(null);
        Map<String, Object> args = Map.of("query", "test query");

        // Act
        String response = tool.execute(args);

        // Assert
        assertNotNull(response);
        assertTrue(response.contains("Spector memory engine is not available"));
    }
}