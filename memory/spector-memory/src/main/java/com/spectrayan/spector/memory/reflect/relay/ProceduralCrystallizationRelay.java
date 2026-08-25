/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-memory/LICENSE
 *
 * Change Date: May 27, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.memory.reflect.relay;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.core.similarity.VectorOps;
import com.spectrayan.spector.memory.cortex.EpisodicLogMemory;
import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.graph.HyperEntityGraphMemory;
import com.spectrayan.spector.memory.id.TsidGenerator;
import com.spectrayan.spector.memory.kernel.layout.CognitiveRecordLayout.CognitiveHeader;
import com.spectrayan.spector.memory.kernel.layout.EpisodicFieldAccessor;
import com.spectrayan.spector.memory.kernel.layout.SynapticHeaderConstants;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.pathway.RelayNames;
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
            if (handle.router() != null && handle.router().isEpisodicLogMode()) {
                var logStore = handle.router().episodicLog();
                if (logStore != null) {
                    processLogStoreForSkills(logStore, signal);
                }
            }
        }
        return true;
    }

    private void processLogStoreForSkills(final EpisodicLogMemory logStore, final ReflectSignal signal) {
        List<Long> unconsolidatedOffsets = logStore.unconsolidatedTurnOffsets();
        if (unconsolidatedOffsets.isEmpty()) return;

        List<EpisodicFieldAccessor.EpisodicRecord> turns = logStore.readTurns(unconsolidatedOffsets, true);
        if (turns.size() < 2) return;

        // Group turns by session
        Map<Long, List<EpisodicFieldAccessor.EpisodicRecord>> sessionTurns = new HashMap<>();
        for (var turn : turns) {
            sessionTurns.computeIfAbsent(turn.sessionId(), k -> new ArrayList<>()).add(turn);
        }

        for (Map.Entry<Long, List<EpisodicFieldAccessor.EpisodicRecord>> entry : sessionTurns.entrySet()) {
            List<EpisodicFieldAccessor.EpisodicRecord> sessionList = entry.getValue();
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
                if (signal.rememberPathway() != null) {
                    try {
                        signal.rememberPathway().ingestCognitive(
                                skillId, skillText, vector, MemoryType.PROCEDURAL, tags,
                                MemorySource.REFLECTED,
                                (com.spectrayan.spector.memory.neurodivergent.IngestionHints) null
                        );
                        signal.addProceduralCrystallized(1);
                    } catch (Exception e) {
                        log.warn("Failed to remember procedural skill: {}", e.getMessage());
                    }
                } else if (signal.ingestionTarget() != null) {
                    float exactNorm = vector != null ? VectorOps.magnitude(vector) : 1.0f;
                    byte procFlags = SynapticHeaderConstants.withMemoryType(
                            (byte) 0, MemoryType.PROCEDURAL.ordinal());
                    short soulVer = signal.ingestionTarget().currentSoulVersion();
                    CognitiveHeader header = CognitiveHeader.createSynthetic(
                            System.currentTimeMillis(), 0L, exactNorm, 1.0f,
                            (byte) 0, (byte) 0, procFlags,
                            SynapticHeaderConstants.FLAG_CRYSTALLIZED,
                            soulVer, 0.0f
                    );
                    signal.ingestionTarget().ingestCognitiveWithHeader(
                            skillId, skillText, vector, MemoryType.PROCEDURAL, tags, MemorySource.REFLECTED, header
                    );
                    signal.addProceduralCrystallized(1);
                }

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

    private String extractTurnText(EpisodicFieldAccessor.EpisodicRecord turn) {
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
