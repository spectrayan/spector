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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.spectrayan.spector.commons.pathway.ConductionOutcome;
import com.spectrayan.spector.commons.security.SpectorScopes;
import com.spectrayan.spector.kernel.api.DreamMode;
import com.spectrayan.spector.mcp.tools.McpToolHandler.McpToolCategory;
import com.spectrayan.spector.mcp.tools.McpToolHandler.ToolArgumentException;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.pathway.dream.relay.DreamReport;

import io.modelcontextprotocol.spec.McpSchema;

/**
 * Unit tests for {@link MemoryDreamTool}.
 */
@DisplayName("MemoryDreamTool — MCP Tool Specification")
class MemoryDreamToolTest {

    private SpectorMemory memory;
    private MemoryDreamTool tool;

    @BeforeEach
    void setUp() {
        memory = mock(SpectorMemory.class);
        tool = new MemoryDreamTool(memory);
    }

    @Test
    @DisplayName("Metadata and schema conform to write tool specifications")
    void metadataAndSchema_areValid() {
        assertThat(tool.name()).isEqualTo("memory_dream");
        assertThat(tool.category()).isEqualTo(McpToolCategory.MEMORY);
        assertThat(tool.isWriteTool()).isTrue();
        assertThat(tool.requiredScopes()).contains(SpectorScopes.MEMORY_WRITE);
        assertThat(tool.inputSchema()).containsKey("properties");
    }

    @Test
    @DisplayName("Default execution without mode parameter defaults to REM")
    void defaultExecution_invokesDreamWithRemMode() throws Exception {
        when(memory.dream(any())).thenReturn(DreamReport.empty());

        McpSchema.CallToolResult result = tool.execute(Map.of());

        assertThat(result.isError()).isFalse();
        verify(memory).dream(DreamMode.REM);
    }

    @ParameterizedTest(name = "Mode input: {0} resolves to: {1}")
    @CsvSource({
            "REM, REM",
            "rem, REM",
            "DAYDREAM, DAYDREAM",
            "daydream, DAYDREAM",
            "DayDream, DAYDREAM",
            "THOUGHT_EXPERIMENT, THOUGHT_EXPERIMENT",
            "thought_experiment, THOUGHT_EXPERIMENT"
    })
    @DisplayName("Explicit mode string is correctly parsed and passed to memory.dream()")
    void explicitModes_areCorrectlyParsed(String modeInput, DreamMode expectedMode) throws Exception {
        when(memory.dream(any())).thenReturn(DreamReport.empty());

        McpSchema.CallToolResult result = tool.execute(Map.of("mode", modeInput));

        assertThat(result.isError()).isFalse();
        verify(memory).dream(expectedMode);
    }

    @Test
    @DisplayName("Invalid dream mode throws ToolArgumentException")
    void invalidMode_throwsToolArgumentException() {
        assertThatThrownBy(() -> tool.execute(Map.of("mode", "NIGHTMARE")))
                .isInstanceOf(ToolArgumentException.class)
                .hasMessageContaining("Invalid dream mode: 'NIGHTMARE'");
    }

    @Test
    @DisplayName("Report formatting correctly renders telemetry in output text")
    void reportFormatting_rendersExpectedTelemetry() throws Exception {
        DreamReport report = new DreamReport(
                10,
                4,
                3,
                2,
                1,
                0,
                Duration.ofMillis(120),
                DreamMode.REM,
                new ConductionOutcome()
        );
        when(memory.dream(DreamMode.REM)).thenReturn(report);

        McpSchema.CallToolResult result = tool.execute(Map.of());

        assertThat(result.isError()).isFalse();
        assertThat(result.content()).hasSize(1);
        String text = ((McpSchema.TextContent) result.content().get(0)).text();

        assertThat(text)
                .contains("Generative Dream Replay Cycle Complete")
                .contains("Mode:     REM")
                .contains("Duration: 120 ms")
                .contains("Outcome:  COMPLETED")
                .contains("Seeds sampled:           10")
                .contains("Scenes constructed:      4")
                .contains("Scenes triaged:          3")
                .contains("Novel insights ingested: 2")
                .contains("Journal entries written: 1")
                .contains("Failed pairs inhibited:  0");
    }

    @Test
    @DisplayName("Supplier constructor creates tool with correct name")
    void supplierConstructor_hasCorrectName() {
        var supplierTool = new MemoryDreamTool(() -> memory);
        assertThat(supplierTool.name()).isEqualTo("memory_dream");
    }
}
