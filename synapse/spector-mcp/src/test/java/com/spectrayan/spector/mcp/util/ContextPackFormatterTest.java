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

import org.junit.jupiter.api.Test;

import com.spectrayan.spector.mcp.util.ContextPackFormatter.ContextPackInput;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.FactHistory;
import com.spectrayan.spector.memory.model.FactHistory.FactSnapshot;
import com.spectrayan.spector.memory.model.MemoryType;

/**
 * Unit tests for {@link ContextPackFormatter}.
 */
class ContextPackFormatterTest {

    @Test
    void format_producesStructuredMarkdownWithAllTiers() {
        CognitiveResult working = mock(CognitiveResult.class);
        when(working.id()).thenReturn("w-1");
        when(working.text()).thenReturn("Active plan: write unit tests");
        when(working.memoryType()).thenReturn(MemoryType.WORKING);

        CognitiveResult proc = mock(CognitiveResult.class);
        when(proc.id()).thenReturn("p-1");
        when(proc.text()).thenReturn("Always assert on edge conditions first");
        when(proc.memoryType()).thenReturn(MemoryType.PROCEDURAL);
        when(proc.score()).thenReturn(0.95f);
        when(proc.valence()).thenReturn((byte) 40);

        CognitiveResult sem = mock(CognitiveResult.class);
        when(sem.id()).thenReturn("s-1");
        when(sem.text()).thenReturn("Spector is a Zero-GC cognitive memory substrate");
        when(sem.memoryType()).thenReturn(MemoryType.SEMANTIC);
        when(sem.synapticTags()).thenReturn(new String[]{"architecture", "zero-gc"});

        CognitiveResult epi = mock(CognitiveResult.class);
        when(epi.id()).thenReturn("e-1");
        when(epi.text()).thenReturn("Passed all 500 regression tests on commit #42");
        when(epi.memoryType()).thenReturn(MemoryType.EPISODIC);
        when(epi.ltpAdjustedDecay()).thenReturn(0.99f);
        when(epi.ageDays()).thenReturn(2.5f);

        FactHistory fh = new FactHistory(
                "Spector", "license",
                new FactSnapshot(1, "BSL 1.1", 1700000000L, Long.MAX_VALUE, 1700000100L, 0.99f, -1),
                List.of(), 1
        );

        ContextPackInput input = new ContextPackInput(
                "What is the system design?",
                "Test the context pack",
                List.of(working, proc, sem, epi),
                List.of(fh),
                2000,
                "BALANCED",
                "persona-architect-01"
        );

        String result = ContextPackFormatter.format(input);

        assertThat(result).contains("# === SPECTOR COGNITIVE CONTEXT PACK ===");
        assertThat(result).contains("**Persona:** `persona-architect-01`");
        assertThat(result).contains("**Profile:** `BALANCED`");
        assertThat(result).contains("## 1. ACTIVE WORKING INTENT & SCRATCHPAD");
        assertThat(result).contains("[Turn Intent]: Test the context pack");
        assertThat(result).contains("[Working #w-1]: Active plan: write unit tests");
        assertThat(result).contains("## 2. PROCEDURAL HEURISTICS & DECISION CADENCE (Basal Ganglia)");
        assertThat(result).contains("[Skill #p-1]: Always assert on edge conditions first");
        assertThat(result).contains("## 3. CORE SEMANTIC FACTS & AXIOMS (Neocortex)");
        assertThat(result).contains("[Fact #s-1]: Spector is a Zero-GC cognitive memory substrate");
        assertThat(result).contains("Tags: [architecture, zero-gc]");
        assertThat(result).contains("## 4. CHRONO-EPISODIC MEMORIES & EXPERIENCES (Hippocampus)");
        assertThat(result).contains("[Episode #e-1]: Passed all 500 regression tests on commit #42");
        assertThat(result).contains("## 5. BITEMPORAL EVIDENCE TRANSITIONS & CONFLICTS");
        assertThat(result).contains("Active Consensus: `BSL 1.1`");
        assertThat(result).contains("# === END COGNITIVE CONTEXT PACK ===");
    }
}
