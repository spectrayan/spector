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

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.spectrayan.spector.commons.security.SpectorScopes;
import com.spectrayan.spector.mcp.tools.McpToolHandler.McpToolCategory;
import com.spectrayan.spector.memory.SpectorMemory;

import io.modelcontextprotocol.spec.McpSchema;

/**
 * Unit tests for {@link MemoryRehearseTool}.
 */
@DisplayName("MemoryRehearseTool — MCP Tool Specification")
class MemoryRehearseToolTest {

    private SpectorMemory memory;
    private MemoryRehearseTool tool;

    @BeforeEach
    void setUp() {
        memory = mock(SpectorMemory.class);
        tool = new MemoryRehearseTool(memory);
    }

    @Test
    @DisplayName("Metadata and schema conform to write tool specifications")
    void metadataAndSchema_areValid() {
        assertThat(tool.name()).isEqualTo("memory_rehearse");
        assertThat(tool.category()).isEqualTo(McpToolCategory.MEMORY);
        assertThat(tool.isWriteTool()).isTrue();
        assertThat(tool.requiredScopes()).contains(SpectorScopes.MEMORY_WRITE);
        assertThat(tool.inputSchema()).containsKey("properties");
    }

    @Test
    @DisplayName("Successful rehearsal delegates to memory.rehearse() and returns confirmation")
    void successfulRehearsal_delegatesAndReturnsConfirmation() throws Exception {
        McpSchema.CallToolResult result = tool.execute(Map.of("memory_id", "mem-42"));

        assertThat(result.isError()).isFalse();
        assertThat(result.content()).hasSize(1);
        String text = ((McpSchema.TextContent) result.content().get(0)).text();

        assertThat(text)
                .contains("Rehearsed memory 'mem-42'")
                .contains("Storage strength")
                .contains("Valence unchanged");

        verify(memory).rehearse("mem-42");
    }

    @Test
    @DisplayName("Rehearsal requires memory_id parameter")
    void rehearsal_requiresMemoryId() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> tool.execute(Map.of()))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("Supplier constructor creates tool with correct name")
    void supplierConstructor_hasCorrectName() {
        var supplierTool = new MemoryRehearseTool(() -> memory);
        assertThat(supplierTool.name()).isEqualTo("memory_rehearse");
    }
}
