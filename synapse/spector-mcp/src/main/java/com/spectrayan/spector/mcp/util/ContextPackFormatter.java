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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.FactHistory;
import com.spectrayan.spector.memory.model.MemoryType;

/**
 * Formats multi-tier cognitive memory into structured, token-budgeted markdown context packs.
 *
 * <p>Allocates token budgets across cognitive dimensions:</p>
 * <ul>
 *   <li><b>Working Memory & Intent</b> (~20% budget): active conversational goals and scratchpad</li>
 *   <li><b>Procedural Heuristics & Cadence</b> (~25% budget): crystallized decision rules and skills</li>
 *   <li><b>Core Semantic Facts & Axioms</b> (~30% budget): beliefs, facts, and world models</li>
 *   <li><b>Chrono-Episodic Memories</b> (~25% budget): episodic stories and experiences</li>
 * </ul>
 */
public final class ContextPackFormatter {

    private static final int CHARS_PER_TOKEN = 4;

    private ContextPackFormatter() {}

    /**
     * Input data bundle for context pack generation.
     */
    public record ContextPackInput(
            String query,
            String workingIntent,
            List<CognitiveResult> recalledMemories,
            List<FactHistory> factHistories,
            int tokenBudget,
            String profileName,
            String personaId
    ) {
        public ContextPackInput {
            recalledMemories = recalledMemories != null ? List.copyOf(recalledMemories) : List.of();
            factHistories = factHistories != null ? List.copyOf(factHistories) : List.of();
            if (tokenBudget <= 0) {
                tokenBudget = 3000;
            }
        }
    }

    /**
     * Formats a complete hierarchical context pack adhering to the token budget.
     *
     * @param input the context pack inputs
     * @return structured markdown string ready for LLM injection
     */
    public static String format(ContextPackInput input) {
        Objects.requireNonNull(input, "input cannot be null");

        int totalCharBudget = input.tokenBudget() * CHARS_PER_TOKEN;
        int workingBudget = (int) (totalCharBudget * 0.20);
        int proceduralBudget = (int) (totalCharBudget * 0.25);
        int semanticBudget = (int) (totalCharBudget * 0.30);
        int episodicBudget = (int) (totalCharBudget * 0.25);

        // Separate recalled memories by tier
        List<CognitiveResult> workingMemories = new ArrayList<>();
        List<CognitiveResult> proceduralMemories = new ArrayList<>();
        List<CognitiveResult> semanticMemories = new ArrayList<>();
        List<CognitiveResult> episodicMemories = new ArrayList<>();

        for (CognitiveResult result : input.recalledMemories()) {
            if (result.memoryType() == MemoryType.WORKING) {
                workingMemories.add(result);
            } else if (result.memoryType() == MemoryType.PROCEDURAL) {
                proceduralMemories.add(result);
            } else if (result.memoryType() == MemoryType.SEMANTIC) {
                semanticMemories.add(result);
            } else if (result.memoryType() == MemoryType.EPISODIC) {
                episodicMemories.add(result);
            }
        }

        var sb = new StringBuilder();
        sb.append("# === SPECTOR COGNITIVE CONTEXT PACK ===\n");
        if (input.personaId() != null && !input.personaId().isBlank()) {
            sb.append("**Persona:** `").append(input.personaId()).append("` | ");
        }
        sb.append("**Profile:** `").append(input.profileName() != null ? input.profileName() : "BALANCED")
          .append("` | **Budget:** ").append(input.tokenBudget()).append(" tokens\n\n");

        // 1. Working Intent & Scratchpad
        sb.append("## 1. ACTIVE WORKING INTENT & SCRATCHPAD\n");
        int workingCharsUsed = 0;
        if (input.workingIntent() != null && !input.workingIntent().isBlank()) {
            String line = "- [Turn Intent]: " + input.workingIntent().strip() + "\n";
            sb.append(line);
            workingCharsUsed += line.length();
        }
        for (CognitiveResult r : workingMemories) {
            String item = "- [Working #" + r.id() + "]: " + r.text() + "\n";
            if (workingCharsUsed + item.length() <= workingBudget) {
                sb.append(item);
                workingCharsUsed += item.length();
            }
        }
        if (workingCharsUsed == 0) {
            sb.append("- _No active working scratchpad note._\n");
        }
        sb.append("\n");

        // 2. Procedural Heuristics & Cadence
        sb.append("## 2. PROCEDURAL HEURISTICS & DECISION CADENCE (Basal Ganglia)\n");
        int procCharsUsed = 0;
        for (CognitiveResult r : proceduralMemories) {
            StringBuilder item = new StringBuilder();
            item.append("- [Skill #").append(r.id()).append("]: ").append(r.text()).append("\n");
            item.append("  - Score: ").append(String.format("%.2f", r.score()));
            item.append(" | Valence: ").append(r.valence()).append("\n");
            if (procCharsUsed + item.length() <= proceduralBudget) {
                sb.append(item);
                procCharsUsed += item.length();
            }
        }
        if (procCharsUsed == 0) {
            sb.append("- _No specialized procedural skill triggered for current context._\n");
        }
        sb.append("\n");

        // 3. Core Semantic Facts & Moral Axioms
        sb.append("## 3. CORE SEMANTIC FACTS & AXIOMS (Neocortex)\n");
        int semCharsUsed = 0;
        for (CognitiveResult r : semanticMemories) {
            StringBuilder item = new StringBuilder();
            item.append("- [Fact #").append(r.id()).append("]: ").append(r.text()).append("\n");
            if (r.synapticTags() != null && r.synapticTags().length > 0) {
                item.append("  - Tags: [").append(String.join(", ", r.synapticTags())).append("]\n");
            }
            if (semCharsUsed + item.length() <= semanticBudget) {
                sb.append(item);
                semCharsUsed += item.length();
            }
        }
        if (semCharsUsed == 0) {
            sb.append("- _No matching semantic beliefs retrieved._\n");
        }
        sb.append("\n");

        // 4. Chrono-Episodic Memories & Anecdotes
        sb.append("## 4. CHRONO-EPISODIC MEMORIES & EXPERIENCES (Hippocampus)\n");
        int epiCharsUsed = 0;
        for (CognitiveResult r : episodicMemories) {
            StringBuilder item = new StringBuilder();
            item.append("- [Episode #").append(r.id()).append("]: ").append(r.text()).append("\n");
            item.append("  - Confidence: ").append(String.format("%.2f", r.ltpAdjustedDecay()))
                .append(" | Age: ").append(String.format("%.1f", r.ageDays())).append("d\n");
            if (epiCharsUsed + item.length() <= episodicBudget) {
                sb.append(item);
                epiCharsUsed += item.length();
            }
        }
        if (epiCharsUsed == 0) {
            sb.append("- _No episodic memories recalled for prompt._\n");
        }
        sb.append("\n");

        // 5. Multi-Evidence Transitions & Conflicts (if present)
        if (!input.factHistories().isEmpty()) {
            sb.append("## 5. BITEMPORAL EVIDENCE TRANSITIONS & CONFLICTS\n");
            for (FactHistory fh : input.factHistories()) {
                sb.append("- [Timeline: `").append(fh.subject()).append("` -> `").append(fh.predicate()).append("`]:\n");
                if (fh.activeFact() != null) {
                    sb.append("  - Active Consensus: `").append(fh.activeFact().object())
                      .append("` (conf: ").append(String.format("%.2f", fh.activeFact().confidence()))
                      .append(", validFrom: ").append(fh.activeFact().validFrom()).append(")\n");
                }
                for (FactHistory.FactSnapshot s : fh.supersededFacts()) {
                    sb.append("  - Historical: `").append(s.object())
                      .append("` (conf: ").append(String.format("%.2f", s.confidence()))
                      .append(", supersededBy: #").append(s.supersededByFactId()).append(")\n");
                }
            }
            sb.append("\n");
        }

        sb.append("# === END COGNITIVE CONTEXT PACK ===\n");
        return sb.toString();
    }
}
