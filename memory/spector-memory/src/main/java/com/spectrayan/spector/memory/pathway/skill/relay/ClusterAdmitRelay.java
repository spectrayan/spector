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

/**
 * Validates cluster shape and admission criteria before skill compilation (ADR-0086 §5.2, §5.6).
 *
 * <p>Admission criteria:
 * <ul>
 *   <li><b>REINFORCE mode</b>: bypasses cluster validation.</li>
 *   <li><b>Episodic cluster</b>: $\ge 2$ episodic turns.</li>
 *   <li><b>Semantic cluster</b>: $\ge 3$ semantic facts.</li>
 *   <li><b>Mixed cluster</b>: $\ge 1$ episodic turn and $\ge 1$ semantic fact.</li>
 *   <li><b>Pre-extracted body</b>: admitted if at least one parent is present.</li>
 * </ul>
 * </p>
 */
public final class ClusterAdmitRelay implements SynapticRelay<SkillSignal> {

    private static final Logger log = LoggerFactory.getLogger(ClusterAdmitRelay.class);

    public static final int MIN_EPISODIC_TURNS = 2;
    public static final int MIN_SEMANTIC_FACTS = 3;

    @Override
    public boolean transmit(final SkillSignal signal) {
        if (signal.mode() == SkillSignal.Mode.REINFORCE) {
            return true;
        }

        int episodicCount = 0;
        int semanticCount = 0;

        for (var parent : signal.parents()) {
            if (parent.type() == MemoryType.EPISODIC) {
                episodicCount++;
            } else if (parent.type() == MemoryType.SEMANTIC) {
                semanticCount++;
            }
        }

        // Pre-extracted body with at least one parent reference
        if (signal.extractedBody() != null && (!signal.parents().isEmpty() || signal.cue() != null)) {
            return true;
        }

        // Rule 1: Episodic session cluster (>= 2 turns)
        if (episodicCount >= MIN_EPISODIC_TURNS) {
            return true;
        }

        // Rule 2: Semantic co-retrieval cluster (>= 3 facts)
        if (semanticCount >= MIN_SEMANTIC_FACTS) {
            return true;
        }

        // Rule 3: Mixed cluster (>= 1 episodic + >= 1 semantic)
        if (episodicCount >= 1 && semanticCount >= 1) {
            return true;
        }

        log.debug("Skill compilation rejected: cluster does not satisfy admission criteria (episodic={}, semantic={})",
                episodicCount, semanticCount);
        return false;
    }

    @Override
    public String relayName() {
        return RelayNames.SKILL_ADMIT;
    }
}
