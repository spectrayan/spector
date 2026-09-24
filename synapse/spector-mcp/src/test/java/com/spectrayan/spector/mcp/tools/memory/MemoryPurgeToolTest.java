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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.spectrayan.spector.commons.security.SpectorScopes;
import com.spectrayan.spector.mcp.tools.McpToolHandler.McpToolCategory;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.model.PurgeResult;

import io.modelcontextprotocol.spec.McpSchema;

/**
 * Unit tests for {@link MemoryPurgeTool}.
 */
@DisplayName("MemoryPurgeTool — MCP Tool Specification")
class MemoryPurgeToolTest {

    private SpectorMemory memory;
    private MemoryPurgeTool tool;

    @BeforeEach
    void setUp() {
        memory = mock(SpectorMemory.class);
        tool = new MemoryPurgeTool(memory);
    }

    @Test
    @DisplayName("Metadata and schema conform to write tool specifications")
    void metadataAndSchema_areValid() {
        assertThat(tool.name()).isEqualTo("memory_purge");
        assertThat(tool.category()).isEqualTo(McpToolCategory.MEMORY);
        assertThat(tool.isWriteTool()).isTrue();
        assertThat(tool.requiredScopes()).contains(SpectorScopes.MEMORY_WRITE);
        assertThat(tool.inputSchema()).containsKey("properties");
    }

    @Test
    @DisplayName("Successful purge formats comprehensive audit report")
    void successfulPurge_returnsDetailedAuditReport() throws Exception {
        var purgeResult = new PurgeResult(
                "mem-101", "default", true, Instant.now(),
                512, 128, false, 0, 0,
                3, true, 1, 0, true,
                PurgeResult.RETAINED_HEADER_FIELDS,
                PurgeResult.UNREACHABLE_COPIES,
                PurgeResult.DISCLOSURE_TEXT
        );
        when(memory.purge("mem-101")).thenReturn(purgeResult);

        McpSchema.CallToolResult result = tool.execute(Map.of("memory_id", "mem-101"));

        assertThat(result.isError()).isFalse();
        assertThat(result.content()).hasSize(1);
        String text = ((McpSchema.TextContent) result.content().get(0)).text();

        assertThat(text)
                .contains("Memory 'mem-101' was PURGED — this is irreversible")
                .contains("512 payload bytes overwritten with zeros")
                .contains("128 text bytes overwritten with zeros")
                .contains("5 graph references removed (3 Hebbian, 1 temporal, 1 entity, 0 hyperedge)")
                .contains("Not reached by this operation: dr_exports, replica_disks");

        verify(memory).purge("mem-101");
    }

    @Test
    @DisplayName("Purge with retained shared text includes prominent warning")
    void purgeWithLocalRetention_warnsAboutRetainedSharedText() throws Exception {
        var purgeResult = new PurgeResult(
                "mem-shared", "default", true, Instant.now(),
                512, 0, false, 64, 2,
                0, false, 0, 0, true,
                PurgeResult.RETAINED_HEADER_FIELDS,
                PurgeResult.UNREACHABLE_COPIES,
                PurgeResult.DISCLOSURE_TEXT
        );
        when(memory.purge("mem-shared")).thenReturn(purgeResult);

        McpSchema.CallToolResult result = tool.execute(Map.of("memory_id", "mem-shared"));

        assertThat(result.isError()).isFalse();
        String text = ((McpSchema.TextContent) result.content().get(0)).text();

        assertThat(text)
                .contains("⚠️ TEXT RETAINED: identical text is shared with 2 other live record(s)");
    }

    @Test
    @DisplayName("Purge of nonexistent ID returns not found notice without error")
    void purgeNotFound_returnsNotFoundNotice() throws Exception {
        when(memory.purge("missing-id")).thenReturn(PurgeResult.notFound("missing-id", "default"));

        McpSchema.CallToolResult result = tool.execute(Map.of("memory_id", "missing-id"));

        assertThat(result.isError()).isFalse();
        String text = ((McpSchema.TextContent) result.content().get(0)).text();
        assertThat(text).contains("No memory found with id 'missing-id'. Nothing was purged.");
    }

    @Test
    @DisplayName("Purge under legal hold propagates exception across tool boundary (Task 2.4a)")
    void purgeUnderLegalHold_throwsException() {
        when(memory.purge("held-mem"))
                .thenThrow(new IllegalStateException("Cannot verify legal hold for namespace 'held-ns'; refusing PURGE"));

        assertThatThrownBy(() -> tool.execute(Map.of("memory_id", "held-mem")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot verify legal hold");
    }
}
