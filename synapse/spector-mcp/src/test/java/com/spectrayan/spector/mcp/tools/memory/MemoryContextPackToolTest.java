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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.spectrayan.spector.mcp.tools.McpToolHandler.McpToolCategory;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.FactHistory;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.RecallOptions;

import io.modelcontextprotocol.spec.McpSchema;

/**
 * Unit tests for {@link MemoryContextPackTool}.
 */
class MemoryContextPackToolTest {

    private SpectorMemory memory;
    private MemoryContextPackTool tool;

    @BeforeEach
    void setUp() {
        memory = mock(SpectorMemory.class);
        tool = new MemoryContextPackTool(memory);
    }

    @Test
    void metadataAndSchema_areValid() {
        assertThat(tool.name()).isEqualTo("memory_context_pack");
        assertThat(tool.category()).isEqualTo(McpToolCategory.MEMORY);
        assertThat(tool.isWriteTool()).isFalse();
        assertThat(tool.requiredScopes()).contains(com.spectrayan.spector.commons.security.SpectorScopes.MEMORY_READ);
        assertThat(tool.description()).contains("hierarchical memory context pack");

        Map<String, Object> schema = tool.inputSchema();
        assertThat(schema).containsKey("properties");
        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        assertThat(props).containsKeys("query", "token_budget", "profile", "persona_id", "as_of", "conflict_mode");
    }

    @Test
    void execute_assemblesContextPackSuccessfully() throws Exception {
        CognitiveResult working = mock(CognitiveResult.class);
        when(working.id()).thenReturn("work-1");
        when(working.text()).thenReturn("Discussing startup founding timeline");
        when(working.memoryType()).thenReturn(MemoryType.WORKING);

        CognitiveResult proc = mock(CognitiveResult.class);
        when(proc.id()).thenReturn("proc-1");
        when(proc.text()).thenReturn("Validate anxiety before offering advice");
        when(proc.memoryType()).thenReturn(MemoryType.PROCEDURAL);
        when(proc.score()).thenReturn(0.92f);
        when(proc.valence()).thenReturn((byte) 50);

        CognitiveResult sem = mock(CognitiveResult.class);
        when(sem.id()).thenReturn("sem-1");
        when(sem.text()).thenReturn("Founded first AI laboratory in Austin");
        when(sem.memoryType()).thenReturn(MemoryType.SEMANTIC);
        when(sem.synapticTags()).thenReturn(new String[]{"career", "lab"});

        CognitiveResult epi = mock(CognitiveResult.class);
        when(epi.id()).thenReturn("epi-1");
        when(epi.text()).thenReturn("Late night brainstorming at the kitchen table");
        when(epi.memoryType()).thenReturn(MemoryType.EPISODIC);
        when(epi.ltpAdjustedDecay()).thenReturn(0.88f);
        when(epi.ageDays()).thenReturn(120.5f);

        when(memory.recall(eq("How did we start the lab?"), any(RecallOptions.class)))
                .thenReturn(List.of(working, proc, sem, epi));

        McpSchema.CallToolResult result = tool.execute(null, Map.of(
                "query", "How did we start the lab?",
                "token_budget", 2500,
                "profile", "BALANCED",
                "persona_id", "persona-founder-001"
        ));

        assertThat(result.isError()).isFalse();
        String text = ((McpSchema.TextContent) result.content().get(0)).text();

        assertThat(text).contains("# === SPECTOR COGNITIVE CONTEXT PACK ===");
        assertThat(text).contains("**Persona:** `persona-founder-001`");
        assertThat(text).contains("## 1. ACTIVE WORKING INTENT & SCRATCHPAD");
        assertThat(text).contains("[Working #work-1]: Discussing startup founding timeline");
        assertThat(text).contains("## 2. PROCEDURAL HEURISTICS & DECISION CADENCE (Basal Ganglia)");
        assertThat(text).contains("[Skill #proc-1]: Validate anxiety before offering advice");
        assertThat(text).contains("## 3. CORE SEMANTIC FACTS & AXIOMS (Neocortex)");
        assertThat(text).contains("[Fact #sem-1]: Founded first AI laboratory in Austin");
        assertThat(text).contains("## 4. CHRONO-EPISODIC MEMORIES & EXPERIENCES (Hippocampus)");
        assertThat(text).contains("[Episode #epi-1]: Late night brainstorming at the kitchen table");
        assertThat(text).contains("# === END COGNITIVE CONTEXT PACK ===");

        ArgumentCaptor<RecallOptions> captor = ArgumentCaptor.forClass(RecallOptions.class);
        verify(memory).recall(eq("How did we start the lab?"), captor.capture());
        assertThat(captor.getValue().topK()).isGreaterThanOrEqualTo(10);
    }
}
