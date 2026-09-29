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

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.spectrayan.spector.commons.security.SpectorScopes;
import com.spectrayan.spector.mcp.tools.McpToolHandler.McpToolCategory;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.graph.GraphHealthMetrics;
import com.spectrayan.spector.memory.model.ReflectReport;
import com.spectrayan.spector.memory.pathway.reflect.ReflectSweepSpec;

import io.modelcontextprotocol.spec.McpSchema;

/**
 * Unit tests for {@link MemoryReflectTool}.
 */
@DisplayName("MemoryReflectTool — MCP Tool Specification")
class MemoryReflectToolTest {

    private SpectorMemory memory;
    private MemoryReflectTool tool;

    @BeforeEach
    void setUp() {
        memory = mock(SpectorMemory.class);
        tool = new MemoryReflectTool(memory);
    }

    @Test
    @DisplayName("Metadata and schema conform to write tool specifications")
    void metadataAndSchema_areValid() {
        assertThat(tool.name()).isEqualTo("memory_reflect");
        assertThat(tool.category()).isEqualTo(McpToolCategory.MEMORY);
        assertThat(tool.isWriteTool()).isTrue();
        assertThat(tool.requiredScopes()).contains(SpectorScopes.MEMORY_WRITE);
        assertThat(tool.inputSchema()).containsKey("properties");
    }

    @Test
    @DisplayName("Default reflect with no args delegates to memory.reflect()")
    void defaultReflect_delegatesToMemoryReflect() throws Exception {
        when(memory.reflect()).thenReturn(ReflectReport.EMPTY);

        McpSchema.CallToolResult result = tool.execute(Map.of());

        assertThat(result.isError()).isFalse();
        verify(memory).reflect();
    }

    @Test
    @DisplayName("Reflect with activity returns detailed consolidation stats")
    void reflectWithActivity_showsDetailedStats() throws Exception {
        var report = new ReflectReport(
                5, 3, 1, 7,
                Duration.ofMillis(142),
                null,
                0, 0, 0.0f, 34, null
        );
        when(memory.reflect()).thenReturn(report);

        McpSchema.CallToolResult result = tool.execute(Map.of());

        assertThat(result.isError()).isFalse();
        assertThat(result.content()).hasSize(1);
        String text = ((McpSchema.TextContent) result.content().get(0)).text();

        assertThat(text)
                .contains("Sleep Consolidation")
                .contains("142 ms")
                .contains("5")    // consolidatedCount
                .contains("34")   // logTurnsConsolidated
                .contains("3")    // tombstonedCount
                .contains("7");   // temporalPrunedCount
    }

    @Test
    @DisplayName("Empty reflect report shows no activity message")
    void reflectNoActivity_showsNoWorkMessage() throws Exception {
        when(memory.reflect()).thenReturn(ReflectReport.EMPTY);

        McpSchema.CallToolResult result = tool.execute(Map.of());

        assertThat(result.isError()).isFalse();
        String text = ((McpSchema.TextContent) result.content().get(0)).text();

        assertThat(text).contains("No activity");
    }

    @Test
    @DisplayName("Reflect with graph health includes graph summary")
    void reflectWithGraphHealth_includesGraphMetrics() throws Exception {
        var graphHealth = new GraphHealthMetrics();
        graphHealth.recordHebbianDecay();
        graphHealth.recordHebbianDecay();
        graphHealth.recordHebbianSurvivor(100, 5);
        graphHealth.recordEntitySurvivor(50);

        var report = new ReflectReport(
                2, 1, 0, 0,
                Duration.ofMillis(87),
                graphHealth,
                0, 0, 0.0f, 10, null
        );
        when(memory.reflect()).thenReturn(report);

        McpSchema.CallToolResult result = tool.execute(Map.of());

        assertThat(result.isError()).isFalse();
        String text = ((McpSchema.TextContent) result.content().get(0)).text();

        assertThat(text)
                .contains("Graph Health")
                .contains("hebbian");
    }

    @Test
    @DisplayName("consolidation_only=true builds consolidation-only spec")
    void consolidationOnly_buildsCorrectSpec() throws Exception {
        when(memory.reflect(any(ReflectSweepSpec.class))).thenReturn(ReflectReport.EMPTY);

        tool.execute(Map.of("consolidation_only", true, "session_limit", 10));

        verify(memory).reflect(any(ReflectSweepSpec.class));
    }

    @Test
    @DisplayName("session_limit without consolidation_only builds full-cycle spec with limit")
    void sessionLimit_buildsFullCycleWithLimit() throws Exception {
        when(memory.reflect(any(ReflectSweepSpec.class))).thenReturn(ReflectReport.EMPTY);

        tool.execute(Map.of("session_limit", 5));

        verify(memory).reflect(any(ReflectSweepSpec.class));
    }

    @Test
    @DisplayName("Soul drift re-fusion is displayed when detected")
    void reflectWithSoulDrift_showsSoulDriftSection() throws Exception {
        var report = new ReflectReport(
                1, 0, 0, 0,
                Duration.ofMillis(200),
                null,
                3, 2, 0.15f, 5, null
        );
        when(memory.reflect()).thenReturn(report);

        McpSchema.CallToolResult result = tool.execute(Map.of());

        assertThat(result.isError()).isFalse();
        String text = ((McpSchema.TextContent) result.content().get(0)).text();

        assertThat(text)
                .contains("Soul Drift")
                .contains("3")    // soulDriftedCount
                .contains("2");   // soulRefusedCount
    }

    @Test
    @DisplayName("buildTemplateModel populates all expected keys")
    void buildTemplateModel_populatesAllKeys() {
        var report = new ReflectReport(
                10, 5, 2, 3,
                Duration.ofMillis(500),
                null,
                1, 1, 0.05f, 20, null
        );

        Map<String, Object> model = MemoryReflectTool.buildTemplateModel(report);

        assertThat(model)
                .containsEntry("durationMs", 500L)
                .containsEntry("consolidatedCount", 10)
                .containsEntry("tombstonedCount", 5)
                .containsEntry("compactedPartitions", 2)
                .containsEntry("temporalPrunedCount", 3)
                .containsEntry("logTurnsConsolidated", 20)
                .containsEntry("hadActivity", true)
                .containsEntry("hasSoulDrift", true)
                .containsEntry("soulDriftedCount", 1)
                .containsEntry("soulRefusedCount", 1)
                .containsEntry("hasGraphHealth", false);
    }
}
