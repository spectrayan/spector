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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.spectrayan.spector.commons.security.SpectorScopes;
import com.spectrayan.spector.mcp.tools.McpToolHandler.McpToolCategory;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.express.relay.ExpressReport;
import com.spectrayan.spector.memory.express.relay.ExpressSignal;
import com.spectrayan.spector.memory.model.RecallOptions;

import io.modelcontextprotocol.spec.McpSchema;

/**
 * Unit tests for {@link MemoryExpressTool}.
 */
class MemoryExpressToolTest {

    private SpectorMemory memory;
    private MemoryExpressTool tool;

    @BeforeEach
    void setUp() {
        memory = mock(SpectorMemory.class);
        tool = new MemoryExpressTool(memory);
    }

    @Test
    void metadataAndSchema_areValid() {
        assertThat(tool.name()).isEqualTo("memory_express");
        assertThat(tool.category()).isEqualTo(McpToolCategory.MEMORY);
        assertThat(tool.isWriteTool()).isFalse();
        assertThat(tool.requiredScopes()).contains(SpectorScopes.MEMORY_READ);
        assertThat(tool.inputSchema()).containsKey("properties");
    }

    @Test
    void execute_successfulSynthesis() throws Exception {
        ExpressReport mockReport = new ExpressReport(
                null, null, null, null,
                "Prompt directives here",
                "Internal monologue here",
                "<prosody pitch=\"+5Hz\"/>",
                Duration.ofMillis(10),
                4
        );
        when(memory.recall(anyString(), any(RecallOptions.class))).thenReturn(List.of());
        when(memory.express(any(ExpressSignal.class))).thenReturn(mockReport);

        McpSchema.CallToolResult result = tool.execute(null, Map.of("query", "How are you?"));

        assertThat(result.isError()).isFalse();
        assertThat(result.content()).isNotEmpty();
        McpSchema.TextContent textContent = (McpSchema.TextContent) result.content().get(0);
        assertThat(textContent.text()).contains("Prompt directives here");
        assertThat(textContent.text()).contains("Internal monologue here");
    }
}
