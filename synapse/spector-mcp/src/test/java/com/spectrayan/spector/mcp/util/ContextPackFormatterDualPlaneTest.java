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
package com.spectrayan.spector.mcp.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.spectrayan.spector.mcp.util.ContextPackFormatter.ContextPackInput;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.FactHistory;
import com.spectrayan.spector.memory.model.FactHistory.FactSnapshot;
import com.spectrayan.spector.kernel.api.MemoryType;

/**
 * Tests for the dual-plane context pack formatting (#1018).
 *
 * <p>Verifies that {@code formatStaticPrefix()} and {@code formatDynamicTail()}
 * correctly partition memories by stability characteristics, enabling LLM prompt
 * prefix cache efficiency.</p>
 */
@DisplayName("Dual-Plane Cache-Preserving Context Pack Formatter")
class ContextPackFormatterDualPlaneTest {

    // ── Test fixtures ──

    private static CognitiveResult workingMemory(String id, String text) {
        CognitiveResult r = mock(CognitiveResult.class);
        when(r.id()).thenReturn(id);
        when(r.text()).thenReturn(text);
        when(r.memoryType()).thenReturn(MemoryType.WORKING);
        return r;
    }

    private static CognitiveResult proceduralMemory(String id, String text) {
        CognitiveResult r = mock(CognitiveResult.class);
        when(r.id()).thenReturn(id);
        when(r.text()).thenReturn(text);
        when(r.memoryType()).thenReturn(MemoryType.PROCEDURAL);
        when(r.score()).thenReturn(0.9f);
        when(r.valence()).thenReturn((byte) 0);
        return r;
    }

    private static CognitiveResult semanticMemory(String id, String text, float importance) {
        CognitiveResult r = mock(CognitiveResult.class);
        when(r.id()).thenReturn(id);
        when(r.text()).thenReturn(text);
        when(r.memoryType()).thenReturn(MemoryType.SEMANTIC);
        when(r.importance()).thenReturn(importance);
        when(r.synapticTags()).thenReturn(new String[]{"test"});
        return r;
    }

    private static CognitiveResult episodicMemory(String id, String text) {
        CognitiveResult r = mock(CognitiveResult.class);
        when(r.id()).thenReturn(id);
        when(r.text()).thenReturn(text);
        when(r.memoryType()).thenReturn(MemoryType.EPISODIC);
        when(r.ltpAdjustedDecay()).thenReturn(0.95f);
        when(r.ageDays()).thenReturn(1.0f);
        return r;
    }

    // ── Static Prefix Tests ──

    @Test
    @DisplayName("Static prefix contains persona, procedural, and high-importance semantic — no working/episodic")
    void staticPrefixContainsStableContentOnly() {
        var input = new ContextPackInput(
                "query",
                "active intent",
                List.of(
                        workingMemory("w-1", "scratch pad note"),
                        proceduralMemory("p-1", "Always run tests before committing"),
                        semanticMemory("s-high", "Spector uses Panama FFM for off-heap", 8.0f),
                        semanticMemory("s-low", "Some context-specific detail", 2.0f),
                        episodicMemory("e-1", "Deployed v1.5 last Tuesday")
                ),
                List.of(),
                2000,
                "BALANCED",
                "persona-forge"
        );

        String prefix = ContextPackFormatter.formatStaticPrefix(input);

        // Static prefix should contain:
        assertThat(prefix).contains("# === SPECTOR COGNITIVE CONTEXT PACK ===");
        assertThat(prefix).contains("**Persona:** `persona-forge`");
        assertThat(prefix).contains("## 2. PROCEDURAL HEURISTICS");
        assertThat(prefix).contains("[Skill #p-1]: Always run tests before committing");
        assertThat(prefix).contains("## 3. CORE SEMANTIC FACTS & AXIOMS");
        assertThat(prefix).contains("[Fact #s-high]: Spector uses Panama FFM for off-heap");

        // Static prefix should NOT contain volatile content:
        assertThat(prefix).doesNotContain("scratch pad note");
        assertThat(prefix).doesNotContain("w-1");
        assertThat(prefix).doesNotContain("Some context-specific detail");
        assertThat(prefix).doesNotContain("s-low");
        assertThat(prefix).doesNotContain("Deployed v1.5 last Tuesday");
        assertThat(prefix).doesNotContain("e-1");
        assertThat(prefix).doesNotContain("WORKING INTENT");
        assertThat(prefix).doesNotContain("EPISODIC");
    }

    @Test
    @DisplayName("Dynamic tail contains working, episodic, low-importance semantic, and facts — no procedural")
    void dynamicTailContainsVolatileContentOnly() {
        FactHistory fh = new FactHistory(
                "Spector", "version",
                new FactSnapshot(1, "1.6.0", 1700000000L, Long.MAX_VALUE, 1700000100L, 0.99f, -1),
                List.of(), 1
        );

        var input = new ContextPackInput(
                "query",
                "debug the NPE",
                List.of(
                        workingMemory("w-1", "checking auth flow"),
                        proceduralMemory("p-1", "Always run tests"),
                        semanticMemory("s-high", "Core axiom", 8.0f),
                        semanticMemory("s-low", "Turn-specific detail", 2.0f),
                        episodicMemory("e-1", "Last deploy crashed")
                ),
                List.of(fh),
                2000,
                "BALANCED",
                "persona-forge"
        );

        String tail = ContextPackFormatter.formatDynamicTail(input);

        // Dynamic tail should contain:
        assertThat(tail).contains("## 1. ACTIVE WORKING INTENT & SCRATCHPAD");
        assertThat(tail).contains("[Turn Intent]: debug the NPE");
        assertThat(tail).contains("[Working #w-1]: checking auth flow");
        assertThat(tail).contains("## 3b. TURN-RELEVANT SEMANTIC MATCHES");
        assertThat(tail).contains("[Fact #s-low]: Turn-specific detail");
        assertThat(tail).contains("## 4. CHRONO-EPISODIC MEMORIES");
        assertThat(tail).contains("[Episode #e-1]: Last deploy crashed");
        assertThat(tail).contains("## 5. BITEMPORAL EVIDENCE");
        assertThat(tail).contains("Active Consensus: `1.6.0`");
        assertThat(tail).contains("# === END COGNITIVE CONTEXT PACK ===");

        // Dynamic tail should NOT contain stable content:
        assertThat(tail).doesNotContain("PROCEDURAL");
        assertThat(tail).doesNotContain("p-1");
        assertThat(tail).doesNotContain("Core axiom");
        assertThat(tail).doesNotContain("s-high");
    }

    // ── Cache Stability Test ──

    @Test
    @DisplayName("Static prefix is byte-identical across calls with different queries and episodic recalls")
    void staticPrefixIsByteIdenticalAcrossTurns() {
        CognitiveResult proc = proceduralMemory("p-1", "Always run tests");
        CognitiveResult semHigh = semanticMemory("s-1", "Core architecture fact", 9.0f);

        // Turn 1: query about "design" with episodic recall about deployment
        var input1 = new ContextPackInput(
                "What is the system design?",
                "Reviewing architecture",
                List.of(proc, semHigh,
                        episodicMemory("e-1", "Deployed v1.5"),
                        semanticMemory("s-low-1", "Design detail A", 2.0f)),
                List.of(),
                2000, "BALANCED", "persona-architect"
        );

        // Turn 2: completely different query and different episodic/dynamic recalls
        var input2 = new ContextPackInput(
                "How to fix the NPE in AuthValidator?",
                "Debugging auth issue",
                List.of(proc, semHigh,
                        episodicMemory("e-2", "Auth crash last week"),
                        semanticMemory("s-low-2", "Auth detail B", 1.0f)),
                List.of(),
                2000, "BALANCED", "persona-architect"
        );

        String prefix1 = ContextPackFormatter.formatStaticPrefix(input1);
        String prefix2 = ContextPackFormatter.formatStaticPrefix(input2);

        // Byte-identical: the key property that enables prompt prefix caching
        assertThat(prefix1).isEqualTo(prefix2);
    }

    @Test
    @DisplayName("Dynamic tail captures turn-specific results and differs across queries")
    void dynamicTailDiffersAcrossTurns() {
        CognitiveResult proc = proceduralMemory("p-1", "Always run tests");
        CognitiveResult semHigh = semanticMemory("s-1", "Core fact", 9.0f);

        var input1 = new ContextPackInput(
                "design question",
                "Intent A",
                List.of(proc, semHigh, episodicMemory("e-1", "Episode A")),
                List.of(), 2000, "BALANCED", "persona-dev"
        );

        var input2 = new ContextPackInput(
                "debug question",
                "Intent B",
                List.of(proc, semHigh, episodicMemory("e-2", "Episode B")),
                List.of(), 2000, "BALANCED", "persona-dev"
        );

        String tail1 = ContextPackFormatter.formatDynamicTail(input1);
        String tail2 = ContextPackFormatter.formatDynamicTail(input2);

        assertThat(tail1).isNotEqualTo(tail2);
        assertThat(tail1).contains("Intent A").contains("Episode A");
        assertThat(tail2).contains("Intent B").contains("Episode B");
    }

    // ── Backward Compatibility ──

    @Test
    @DisplayName("format() concatenates static prefix and dynamic tail")
    void formatConcatenatesBothPlanes() {
        var input = new ContextPackInput(
                "query",
                "intent",
                List.of(
                        proceduralMemory("p-1", "Skill text"),
                        semanticMemory("s-1", "High importance fact", 9.0f),
                        workingMemory("w-1", "Scratch"),
                        episodicMemory("e-1", "Episode")
                ),
                List.of(),
                2000, "BALANCED", "persona-dev"
        );

        String full = ContextPackFormatter.format(input);
        String prefix = ContextPackFormatter.formatStaticPrefix(input);
        String tail = ContextPackFormatter.formatDynamicTail(input);

        assertThat(full).isEqualTo(prefix + tail);
    }

    @Test
    @DisplayName("7-arg constructor backward compatibility with default threshold")
    void sevenArgConstructorUsesDefaultThreshold() {
        var input = new ContextPackInput(
                "query", "intent", List.of(), List.of(), 2000, "BALANCED", "persona"
        );

        assertThat(input.staticImportanceThreshold())
                .isEqualTo(ContextPackFormatter.DEFAULT_STATIC_IMPORTANCE_THRESHOLD);
    }

    @Test
    @DisplayName("Custom importance threshold shifts semantic memories between planes")
    void customThresholdControlsSplit() {
        CognitiveResult midImportance = semanticMemory("s-mid", "Mid importance", 4.0f);

        // Threshold at 3.0 → mid-importance goes to static prefix
        var lowThresholdInput = new ContextPackInput(
                "q", "i", List.of(midImportance), List.of(), 2000, "B", "p", 3.0f
        );
        assertThat(ContextPackFormatter.formatStaticPrefix(lowThresholdInput)).contains("s-mid");
        assertThat(ContextPackFormatter.formatDynamicTail(lowThresholdInput)).doesNotContain("s-mid");

        // Threshold at 6.0 → mid-importance goes to dynamic tail
        var highThresholdInput = new ContextPackInput(
                "q", "i", List.of(midImportance), List.of(), 2000, "B", "p", 6.0f
        );
        assertThat(ContextPackFormatter.formatStaticPrefix(highThresholdInput)).doesNotContain("s-mid");
        assertThat(ContextPackFormatter.formatDynamicTail(highThresholdInput)).contains("s-mid");
    }

    @Test
    @DisplayName("Empty recalled memories produce placeholder messages in both planes")
    void emptyMemoriesShowPlaceholders() {
        var input = new ContextPackInput(
                "query", null, List.of(), List.of(), 2000, "BALANCED", null
        );

        String prefix = ContextPackFormatter.formatStaticPrefix(input);
        String tail = ContextPackFormatter.formatDynamicTail(input);

        assertThat(prefix).contains("_No specialized procedural skill triggered");
        assertThat(prefix).contains("_No high-confidence semantic axioms");
        assertThat(tail).contains("_No active working scratchpad note._");
        assertThat(tail).contains("_No episodic memories recalled");
    }

    @Test
    @DisplayName("formatSplit() renders static and dynamic planes separated by cache boundary")
    void formatSplitSeparatesPlanesWithBoundaryMarker() {
        var input = new ContextPackInput(
                "query",
                "active intent",
                List.of(
                        proceduralMemory("p-1", "Always run tests"),
                        semanticMemory("s-1", "Core axiom", 9.0f),
                        workingMemory("w-1", "Scratch note"),
                        episodicMemory("e-1", "Episode text")
                ),
                List.of(),
                2000, "BALANCED", "persona-dev"
        );

        String split = ContextPackFormatter.formatSplit(input);

        assertThat(split).contains("# === SPECTOR COGNITIVE CONTEXT PACK ===");
        assertThat(split).contains("<!-- SPECTOR_CACHE_BOUNDARY -->");
        assertThat(split).contains("## 1. ACTIVE WORKING INTENT & SCRATCHPAD");
        assertThat(split).contains("# === END COGNITIVE CONTEXT PACK ===");

        String[] parts = split.split("<!-- SPECTOR_CACHE_BOUNDARY -->\\n?");
        assertThat(parts).hasSize(2);
        assertThat(parts[0]).contains("Always run tests");
        assertThat(parts[0]).contains("Core axiom");
        assertThat(parts[1]).contains("Scratch note");
        assertThat(parts[1]).contains("Episode text");
    }

    @Test
    @DisplayName("McpTemplateEngine has compiled templates for context pack planes")
    void templatesExistInMcpTemplateEngine() {
        assertThat(McpTemplateEngine.engine().hasTemplate("memory-context-pack-static")).isTrue();
        assertThat(McpTemplateEngine.engine().hasTemplate("memory-context-pack-dynamic")).isTrue();
        assertThat(McpTemplateEngine.engine().hasTemplate("memory-context-pack")).isTrue();
        assertThat(McpTemplateEngine.engine().hasTemplate("memory-context-pack-split")).isTrue();
    }
}
