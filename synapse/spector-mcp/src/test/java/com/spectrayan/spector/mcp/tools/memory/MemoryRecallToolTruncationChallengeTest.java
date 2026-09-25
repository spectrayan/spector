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

import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.RecallOptions;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("MemoryRecallToolTruncationChallengeTest — M3 MCP Truncation Warning Banner")
class MemoryRecallToolTruncationChallengeTest {

    private SpectorMemory memory;
    private MemoryRecallTool tool;

    @BeforeEach
    void setUp() {
        memory = mock(SpectorMemory.class);
        tool = new MemoryRecallTool(memory);
    }

    private CognitiveResult createCognitiveResult(String id, boolean truncated) {
        return new CognitiveResult(
                id,
                "Content of " + id,
                0.95f,
                1.0f,
                0.1f,
                1,
                (byte) 0,
                MemoryType.SEMANTIC,
                null,
                new String[]{"m3", "test"},
                0.9f,
                0.9f,
                null,
                null,
                null,
                null,
                Map.of(),
                (byte) 0,
                System.currentTimeMillis(),
                truncated
        );
    }

    @Test
    @DisplayName("Challenge 3.1: Displays prominent warning banner when recall is truncated")
    void recallTool_displaysWarningBanner_whenTruncated() throws Exception {
        CognitiveResult truncatedResult = createCognitiveResult("mem-mcp-1", true);
        when(memory.recall(eq("vector indexing"), any(RecallOptions.class)))
                .thenReturn(List.of(truncatedResult));

        McpSchema.CallToolResult result = tool.execute(Map.of(
                "query", "vector indexing",
                "partition_visit_budget", 2
        ));

        assertThat(result.isError()).isFalse();
        assertThat(result.content()).hasSize(1);
        String text = ((McpSchema.TextContent) result.content().get(0)).text();

        assertThat(text)
                .as("Output must prepend the explicit truncation warning banner")
                .contains("⚠️ Recall results truncated: partition visit budget capped recall to 2 most recent partitions")
                .contains("⚠️ **RECALL TRUNCATED**: Partition visit budget reached. Some older candidate partitions were skipped.")
                .contains("🧠 Recalled 1 memories")
                .contains("ID: mem-mcp-1");
    }

    @Test
    @DisplayName("Challenge 3.2: Does NOT display warning banner when recall is not truncated")
    void recallTool_noBanner_whenNotTruncated() throws Exception {
        CognitiveResult untruncatedResult = createCognitiveResult("mem-mcp-2", false);
        when(memory.recall(eq("vector indexing"), any(RecallOptions.class)))
                .thenReturn(List.of(untruncatedResult));

        McpSchema.CallToolResult result = tool.execute(Map.of(
                "query", "vector indexing",
                "partition_visit_budget", 10
        ));

        assertThat(result.isError()).isFalse();
        String text = ((McpSchema.TextContent) result.content().get(0)).text();

        assertThat(text)
                .as("Output must not contain any truncation warning")
                .doesNotContain("⚠️ Recall results truncated")
                .doesNotContain("⚠️ **RECALL TRUNCATED**")
                .contains("🧠 Recalled 1 memories")
                .contains("ID: mem-mcp-2");
    }

    @Test
    @DisplayName("Challenge 3.3: Correctly parses partition_visit_budget parameter into RecallOptions")
    void recallTool_passesPartitionVisitBudget_toRecallOptions() throws Exception {
        CognitiveResult res = createCognitiveResult("mem-mcp-3", false);
        ArgumentCaptor<RecallOptions> optionsCaptor = ArgumentCaptor.forClass(RecallOptions.class);
        when(memory.recall(eq("budget plumbing test"), optionsCaptor.capture()))
                .thenReturn(List.of(res));

        tool.execute(Map.of(
                "query", "budget plumbing test",
                "partition_visit_budget", 7
        ));

        RecallOptions captured = optionsCaptor.getValue();
        assertThat(captured.partitionVisitBudget()).isEqualTo(7);
    }

    @Test
    @DisplayName("Challenge 3.4 (Edge Case): When results are empty, returns standard no-memories message")
    void recallTool_emptyResults_returnsNoMemoriesMessage() throws Exception {
        when(memory.recall(eq("empty query"), any(RecallOptions.class)))
                .thenReturn(List.of());

        McpSchema.CallToolResult result = tool.execute(Map.of(
                "query", "empty query",
                "partition_visit_budget", 1
        ));

        assertThat(result.isError()).isFalse();
        String text = ((McpSchema.TextContent) result.content().get(0)).text();
        assertThat(text).isEqualTo("No memories found for query: 'empty query'");
    }
}
