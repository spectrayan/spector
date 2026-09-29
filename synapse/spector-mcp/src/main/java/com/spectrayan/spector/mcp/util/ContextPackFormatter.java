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
 * Formats multi-tier cognitive memory into structured, token-budgeted markdown context packs
 * using declarative Handlebars templates.
 *
 * <h3>Dual-Plane Rendering</h3>
 * <p>This formatter supports two rendering modes for LLM prompt prefix cache efficiency:</p>
 * <ul>
 *   <li><b>{@link #formatStaticPrefix}</b>: Stable content (persona, procedural skills, semantic axioms)
 *       that remains byte-identical across turns within a session. Rendered via
 *       {@code /mcp/templates/memory-context-pack-static.hbs} for system prompt caching.</li>
 *   <li><b>{@link #formatDynamicTail}</b>: Volatile content (working scratchpad, episodic memories,
 *       turn-specific semantic matches, fact transitions) that varies per turn. Rendered via
 *       {@code /mcp/templates/memory-context-pack-dynamic.hbs} for user-turn injection.</li>
 *   <li><b>{@link #formatSplit}</b>: Renders both planes separated by the cache boundary marker
 *       via {@code /mcp/templates/memory-context-pack-split.hbs}.</li>
 * </ul>
 *
 * <p>The original {@link #format} method is preserved for backward compatibility and delegates
 * to both planes.</p>
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

    // ──────────────── Handlebars View Models ────────────────

    public record ProceduralSkillView(
            String id,
            String name,
            String kind,
            float confidence,
            String summary,
            String structured,
            List<String> tools,
            boolean hasTools,
            float score,
            int valence,
            String legacyText,
            boolean isLegacy
    ) {}

    public record SemanticFactView(
            String id,
            String text,
            List<String> synapticTags,
            boolean hasTags
    ) {}

    public record WorkingMemoryView(
            String id,
            String text
    ) {}

    public record EpisodicMemoryView(
            String id,
            String text,
            float ltpAdjustedDecay,
            float ageDays
    ) {}

    public record ContextPackModel(
            String personaId,
            String profileName,
            int tokenBudget,
            List<ProceduralSkillView> proceduralMemories,
            boolean hasProceduralMemories,
            List<SemanticFactView> staticSemanticMemories,
            boolean hasStaticSemanticMemories,
            String workingIntent,
            boolean hasWorkingIntent,
            List<WorkingMemoryView> workingMemories,
            boolean hasWorkingMemories,
            List<SemanticFactView> dynamicSemanticMemories,
            boolean hasDynamicSemanticMemories,
            List<EpisodicMemoryView> episodicMemories,
            boolean hasEpisodicMemories,
            List<FactHistory> factHistories,
            boolean hasFactHistories
    ) {}

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
     * <p>Renders via {@code /mcp/templates/memory-context-pack-static.hbs}.</p>
     *
     * @param input the context pack inputs
     * @return stable markdown prefix for system prompt caching
     */
    public static String formatStaticPrefix(ContextPackInput input) {
        Objects.requireNonNull(input, "input cannot be null");
        return McpTemplateEngine.render("memory-context-pack-static", buildModel(input));
    }

    /**
     * Renders the <b>dynamic tail</b> — content that varies per turn.
     *
     * <p>Renders via {@code /mcp/templates/memory-context-pack-dynamic.hbs}.</p>
     *
     * @param input the context pack inputs
     * @return volatile markdown tail for user-turn injection
     */
    public static String formatDynamicTail(ContextPackInput input) {
        Objects.requireNonNull(input, "input cannot be null");
        return McpTemplateEngine.render("memory-context-pack-dynamic", buildModel(input));
    }

    /**
     * Renders the dual-plane context pack separated by the cache boundary marker.
     *
     * <p>Renders via {@code /mcp/templates/memory-context-pack-split.hbs}.</p>
     *
     * @param input the context pack inputs
     * @return markdown with static prefix, cache boundary marker, and dynamic tail
     */
    public static String formatSplit(ContextPackInput input) {
        Objects.requireNonNull(input, "input cannot be null");
        return McpTemplateEngine.render("memory-context-pack-split", buildModel(input));
    }

    // ──────────────── Model Building & Budgeting ────────────────

    public static ContextPackModel buildModel(ContextPackInput input) {
        int totalCharBudget = input.tokenBudget() * CHARS_PER_TOKEN;
        int workingBudget = (int) (totalCharBudget * 0.20);
        int proceduralBudget = (int) (totalCharBudget * 0.25);
        int semanticBudget = (int) (totalCharBudget * 0.30);
        int episodicBudget = (int) (totalCharBudget * 0.25);

        List<CognitiveResult> workingList = new ArrayList<>();
        List<CognitiveResult> proceduralList = new ArrayList<>();
        List<CognitiveResult> staticSemanticList = new ArrayList<>();
        List<CognitiveResult> dynamicSemanticList = new ArrayList<>();
        List<CognitiveResult> episodicList = new ArrayList<>();

        for (CognitiveResult result : input.recalledMemories()) {
            if (result.memoryType() == MemoryType.WORKING) {
                workingList.add(result);
            } else if (result.memoryType() == MemoryType.PROCEDURAL) {
                proceduralList.add(result);
            } else if (result.memoryType() == MemoryType.SEMANTIC) {
                if (result.importance() >= input.staticImportanceThreshold()) {
                    staticSemanticList.add(result);
                } else {
                    dynamicSemanticList.add(result);
                }
            } else if (result.memoryType() == MemoryType.EPISODIC) {
                episodicList.add(result);
            }
        }

        // Budget Procedural
        List<ProceduralSkillView> budgetedProcedural = new ArrayList<>();
        int procChars = 0;
        for (CognitiveResult r : proceduralList) {
            ProceduralSkillView view = toProceduralSkillView(r);
            int estLen = estimateProceduralLength(view);
            if (procChars + estLen <= proceduralBudget) {
                budgetedProcedural.add(view);
                procChars += estLen;
            }
        }

        // Budget Static Semantic
        List<SemanticFactView> budgetedStaticSemantic = new ArrayList<>();
        int staticSemChars = 0;
        for (CognitiveResult r : staticSemanticList) {
            SemanticFactView view = toSemanticFactView(r);
            int estLen = estimateSemanticLength(view);
            if (staticSemChars + estLen <= semanticBudget) {
                budgetedStaticSemantic.add(view);
                staticSemChars += estLen;
            }
        }

        // Budget Working
        List<WorkingMemoryView> budgetedWorking = new ArrayList<>();
        int workingChars = 0;
        boolean hasWorkingIntent = input.workingIntent() != null && !input.workingIntent().isBlank();
        if (hasWorkingIntent) {
            workingChars += ("- [Turn Intent]: " + input.workingIntent().strip() + "\n").length();
        }
        for (CognitiveResult r : workingList) {
            String item = "- [Working #" + r.id() + "]: " + r.text() + "\n";
            if (workingChars + item.length() <= workingBudget) {
                budgetedWorking.add(new WorkingMemoryView(r.id(), r.text()));
                workingChars += item.length();
            }
        }

        // Budget Dynamic Semantic
        List<SemanticFactView> budgetedDynamicSemantic = new ArrayList<>();
        int dynamicSemChars = 0;
        for (CognitiveResult r : dynamicSemanticList) {
            SemanticFactView view = toSemanticFactView(r);
            int estLen = estimateSemanticLength(view);
            if (dynamicSemChars + estLen <= semanticBudget) {
                budgetedDynamicSemantic.add(view);
                dynamicSemChars += estLen;
            }
        }

        // Budget Episodic
        List<EpisodicMemoryView> budgetedEpisodic = new ArrayList<>();
        int epiChars = 0;
        for (CognitiveResult r : episodicList) {
            EpisodicMemoryView view = new EpisodicMemoryView(r.id(), r.text(), r.ltpAdjustedDecay(), r.ageDays());
            int estLen = estimateEpisodicLength(view);
            if (epiChars + estLen <= episodicBudget) {
                budgetedEpisodic.add(view);
                epiChars += estLen;
            }
        }

        String profile = input.profileName() != null && !input.profileName().isBlank()
                ? input.profileName() : "BALANCED";

        return new ContextPackModel(
                input.personaId(),
                profile,
                input.tokenBudget(),
                budgetedProcedural,
                !budgetedProcedural.isEmpty(),
                budgetedStaticSemantic,
                !budgetedStaticSemantic.isEmpty(),
                hasWorkingIntent ? input.workingIntent().strip() : null,
                hasWorkingIntent,
                budgetedWorking,
                !budgetedWorking.isEmpty(),
                budgetedDynamicSemantic,
                !budgetedDynamicSemantic.isEmpty(),
                budgetedEpisodic,
                !budgetedEpisodic.isEmpty(),
                input.factHistories(),
                !input.factHistories().isEmpty()
        );
    }

    private static ProceduralSkillView toProceduralSkillView(CognitiveResult r) {
        SkillBody skillBody = SkillBody.parse(r.text());
        if (skillBody.hasMeta()) {
            var meta = skillBody.meta();
            String kindStr = meta.kind() != null ? meta.kind().name().toLowerCase() : "heuristic";
            String nameStr = meta.name() != null ? meta.name() : "unnamed";
            String structured = formatStructuredSkill(skillBody.body());
            String summary = structured.isEmpty() ? extractFirstParagraph(skillBody.body()) : "";
            List<String> tools = meta.tools() != null ? meta.tools() : List.of();
            return new ProceduralSkillView(
                    r.id(), nameStr, kindStr, meta.confidence(),
                    summary, structured, tools, !tools.isEmpty(),
                    r.score(), r.valence(), null, false
            );
        } else {
            String cleanText = r.text();
            if (cleanText != null && cleanText.stripLeading().startsWith("---")) {
                int secondFence = cleanText.indexOf("---", 3);
                if (secondFence != -1) {
                    cleanText = cleanText.substring(secondFence + 3).strip();
                }
            }
            return new ProceduralSkillView(
                    r.id(), null, null, 0f,
                    null, null, List.of(), false,
                    r.score(), r.valence(), cleanText, true
            );
        }
    }

    private static SemanticFactView toSemanticFactView(CognitiveResult r) {
        List<String> tags = r.synapticTags() != null ? List.of(r.synapticTags()) : List.of();
        return new SemanticFactView(r.id(), r.text(), tags, !tags.isEmpty());
    }

    private static int estimateProceduralLength(ProceduralSkillView view) {
        if (view.isLegacy()) {
            return (view.legacyText() != null ? view.legacyText().length() : 0) + 40;
        }
        int len = (view.name() != null ? view.name().length() : 0) + 50;
        if (view.structured() != null && !view.structured().isEmpty()) {
            len += view.structured().length();
        } else if (view.summary() != null && !view.summary().isEmpty()) {
            len += view.summary().length() + 2;
        }
        if (view.hasTools()) {
            len += 20 + String.join(", ", view.tools()).length();
        }
        return len;
    }

    private static int estimateSemanticLength(SemanticFactView view) {
        int len = (view.text() != null ? view.text().length() : 0) + 20;
        if (view.hasTags()) {
            len += 15 + String.join(", ", view.synapticTags()).length();
        }
        return len;
    }

    private static int estimateEpisodicLength(EpisodicMemoryView view) {
        return (view.text() != null ? view.text().length() : 0) + 60;
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
