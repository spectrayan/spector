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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.spectrayan.spector.mcp.tools.McpToolHandler.McpToolCategory;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.model.FactHistory;
import com.spectrayan.spector.memory.model.FactHistory.FactSnapshot;
import com.spectrayan.spector.memory.model.RecallOptions;

import io.modelcontextprotocol.spec.McpSchema;

/**
 * Unit tests for {@link MemoryMultiEvidenceRecallTool}.
 */
class MemoryMultiEvidenceRecallToolTest {

    private SpectorMemory memory;
    private MemoryMultiEvidenceRecallTool tool;

    @BeforeEach
    void setUp() {
        memory = mock(SpectorMemory.class);
        tool = new MemoryMultiEvidenceRecallTool(memory);
    }

    @Test
    void metadataAndSchema_areValid() {
        assertThat(tool.name()).isEqualTo("memory_multi_evidence_recall");
        assertThat(tool.category()).isEqualTo(McpToolCategory.MEMORY);
        assertThat(tool.isWriteTool()).isFalse();
        assertThat(tool.requiredScopes()).contains(com.spectrayan.spector.commons.security.SpectorScopes.MEMORY_READ);

        Map<String, Object> schema = tool.inputSchema();
        assertThat(schema).containsKey("properties");
        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        assertThat(props).containsKeys("query", "subject", "predicate", "top_k");
    }

    @Test
    void execute_evaluatesConflictingHypotheses_andRecommendsClarificationPolicy() throws Exception {
        FactSnapshot active = new FactSnapshot(201, "Prefers remote work", 1700000000L, Long.MAX_VALUE, 1700000100L, 0.85f, -1);
        FactSnapshot older = new FactSnapshot(200, "Prefers in-office work", 1600000000L, Long.MAX_VALUE, 1600000050L, 0.82f, -1); // unretracted competing fact

        FactHistory history = new FactHistory("Alice", "work_preference", active, List.of(older), 2);
        when(memory.factHistory("Alice", "work_preference")).thenReturn(history);
        when(memory.recall(eq("What is Alice's work preference?"), any(RecallOptions.class))).thenReturn(List.of());

        McpSchema.CallToolResult result = tool.execute(null, Map.of(
                "query", "What is Alice's work preference?",
                "subject", "Alice",
                "predicate", "work_preference"
        ));

        assertThat(result.isError()).isFalse();
        String text = ((McpSchema.TextContent) result.content().get(0)).text();

        assertThat(text).contains("Multi-Evidence Cognitive Distribution");
        assertThat(text).contains("Bitemporal Evidence Chain for `Alice`");
        assertThat(text).contains("`Prefers remote work`");
        assertThat(text).contains("`Prefers in-office work`");
        assertThat(text).contains("**Recommended Action Policy:** `ASK_CLARIFYING_QUESTION`");
    }

    @Test
    void execute_recommendsPresentAlternatives_whenHistoricalSupersessionExists() throws Exception {
        FactSnapshot active = new FactSnapshot(302, "San Francisco", 1700000000L, Long.MAX_VALUE, 1700000100L, 0.95f, -1);
        FactSnapshot older = new FactSnapshot(301, "New York", 1500000000L, 1700000000L, 1500000100L, 0.90f, 302);

        FactHistory history = new FactHistory("Bob", "city", active, List.of(older), 2);
        when(memory.factHistory("Bob", "city")).thenReturn(history);
        when(memory.recall(eq("Where does Bob live?"), any(RecallOptions.class))).thenReturn(List.of());

        McpSchema.CallToolResult result = tool.execute(null, Map.of(
                "query", "Where does Bob live?",
                "subject", "Bob",
                "predicate", "city"
        ));

        assertThat(result.isError()).isFalse();
        String text = ((McpSchema.TextContent) result.content().get(0)).text();

        assertThat(text).contains("`San Francisco`");
        assertThat(text).contains("`New York`");
        assertThat(text).contains("**Recommended Action Policy:** `PRESENT_ALTERNATIVES`");
    }
}
