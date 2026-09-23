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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Validates cluster shape and admission criteria before skill compilation (ADR-0086 §5.2, §5.6).
 *
 * <p>Admission criteria:
 * <ul>
 *   <li><b>REINFORCE mode</b>: bypasses cluster validation.</li>
 *   <li><b>Working-only rejection</b>: clusters consisting exclusively of working memory are rejected.</li>
 *   <li><b>Denylist</b>: raw tool JSON dumps and raw Chain-of-Thought logs are rejected.</li>
 *   <li><b>Episodic cluster</b>: $\ge 2$ episodic turns.</li>
 *   <li><b>Semantic cluster</b>: $\ge 3$ semantic facts.</li>
 *   <li><b>Mixed cluster</b>: $\ge 1$ episodic turn and $\ge 1$ semantic fact (or prior procedural skill).</li>
 *   <li><b>Explicit tool compilation (Rule 5)</b>: pre-extracted body with parent reference or valid cue (MCP/CLI compilation);
 *       bypasses multi-item clustering shapes while still strictly enforcing denylist and working-only rejection.</li>
 * </ul>
 * </p>
 */
public final class ClusterAdmitRelay implements SynapticRelay<SkillSignal> {

    private static final Logger log = LoggerFactory.getLogger(ClusterAdmitRelay.class);

    public static final int MIN_EPISODIC_TURNS = 2;
    public static final int MIN_SEMANTIC_FACTS = 3;

    private static final List<String> DENYLIST_PATTERNS = List.of(
            "<thinking>",
            "</thinking>",
            "\"tool_calls\":",
            "\"function\":",
            "\"tool_call_id\":"
    );

    @Override
    public boolean transmit(final SkillSignal signal) {
        if (signal.mode() == SkillSignal.Mode.REINFORCE) {
            return true;
        }

        // Denylist inspection: reject raw tool-call JSON dumps and raw CoT thinking dumps
        if (containsDenylistedContent(signal)) {
            log.debug("Skill compilation rejected: candidate contains denylisted tool-JSON or raw CoT content");
            return false;
        }

        int episodicCount = 0;
        int semanticCount = 0;
        int proceduralCount = 0;
        int workingCount = 0;

        for (var parent : signal.parents()) {
            if (parent.type() == MemoryType.EPISODIC) {
                episodicCount++;
            } else if (parent.type() == MemoryType.SEMANTIC) {
                semanticCount++;
            } else if (parent.type() == MemoryType.PROCEDURAL) {
                proceduralCount++;
            } else if (parent.type() == MemoryType.WORKING) {
                workingCount++;
            }
        }

        // Rule 0: Reject working-only clusters (ADR-0086 §5.6.1)
        if (workingCount > 0 && episodicCount == 0 && semanticCount == 0 && proceduralCount == 0) {
            log.debug("Skill compilation rejected: working-only clusters are prohibited from procedural crystallization");
            return false;
        }

        // Rule 1: Episodic session cluster (>= 2 turns)
        if (episodicCount >= MIN_EPISODIC_TURNS) {
            return true;
        }

        // Rule 2: Semantic co-retrieval cluster (>= 3 facts)
        if (semanticCount >= MIN_SEMANTIC_FACTS) {
            return true;
        }

        // Rule 3: Mixed cluster (>= 1 episodic + >= 1 semantic/procedural)
        if (episodicCount >= 1 && (semanticCount >= 1 || proceduralCount >= 1)) {
            return true;
        }

        // Rule 4: Pure procedural composition (>= 2 procedural skills)
        if (proceduralCount >= 2) {
            return true;
        }

        // Rule 5: Pre-extracted body with parent reference or valid cue (MCP compile_skill, CLI, explicit crystallization)
        // Explicit tool compilations provide structured SkillBody directly and intentionally bypass multi-item
        // shape constraints (k >= 2), while denylist filtering and working-only rejection are strictly enforced.
        if (signal.extractedBody() != null && (!signal.parents().isEmpty() || (signal.cue() != null && !signal.cue().isBlank()))) {
            return true;
        }

        log.debug("Skill compilation rejected: cluster does not satisfy admission criteria (episodic={}, semantic={}, procedural={})",
                episodicCount, semanticCount, proceduralCount);
        return false;
    }

    private boolean containsDenylistedContent(final SkillSignal signal) {
        if (signal.cue() != null && matchesDenylist(signal.cue())) {
            return true;
        }
        for (String text : signal.parentTexts()) {
            if (matchesDenylist(text)) {
                return true;
            }
        }
        if (signal.extractedBody() != null) {
            if (matchesDenylist(signal.extractedBody().body())) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesDenylist(final String text) {
        if (text == null || text.isBlank()) return false;
        for (String pattern : DENYLIST_PATTERNS) {
            if (text.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String relayName() {
        return RelayNames.SKILL_ADMIT;
    }
}
