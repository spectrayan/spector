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
package com.spectrayan.spector.memory.pathway.skill.relay;

import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.memory.pathway.RelayNames;
import com.spectrayan.spector.memory.pathway.skill.model.SkillBody;
import com.spectrayan.spector.memory.pathway.skill.model.SkillKind;
import com.spectrayan.spector.memory.pathway.skill.model.SkillMeta;
import com.spectrayan.spector.provider.generation.LlmProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Extracts structured procedural skill frontmatter and markdown body from admitted clusters (ADR-0086 §5.2, §5.4).
 *
 * <p>Produces a standardized {@code spector.skill.v1} markdown representation with YAML frontmatter.
 * In offline or deterministic test modes where no LLM is configured, gracefully synthesizes a deterministic
 * playbook or heuristic rule directly from parent texts.</p>
 */
public final class SkillExtractRelay implements SynapticRelay<SkillSignal> {

    private static final Logger log = LoggerFactory.getLogger(SkillExtractRelay.class);

    @Override
    public boolean transmit(final SkillSignal signal) {
        if (signal.mode() == SkillSignal.Mode.REINFORCE) {
            return true;
        }

        // If body is already extracted (e.g. supplied by caller or tool), skip
        if (signal.extractedBody() != null) {
            return true;
        }

        Map<String, List<String>> parentsMap = buildParentsMap(signal.parents());

        // Determine skill kind: mixed or multi-turn episodic defaults to PLAYBOOK; semantic defaults to HEURISTIC
        boolean hasEpisodic = parentsMap.containsKey("episodic") && !parentsMap.get("episodic").isEmpty();
        boolean hasSemantic = parentsMap.containsKey("semantic") && !parentsMap.get("semantic").isEmpty();
        SkillKind kind = (hasEpisodic && (hasSemantic || parentsMap.get("episodic").size() >= 2))
                ? SkillKind.PLAYBOOK
                : SkillKind.HEURISTIC;

        String name = signal.cue() != null && !signal.cue().isBlank()
                ? sanitizeName(signal.cue())
                : (kind == SkillKind.PLAYBOOK ? "crystallized-playbook" : "crystallized-heuristic");

        float initialConfidence = 0.35f; // Initial confidence prior at mint time (ADR-0086 §5.4)

        LlmProvider llm = signal.context() != null ? signal.context().get(LlmProvider.class) : null;
        if (llm != null && !signal.parentTexts().isEmpty()) {
            try {
                String prompt = buildPrompt(signal.parentTexts(), kind);
                String response = llm.generate(prompt);
                SkillBody parsed = SkillBody.parse(response);
                if (parsed.hasMeta()) {
                    signal.extractedBody(parsed);
                    return true;
                }
            } catch (Exception e) {
                log.warn("LLM skill extraction failed, falling back to heuristic synthesizer: {}", e.getMessage());
            }
        }

        // Deterministic fallback synthesis
        String markdownBody = buildFallbackBody(name, kind, signal.parentTexts());
        SkillMeta meta = new SkillMeta(
                SkillMeta.SCHEMA_V1,
                name,
                kind,
                initialConfidence,
                List.of(),
                parentsMap
        );

        SkillBody body = new SkillBody(meta, markdownBody);
        signal.extractedBody(body);
        return true;
    }

    private static Map<String, List<String>> buildParentsMap(final List<SkillSignal.ParentRef> parents) {
        Map<String, List<String>> map = new HashMap<>();
        for (var p : parents) {
            String key = p.type() == MemoryType.EPISODIC ? "episodic" : "semantic";
            map.computeIfAbsent(key, k -> new ArrayList<>()).add(p.tsid());
        }
        return map;
    }

    private static String sanitizeName(final String cue) {
        return cue.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
    }

    private static String buildPrompt(final List<String> parentTexts, final SkillKind kind) {
        var sb = new StringBuilder();
        sb.append("You are the Spector Procedural Skill Crystallizer.\n");
        sb.append("Synthesize the following observations into a clean, reusable ").append(kind.name()).append(".\n");
        sb.append("Output MUST begin with YAML frontmatter between --- fences (schema: spector.skill.v1, name, kind, confidence: 0.35, tools: []).\n");
        sb.append("Followed by markdown instructions.\n\n");
        sb.append("Observations:\n");
        for (int i = 0; i < parentTexts.size(); i++) {
            sb.append(i + 1).append(". ").append(parentTexts.get(i)).append("\n");
        }
        return sb.toString();
    }

    private static String buildFallbackBody(final String name, final SkillKind kind, final List<String> texts) {
        var sb = new StringBuilder();
        sb.append("# ").append(name).append("\n\n");
        if (kind == SkillKind.PLAYBOOK) {
            sb.append("## Execution Steps\n\n");
            if (texts.isEmpty()) {
                sb.append("1. Follow procedural strategy distilled from context.\n");
            } else {
                for (int i = 0; i < texts.size(); i++) {
                    sb.append(i + 1).append(". ").append(texts.get(i)).append("\n");
                }
            }
        } else {
            sb.append("## Heuristic Rule\n\n");
            if (texts.isEmpty()) {
                sb.append("When encountering similar conditions, apply verified resolution.\n");
            } else {
                sb.append(String.join("\n", texts)).append("\n");
            }
        }
        return sb.toString();
    }

    @Override
    public String relayName() {
        return RelayNames.SKILL_EXTRACT;
    }
}
