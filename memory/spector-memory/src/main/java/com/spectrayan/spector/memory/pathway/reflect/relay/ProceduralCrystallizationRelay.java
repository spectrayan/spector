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
package com.spectrayan.spector.memory.pathway.reflect.relay;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.spectrayan.spector.commons.pathway.PathwayCatalog;
import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.kernel.api.EpisodeRecord;
import com.spectrayan.spector.kernel.store.EpisodicMemory;
import com.spectrayan.spector.memory.pathway.RelayNames;
import com.spectrayan.spector.memory.pathway.skill.SkillPathway;
import com.spectrayan.spector.memory.pathway.skill.relay.SkillSignal;

/**
 * Multi-Scale Crystallization Engine (MSCE) procedural skill formation relay (ADR-0008, ADR-0086 §5.6.1).
 *
 * <p>Pure cluster dispatcher: scans unconsolidated episodic conversation turns, groups them into session
 * clusters, and dispatches them to {@link SkillPathway} with {@link SkillSignal.Mode#COMPILE}.
 * Does not call {@code RememberPathway} directly and does not synthesize raw unverified rules.</p>
 */
public final class ProceduralCrystallizationRelay implements SynapticRelay<ReflectSignal> {

    private static final Logger log = LoggerFactory.getLogger(ProceduralCrystallizationRelay.class);

    @Override
    public boolean transmit(final ReflectSignal signal) {
        if (signal.partitionManager() == null) {
            return true;
        }

        var handles = signal.partitionManager().snapshot();
        for (var handle : handles) {
            if (handle.router() != null) {
                var episodicStore = handle.router().episodic();
                if (episodicStore != null) {
                    processLogStoreForSkills(episodicStore, signal);
                }
            }
        }
        return true;
    }

    private void processLogStoreForSkills(final EpisodicMemory logStore, final ReflectSignal signal) {
        List<Long> unconsolidatedOffsets = logStore.unconsolidatedTurnOffsets();
        if (unconsolidatedOffsets.isEmpty()) return;

        List<EpisodeRecord> turns = logStore.readTurns(unconsolidatedOffsets, true);
        if (turns.size() < 2) return;

        // Group turns by session
        Map<Long, List<EpisodeRecord>> sessionTurns = new HashMap<>();
        for (var turn : turns) {
            sessionTurns.computeIfAbsent(turn.sessionId(), k -> new ArrayList<>()).add(turn);
        }

        final PathwayCatalog catalog = signal.context() != null ? signal.context().catalog() : null;
        if (catalog == null || catalog.find(SkillPathway.class).isEmpty()) {
            log.debug("SkillPathway not available in catalog, skipping skill crystallization dispatch");
            return;
        }

        for (Map.Entry<Long, List<EpisodeRecord>> entry : sessionTurns.entrySet()) {
            List<EpisodeRecord> sessionList = entry.getValue();
            if (sessionList.size() < 2) continue;

            List<SkillSignal.ParentRef> parents = new ArrayList<>();
            for (var t : sessionList) {
                String text = extractTurnText(t);
                parents.add(SkillSignal.ParentRef.episodic(t.sessionId(), t.sequenceId(), text));
            }

            // Dispatch to SkillPathway with Mode.COMPILE, no extractedBody (allows admit & extract relays to run)
            SkillSignal skillSignal = SkillSignal.builder()
                    .mode(SkillSignal.Mode.COMPILE)
                    .parents(parents)
                    .commit(true)
                    .hyperEntityGraph(signal.hyperEntityGraph())
                    .entityDirectory(signal.entityDirectory())
                    .provenanceMemory(signal.provenanceMemory())
                    .build();

            try {
                catalog.invoke(SkillPathway.class, signal.context(), skillSignal);
                if (skillSignal.persistedSkillId() != null || skillSignal.duplicateOf() != null) {
                    signal.addProceduralCrystallized(1);
                }
            } catch (Exception e) {
                log.warn("Failed to dispatch to SkillPathway for session {}: {}", entry.getKey(), e.getMessage());
            }
        }
    }

    private String extractTurnText(EpisodeRecord turn) {
        if (turn.body() == null || turn.body().length == 0) return "";
        try {
            return new String(turn.body(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public String relayName() {
        return RelayNames.PROCEDURAL_CRYSTALLIZATION;
    }
}
