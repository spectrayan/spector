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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.pathway.skill.model.SkillBody;
import com.spectrayan.spector.memory.pathway.skill.model.SkillKind;
import com.spectrayan.spector.memory.pathway.skill.model.SkillMeta;
import com.spectrayan.spector.memory.pathway.skill.relay.SkillReport;
import com.spectrayan.spector.memory.pathway.skill.relay.SkillSignal;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ADR-0086 §5.6, §7 Phase 5: MemoryCompileSkillTool Tests")
class MemoryCompileSkillToolTest {

    private SpectorMemory memory;
    private MemoryCompileSkillTool tool;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @BeforeEach
    void setUp() {
        memory = mock(SpectorMemory.class);
        tool = new MemoryCompileSkillTool(memory);
    }

    @Test
    @DisplayName("Dry-run execution previews spector.skill.v1 without committing")
    void dryRunExecution() throws Exception {
        SkillBody body = new SkillBody(
                new SkillMeta(SkillMeta.SCHEMA_V1, "clean-build-flow", SkillKind.PLAYBOOK, 0.35f, List.of("mvn"), Map.of()),
                "# Clean Build Flow\n\nRun mvn clean compile."
        );
        SkillReport report = new SkillReport(
                null,
                List.of(),
                null,
                body,
                SkillSignal.Mode.DRY_RUN,
                false
        );
        when(memory.compileSkill(any(SkillSignal.class))).thenReturn(report);

        Map<String, Object> args = Map.of(
                "cue", "Clean build flow",
                "parents", List.of("ep-1", "ep-2"),
                "commit", false
        );

        McpSchema.CallToolResult result = tool.execute(args);
        assertThat(result.isError()).isFalse();

        var content = (McpSchema.TextContent) result.content().get(0);
        @SuppressWarnings("unchecked")
        Map<String, Object> respMap = MAPPER.readValue(content.text(), Map.class);

        assertThat(respMap.get("mode")).isEqualTo("DRY_RUN");
        assertThat(respMap.get("skillId")).isNull();
        assertThat(respMap.get("duplicateOf")).isNull();
        assertThat(respMap.get("serializedSkill").toString()).contains("schema: spector.skill.v1");
        assertThat(respMap.get("serializedSkill").toString()).contains("name: clean-build-flow");

        ArgumentCaptor<SkillSignal> signalCaptor = ArgumentCaptor.forClass(SkillSignal.class);
        verify(memory).compileSkill(signalCaptor.capture());
        SkillSignal captured = signalCaptor.getValue();
        assertThat(captured.mode()).isEqualTo(SkillSignal.Mode.DRY_RUN);
        assertThat(captured.commit()).isFalse();
    }

    @Test
    @DisplayName("Compile execution with commit=true persists skill")
    void compileExecutionWithCommit() throws Exception {
        SkillBody body = new SkillBody(
                new SkillMeta(SkillMeta.SCHEMA_V1, "fix-test-race", SkillKind.HEURISTIC, 0.35f, List.of(), Map.of()),
                "# Fix Test Race\n\nUse CountDownLatch instead of Thread.sleep."
        );
        SkillReport report = new SkillReport(
                "skill-crockford-1",
                List.of(),
                null,
                body,
                SkillSignal.Mode.COMPILE,
                false
        );
        when(memory.compileSkill(any(SkillSignal.class))).thenReturn(report);

        Map<String, Object> args = Map.of(
                "cue", "Fix test race",
                "parents", List.of("sem-1", "sem-2", "sem-3"),
                "commit", true
        );

        McpSchema.CallToolResult result = tool.execute(args);
        assertThat(result.isError()).isFalse();

        var content = (McpSchema.TextContent) result.content().get(0);
        @SuppressWarnings("unchecked")
        Map<String, Object> respMap = MAPPER.readValue(content.text(), Map.class);

        assertThat(respMap.get("mode")).isEqualTo("COMPILE");
        assertThat(respMap.get("skillId")).isEqualTo("skill-crockford-1");

        ArgumentCaptor<SkillSignal> signalCaptor = ArgumentCaptor.forClass(SkillSignal.class);
        verify(memory).compileSkill(signalCaptor.capture());
        SkillSignal captured = signalCaptor.getValue();
        assertThat(captured.mode()).isEqualTo(SkillSignal.Mode.COMPILE);
        assertThat(captured.commit()).isTrue();
    }
}
