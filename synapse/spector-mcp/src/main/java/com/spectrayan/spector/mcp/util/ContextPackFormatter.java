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
import com.spectrayan.spector.memory.pathway.skill.model.SkillBody;
import com.spectrayan.spector.kernel.api.MemoryType;

/**
 * Formats multi-tier cognitive memory into structured, token-budgeted markdown context packs.
 *
 * <h3>Dual-Plane Rendering</h3>
 * <p>This formatter supports two rendering modes for LLM prompt prefix cache efficiency:</p>
 * <ul>
 *   <li><b>{@link #formatStaticPrefix}</b>: Stable content (persona, procedural skills, semantic axioms)
 *       that remains byte-identical across turns within a session. Designed for the system prompt,
 *       above the cache boundary.</li>
 *   <li><b>{@link #formatDynamicTail}</b>: Volatile content (working scratchpad, episodic memories,
 *       turn-specific semantic matches, fact transitions) that varies per turn. Designed for the
 *       user message or tool response, below the cache boundary.</li>
 * </ul>
 *
 * <p>The original {@link #format} method is preserved for backward compatibility and concatenates both.</p>
 *
 * <h3>Token Budget Allocation</h3>
 * <ul>
 *   <li><b>Working Memory &amp; Intent</b> (~20% budget): active conversational goals and scratchpad</li>
 *   <li><b>Procedural Heuristics &amp; Cadence</b> (~25% budget): crystallized decision rules and skills</li>
 *   <li><b>Core Semantic Facts &amp; Axioms</b> (~30% budget): beliefs, facts, and world models</li>
 *   <li><b>Chrono-Episodic Memories</b> (~25% budget): episodic stories and experiences</li>
 * </ul>
 */
public final class ContextPackFormatter {

    private static final int CHARS_PER_TOKEN = 4;

    /**
     * Default importance threshold for classifying semantic memories as static axioms.
     * Memories with importance &ge; this value are rendered in the static prefix;
     * those below go to the dynamic tail. Based on a 0–10 importance scale.
     */
    public static final float DEFAULT_STATIC_IMPORTANCE_THRESHOLD = 5.0f;

    private ContextPackFormatter() {}

    /**
     * Input data bundle for context pack generation.
     *
     * @param query                       the recall query for this turn
     * @param workingIntent               active intent / scratchpad note
     * @param recalledMemories            all recalled memories across tiers
     * @param factHistories               bitemporal fact transition histories
     * @param tokenBudget                 total token budget for the context pack
     * @param profileName                 cognitive recall profile name
     * @param personaId                   active persona identifier
     * @param staticImportanceThreshold   importance threshold for static/dynamic semantic split;
     *                                    semantic memories with {@code importance >= threshold}
     *                                    go to the static prefix, others to the dynamic tail.
     *                                    Defaults to {@value #DEFAULT_STATIC_IMPORTANCE_THRESHOLD}.
     */
    public record ContextPackInput(
            String query,
            String workingIntent,
            List<CognitiveResult> recalledMemories,
            List<FactHistory> factHistories,
            int tokenBudget,
            String profileName,
            String personaId,
            float staticImportanceThreshold
    ) {
        /**
         * Backward-compatible constructor without staticImportanceThreshold.
         */
        public ContextPackInput(String query, String workingIntent,
                                List<CognitiveResult> recalledMemories,
                                List<FactHistory> factHistories,
                                int tokenBudget, String profileName,
                                String personaId) {
            this(query, workingIntent, recalledMemories, factHistories,
                    tokenBudget, profileName, personaId,
                    DEFAULT_STATIC_IMPORTANCE_THRESHOLD);
        }

        public ContextPackInput {
            recalledMemories = recalledMemories != null ? List.copyOf(recalledMemories) : List.of();
            factHistories = factHistories != null ? List.copyOf(factHistories) : List.of();
            if (tokenBudget <= 0) {
                tokenBudget = 3000;
            }
            if (staticImportanceThreshold < 0) {
                staticImportanceThreshold = DEFAULT_STATIC_IMPORTANCE_THRESHOLD;
            }
        }
    }

    /**
     * Formats a complete hierarchical context pack adhering to the token budget.
     *
     * <p>Backward-compatible: produces the same output as before by concatenating
     * {@link #formatStaticPrefix} and {@link #formatDynamicTail}.</p>
     *
     * @param input the context pack inputs
     * @return structured markdown string ready for LLM injection
     */
    public static String format(ContextPackInput input) {
        Objects.requireNonNull(input, "input cannot be null");
        return formatStaticPrefix(input) + formatDynamicTail(input);
    }

    /**
     * Renders the <b>static prefix</b> — content stable across turns within a session.
     *
     * <p>Includes:</p>
     * <ul>
     *   <li>Context pack header with persona and profile metadata</li>
     *   <li>Procedural heuristics and crystallized skills (tier {@code PROCEDURAL})</li>
     *   <li>High-importance semantic axioms (tier {@code SEMANTIC}, importance &ge; threshold)</li>
     * </ul>
     *
     * <p>This block is designed to be placed in the system prompt, above the LLM provider's
     * cache boundary, and remain byte-identical across consecutive conversational turns
     * (provided persona, skills, and high-importance facts haven't changed).</p>
     *
     * @param input the context pack inputs
     * @return stable markdown prefix for system prompt caching
     */
    public static String formatStaticPrefix(ContextPackInput input) {
        Objects.requireNonNull(input, "input cannot be null");

        int totalCharBudget = input.tokenBudget() * CHARS_PER_TOKEN;
        int proceduralBudget = (int) (totalCharBudget * 0.25);
        int semanticBudget = (int) (totalCharBudget * 0.30);

        List<CognitiveResult> proceduralMemories = new ArrayList<>();
        List<CognitiveResult> staticSemanticMemories = new ArrayList<>();

        for (CognitiveResult result : input.recalledMemories()) {
            if (result.memoryType() == MemoryType.PROCEDURAL) {
                proceduralMemories.add(result);
            } else if (result.memoryType() == MemoryType.SEMANTIC
                    && result.importance() >= input.staticImportanceThreshold()) {
                staticSemanticMemories.add(result);
            }
        }

        var sb = new StringBuilder();

        // Header
        sb.append("# === SPECTOR COGNITIVE CONTEXT PACK ===\n");
        if (input.personaId() != null && !input.personaId().isBlank()) {
            sb.append("**Persona:** `").append(input.personaId()).append("` | ");
        }
        sb.append("**Profile:** `").append(input.profileName() != null ? input.profileName() : "BALANCED")
          .append("` | **Budget:** ").append(input.tokenBudget()).append(" tokens\n\n");

        // Procedural Heuristics & Cadence
        sb.append("## 2. PROCEDURAL HEURISTICS & DECISION CADENCE\n");
        int procCharsUsed = renderProceduralMemories(sb, proceduralMemories, proceduralBudget);
        if (procCharsUsed == 0) {
            sb.append("- _No specialized procedural skill triggered for current context._\n");
        }
        sb.append("\n");

        // Static Semantic Axioms (high importance)
        sb.append("## 3. CORE SEMANTIC FACTS & AXIOMS\n");
        int semCharsUsed = renderSemanticMemories(sb, staticSemanticMemories, semanticBudget);
        if (semCharsUsed == 0) {
            sb.append("- _No high-confidence semantic axioms in static prefix._\n");
        }
        sb.append("\n");

        return sb.toString();
    }

    /**
     * Renders the <b>dynamic tail</b> — content that varies per turn.
     *
     * <p>Includes:</p>
     * <ul>
     *   <li>Working memory intent and scratchpad (tier {@code WORKING})</li>
     *   <li>Turn-specific semantic matches (tier {@code SEMANTIC}, importance &lt; threshold)</li>
     *   <li>Chrono-episodic memories and anecdotes (tier {@code EPISODIC})</li>
     *   <li>Bitemporal fact transitions and conflicts</li>
     * </ul>
     *
     * <p>This block is designed to be appended to the user message or tool response,
     * below the LLM provider's cache boundary.</p>
     *
     * @param input the context pack inputs
     * @return volatile markdown tail for user-turn injection
     */
    public static String formatDynamicTail(ContextPackInput input) {
        Objects.requireNonNull(input, "input cannot be null");

        int totalCharBudget = input.tokenBudget() * CHARS_PER_TOKEN;
        int workingBudget = (int) (totalCharBudget * 0.20);
        int semanticBudget = (int) (totalCharBudget * 0.30);
        int episodicBudget = (int) (totalCharBudget * 0.25);

        List<CognitiveResult> workingMemories = new ArrayList<>();
        List<CognitiveResult> dynamicSemanticMemories = new ArrayList<>();
        List<CognitiveResult> episodicMemories = new ArrayList<>();

        for (CognitiveResult result : input.recalledMemories()) {
            if (result.memoryType() == MemoryType.WORKING) {
                workingMemories.add(result);
            } else if (result.memoryType() == MemoryType.SEMANTIC
                    && result.importance() < input.staticImportanceThreshold()) {
                dynamicSemanticMemories.add(result);
            } else if (result.memoryType() == MemoryType.EPISODIC) {
                episodicMemories.add(result);
            }
        }

        var sb = new StringBuilder();

        // Working Intent & Scratchpad
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

        // Dynamic Semantic Matches (lower importance, turn-specific)
        if (!dynamicSemanticMemories.isEmpty()) {
            sb.append("## 3b. TURN-RELEVANT SEMANTIC MATCHES\n");
            renderSemanticMemories(sb, dynamicSemanticMemories, semanticBudget);
            sb.append("\n");
        }

        // Chrono-Episodic Memories
        sb.append("## 4. CHRONO-EPISODIC MEMORIES & EXPERIENCES\n");
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

        // Bitemporal Fact Transitions
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

    // ──────────────── Shared rendering helpers ────────────────

    /**
     * Renders procedural memories into the given StringBuilder.
     *
     * @return total characters used
     */
    private static int renderProceduralMemories(StringBuilder sb,
                                                 List<CognitiveResult> memories,
                                                 int charBudget) {
        int charsUsed = 0;
        for (CognitiveResult r : memories) {
            SkillBody skillBody = SkillBody.parse(r.text());
            StringBuilder item = new StringBuilder();

            if (skillBody.hasMeta()) {
                var meta = skillBody.meta();
                String kindStr = meta.kind() != null ? meta.kind().name().toLowerCase() : "heuristic";
                String nameStr = meta.name() != null ? meta.name() : "unnamed";
                String structured = formatStructuredSkill(skillBody.body());
                if (!structured.isEmpty()) {
                    item.append("- [Skill #").append(r.id()).append("] ").append(nameStr)
                            .append("  (").append(kindStr).append(", conf ")
                            .append(String.format(java.util.Locale.ROOT, "%.2f", meta.confidence())).append(")\n");
                    item.append(structured);
                } else {
                    String summary = extractFirstParagraph(skillBody.body());
                    item.append("- [Skill #").append(r.id()).append("] ").append(nameStr)
                            .append("  (").append(kindStr).append(", conf ")
                            .append(String.format(java.util.Locale.ROOT, "%.2f", meta.confidence())).append(")");
                    if (!summary.isEmpty()) {
                        item.append(": ").append(summary);
                    }
                    item.append("\n");
                }
                if (meta.tools() != null && !meta.tools().isEmpty()) {
                    item.append("  - Tools: [").append(String.join(", ", meta.tools())).append("]\n");
                }
            } else {
                String cleanText = r.text();
                if (cleanText.stripLeading().startsWith("---")) {
                    int secondFence = cleanText.indexOf("---", 3);
                    if (secondFence != -1) {
                        cleanText = cleanText.substring(secondFence + 3).strip();
                    }
                }
                item.append("- [Skill #").append(r.id()).append("]: ").append(cleanText).append("\n");
            }

            item.append("  - Score: ").append(String.format(java.util.Locale.ROOT, "%.2f", r.score()));
            item.append(" | Valence: ").append(r.valence()).append("\n");
            if (charsUsed + item.length() <= charBudget) {
                sb.append(item);
                charsUsed += item.length();
            }
        }
        return charsUsed;
    }

    /**
     * Renders semantic memories into the given StringBuilder.
     *
     * @return total characters used
     */
    private static int renderSemanticMemories(StringBuilder sb,
                                               List<CognitiveResult> memories,
                                               int charBudget) {
        int charsUsed = 0;
        for (CognitiveResult r : memories) {
            StringBuilder item = new StringBuilder();
            item.append("- [Fact #").append(r.id()).append("]: ").append(r.text()).append("\n");
            if (r.synapticTags() != null && r.synapticTags().length > 0) {
                item.append("  - Tags: [").append(String.join(", ", r.synapticTags())).append("]\n");
            }
            if (charsUsed + item.length() <= charBudget) {
                sb.append(item);
                charsUsed += item.length();
            }
        }
        return charsUsed;
    }

    private static String formatStructuredSkill(final String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        String[] lines = body.split("\\r?\\n");
        String currentSection = null;
        List<String> doLines = new ArrayList<>();
        String whenLine = null;
        String doneLine = null;

        for (String rawLine : lines) {
            String line = rawLine.strip();
            if (line.isEmpty()) continue;

            String lower = line.toLowerCase();
            if (lower.startsWith("when:") || lower.startsWith("## when")) {
                currentSection = "when";
                int colonIdx = line.indexOf(':');
                if (colonIdx >= 0 && colonIdx < line.length() - 1) {
                    whenLine = line.substring(colonIdx + 1).strip();
                }
            } else if (lower.startsWith("do:") || lower.startsWith("## do") || lower.startsWith("## execution steps")) {
                currentSection = "do";
                int colonIdx = line.indexOf(':');
                if (colonIdx >= 0 && colonIdx < line.length() - 1) {
                    String after = line.substring(colonIdx + 1).strip();
                    if (!after.isEmpty()) doLines.add(after);
                }
            } else if (lower.startsWith("done:") || lower.startsWith("## done") || lower.startsWith("## validation")) {
                currentSection = "done";
                int colonIdx = line.indexOf(':');
                if (colonIdx >= 0 && colonIdx < line.length() - 1) {
                    doneLine = line.substring(colonIdx + 1).strip();
                }
            } else if ("when".equals(currentSection) && whenLine == null) {
                whenLine = line;
            } else if ("done".equals(currentSection) && doneLine == null) {
                doneLine = line;
            } else if ("do".equals(currentSection)) {
                doLines.add(line);
            }
        }

        if (whenLine != null || !doLines.isEmpty() || doneLine != null) {
            StringBuilder out = new StringBuilder();
            if (whenLine != null) {
                out.append("  When: ").append(whenLine).append("\n");
            }
            if (!doLines.isEmpty()) {
                out.append("  Do:\n");
                for (String step : doLines) {
                    out.append("    ").append(step).append("\n");
                }
            }
            if (doneLine != null) {
                out.append("  Done: ").append(doneLine).append("\n");
            }
            return out.toString();
        }
        return "";
    }

    private static String extractFirstParagraph(final String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        String stripped = body.strip();
        if (stripped.startsWith("#")) {
            int firstNewline = stripped.indexOf('\n');
            if (firstNewline != -1) {
                stripped = stripped.substring(firstNewline).strip();
            }
        }
        int doubleNewline = stripped.indexOf("\n\n");
        if (doubleNewline != -1) {
            return stripped.substring(0, doubleNewline).strip().replace("\n", " ");
        }
        return stripped.replace("\n", " ");
    }
}
