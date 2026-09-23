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

import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.mcp.util.ContextPackFormatter.ContextPackInput;
import com.spectrayan.spector.memory.model.CognitiveResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("ADR-0086 §5.7: ContextPackFormatter Procedural Skill Formatting Tests")
class ContextPackFormatterSkillTest {

    @Test
    @DisplayName("Formats structured spector.skill.v1 skill with metadata, tools, and summary")
    void formatsStructuredSkill() {
        String structuredSkillText = """
                ---
                schema: spector.skill.v1
                name: safe-git-rebase
                kind: playbook
                confidence: 0.85
                tools:
                  - git
                  - terminal
                parents: {}
                ---
                # Safe Git Rebase
                
                Always fetch origin and ensure working directory is clean before rebasing.
                
                ## Detailed Steps
                1. git status --porcelain
                2. git fetch origin
                """;

        CognitiveResult skill = mock(CognitiveResult.class);
        when(skill.id()).thenReturn("skill-999");
        when(skill.text()).thenReturn(structuredSkillText);
        when(skill.memoryType()).thenReturn(MemoryType.PROCEDURAL);
        when(skill.score()).thenReturn(0.92f);
        when(skill.valence()).thenReturn((byte) 10);

        ContextPackInput input = new ContextPackInput(
                "How to rebase cleanly?",
                "Rebasing feature branch",
                List.of(skill),
                List.of(),
                1000,
                "BALANCED",
                "persona-dev"
        );

        String result = ContextPackFormatter.format(input);

        assertThat(result).contains("## 2. PROCEDURAL HEURISTICS & DECISION CADENCE (Basal Ganglia)");
        assertThat(result).contains("[Skill #skill-999] (playbook: safe-git-rebase): Always fetch origin and ensure working directory is clean before rebasing.");
        assertThat(result).contains("Tools: [git, terminal]");
        assertThat(result).contains("Score: 0.92 | Valence: 10");
        assertThat(result).doesNotContain("Detailed Steps"); // Truncated after first paragraph
    }

    @Test
    @DisplayName("Legacy unparsed skill falls back to emitting raw text")
    void formatsLegacySkill() {
        CognitiveResult legacySkill = mock(CognitiveResult.class);
        when(legacySkill.id()).thenReturn("skill-old-1");
        when(legacySkill.text()).thenReturn("Check null pointer before dereferencing");
        when(legacySkill.memoryType()).thenReturn(MemoryType.PROCEDURAL);
        when(legacySkill.score()).thenReturn(0.77f);
        when(legacySkill.valence()).thenReturn((byte) 0);

        ContextPackInput input = new ContextPackInput(
                "Fix crash",
                "Checking NPE",
                List.of(legacySkill),
                List.of(),
                1000,
                "BALANCED",
                "persona-dev"
        );

        String result = ContextPackFormatter.format(input);

        assertThat(result).contains("[Skill #skill-old-1]: Check null pointer before dereferencing");
        assertThat(result).contains("Score: 0.77 | Valence: 0");
    }
}
