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
import com.spectrayan.spector.memory.pathway.remember.RememberPathway;
import com.spectrayan.spector.memory.pathway.remember.relay.RememberSignal;
import com.spectrayan.spector.core.similarity.VectorOps;
import com.spectrayan.spector.kernel.store.EpisodicMemory;
import com.spectrayan.spector.kernel.api.MemorySource;
import com.spectrayan.spector.kernel.store.HyperEntityGraphMemory;
import com.spectrayan.spector.kernel.id.TsidGenerator;
import com.spectrayan.spector.kernel.engram.EncodingHeader;
import com.spectrayan.spector.kernel.api.EpisodeRecord;
import com.spectrayan.spector.kernel.engram.field.EncodingHeaderFields;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.memory.pathway.RelayNames;
import com.spectrayan.spector.memory.pathway.SoulVersionSource;
import com.spectrayan.spector.memory.pathway.skill.SkillPathway;
import com.spectrayan.spector.memory.pathway.skill.model.SkillBody;
import com.spectrayan.spector.memory.pathway.skill.relay.SkillSignal;
import com.spectrayan.spector.provider.generation.GenerationOptions;

/**
 * Multi-Scale Crystallization Engine (MSCE) procedural skill formation relay (ADR-0008).
 *
 * <p>Synthesizes recurring problem-solving episodic sequences into fast, compiled
 * {@link MemoryType#PROCEDURAL} decision heuristics stamped with {@code FLAG_CRYSTALLIZED},
 * preserving causal graph lineage via {@link HyperEntityGraphMemory#ROLE_DERIVED_FROM}.</p>
 */
public final class ProceduralCrystallizationRelay implements SynapticRelay<ReflectSignal> {

    private static final Logger log = LoggerFactory.getLogger(ProceduralCrystallizationRelay.class);
    private static final TsidGenerator TSID = new TsidGenerator();

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

        for (Map.Entry<Long, List<EpisodeRecord>> entry : sessionTurns.entrySet()) {
            List<EpisodeRecord> sessionList = entry.getValue();
            if (sessionList.size() < 2) continue;

            List<String> turnTexts = new ArrayList<>();
            for (var t : sessionList) {
                String text = extractTurnText(t);
                if (!text.isBlank()) {
                    turnTexts.add(text);
                }
            }

            if (turnTexts.size() < 2) continue;

            List<String> skills = distillSkills(turnTexts, signal);
            for (String skillText : skills) {
                String skillId = TSID.generate();
                float[] vector = null;
                if (signal.embeddingProvider() != null) {
                    try {
                        vector = signal.embeddingProvider().embed(skillText).vector();
                    } catch (Exception e) {
                        log.warn("Failed to generate embedding for crystallized skill: {}", e.getMessage());
                    }
                }

                String[] tags = new String[]{"procedural", "crystallized", "skill"};
                final PathwayCatalog catalog = signal.context() != null ? signal.context().catalog() : null;
                if (catalog != null && catalog.find(SkillPathway.class).isPresent()) {
                    List<SkillSignal.ParentRef> parents = new ArrayList<>();
                    for (var t : sessionList) {
                        parents.add(new SkillSignal.ParentRef(t.sessionId() + "-" + t.sequenceId(), MemoryType.EPISODIC));
                    }
                    SkillBody skillBody = SkillBody.parse(skillText);
                    SkillSignal skillSignal = new SkillSignal(
                            SkillSignal.Mode.COMPILE,
                            parents,
                            null,
                            true,
                            null,
                            0.0f,
                            signal.hyperEntityGraph(),
                            signal.entityDirectory(),
                            signal.provenanceMemory()
                    );
                    skillSignal.extractedBody(skillBody);
                    try {
                        catalog.invoke(SkillPathway.class, signal.context(), skillSignal);
                        signal.addProceduralCrystallized(1);
                    } catch (Exception e) {
                        log.warn("Failed to invoke SkillPathway via catalog for skill {}: {}", skillId, e.getMessage());
                    }
                } else if (catalog != null && catalog.find(RememberPathway.class).isPresent()) {
                    float exactNorm = vector != null ? VectorOps.magnitude(vector) : 1.0f;
                    byte procFlags = EncodingHeaderFields.withMemoryType(
                            (byte) 0, MemoryType.PROCEDURAL.ordinal());
                    short soulVer = signal.context() != null
                            ? signal.context().find(SoulVersionSource.class)
                                    .map(SoulVersionSource::currentSoulVersion).orElse((short) 0)
                            : (short) 0;
                    EncodingHeader header = EncodingHeader.createSynthetic(
                            System.currentTimeMillis(), 0L, exactNorm, 1.0f,
                            (byte) 0, (byte) 0, procFlags,
                            EncodingHeaderFields.FLAG_CRYSTALLIZED,
                            soulVer, 0.0f
                    );
                    RememberSignal rs = RememberSignal.forCognitiveWithHeader(
                            skillId, skillText, vector, MemoryType.PROCEDURAL,
                            tags, MemorySource.REFLECTED, header);
                    try {
                        catalog.invoke(RememberPathway.class, signal.context(), rs);
                    } catch (Exception e) {
                        log.warn("Failed to invoke Remember via catalog for skill {}: {}", skillId, e.getMessage());
                    }
                    signal.addProceduralCrystallized(1);

                    // Link procedural skill to hypergraph lineage
                    if (signal.hyperEntityGraph() != null && signal.entityDirectory() != null) {
                        try {
                            int skillEntityId = signal.entityDirectory().intern("skill:" + skillId, "PROCEDURAL_SKILL");
                            int[] entities = new int[]{skillEntityId};
                            int[] roles = new int[]{HyperEntityGraphMemory.ROLE_DERIVED_FROM};
                            signal.hyperEntityGraph().addHyperedge(
                                    entities, roles, HyperEntityGraphMemory.TYPE_RELATIONSHIP, 1.0f, 0, System.currentTimeMillis()
                            );
                        } catch (Exception e) {
                            log.debug("HyperEntity lineage linking skipped: {}", e.getMessage());
                        }
                    }
                }
            }
        }
    }

    private List<String> distillSkills(List<String> turnTexts, ReflectSignal signal) {
        if (signal.textGenerator() != null && signal.templateEngine() != null) {
            try {
                Map<String, Object> model = Map.of(
                        "interactionCount", turnTexts.size(),
                        "interactions", turnTexts
                );
                String prompt = "Synthesize the following problem-solving steps into a generalized procedural heuristic rule:\n"
                        + String.join("\n", turnTexts)
                        + "\n\nOutput a concise heuristic rule prefixed with 'PROCEDURAL RULE: '";
                String response = signal.textGenerator().generate(prompt, GenerationOptions.CONCISE);

                if (response != null && !response.isBlank()) {
                    return List.of(response.strip());
                }
            } catch (Exception e) {
                log.warn("Skill distillation LLM generation failed, using fallback: {}", e.getMessage());
            }
        }

        // Algorithmic pattern heuristic fallback
        return List.of("Procedural Skill Pattern: " + turnTexts.get(0).substring(0, Math.min(100, turnTexts.get(0).length())));
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
