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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.spectrayan.spector.mcp.tools.McpToolHandler.McpToolCategory;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.model.FactHistory;
import com.spectrayan.spector.memory.model.FactHistory.FactSnapshot;

import io.modelcontextprotocol.spec.McpSchema;

/**
 * Unit tests for {@link MemoryFactHistoryTool}.
 */
class MemoryFactHistoryToolTest {

    private SpectorMemory memory;
    private MemoryFactHistoryTool tool;

    @BeforeEach
    void setUp() {
        memory = mock(SpectorMemory.class);
        tool = new MemoryFactHistoryTool(memory);
    }

    @Test
    void metadataAndSchema_areValid() {
        assertThat(tool.name()).isEqualTo("memory_fact_history");
        assertThat(tool.category()).isEqualTo(McpToolCategory.MEMORY);
        assertThat(tool.isWriteTool()).isFalse();
        assertThat(tool.requiredScopes()).contains(com.spectrayan.spector.commons.security.SpectorScopes.MEMORY_READ);

        Map<String, Object> schema = tool.inputSchema();
        assertThat(schema).containsKey("properties");
        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        assertThat(props).containsKeys("subject", "predicate");
    }

    @Test
    void execute_returnsFormattedFactHistory() throws Exception {
        FactSnapshot active = new FactSnapshot(102, "VP of Engineering", 1700000000L, Long.MAX_VALUE, 1700000100L, 0.95f, -1);
        FactSnapshot older = new FactSnapshot(101, "Lead Architect", 1600000000L, 1700000000L, 1600000100L, 0.90f, 102);

        FactHistory history = new FactHistory("Alice", "role", active, List.of(older), 2);
        when(memory.factHistory("Alice", "role")).thenReturn(history);

        McpSchema.CallToolResult result = tool.execute(null, Map.of(
                "subject", "Alice",
                "predicate", "role"
        ));

        assertThat(result.isError()).isFalse();
        String text = ((McpSchema.TextContent) result.content().get(0)).text();

        assertThat(text).contains("Bitemporal Fact Evolution");
        assertThat(text).contains("`Alice`");
        assertThat(text).contains("Active Consensus Fact");
        assertThat(text).contains("`VP of Engineering`");
        assertThat(text).contains("Historical Superseded Chain");
        assertThat(text).contains("`Lead Architect`");
        assertThat(text).contains("Superseded By Fact: #102");

        verify(memory).factHistory("Alice", "role");
    }

    @Test
    void execute_handlesNoHistoryGracefully() throws Exception {
        when(memory.factHistory("UnknownEntity", "role")).thenReturn(FactHistory.empty("UnknownEntity", "role"));

        McpSchema.CallToolResult result = tool.execute(null, Map.of(
                "subject", "UnknownEntity",
                "predicate", "role"
        ));

        assertThat(result.isError()).isFalse();
        String text = ((McpSchema.TextContent) result.content().get(0)).text();
        assertThat(text).contains("No historical evolution found");
    }
}
