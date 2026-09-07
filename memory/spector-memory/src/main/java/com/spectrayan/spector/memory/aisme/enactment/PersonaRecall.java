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
package com.spectrayan.spector.memory.aisme.enactment;

import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.aisme.AismeBundle;
import com.spectrayan.spector.memory.aisme.workspace.GlobalWorkspace;
import com.spectrayan.spector.memory.model.AgentSoul;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.model.enactment.EnactMode;
import com.spectrayan.spector.memory.model.enactment.EngramCitation;
import com.spectrayan.spector.memory.model.enactment.SituationFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Executes 4-cue self-recall against bound SpectorMemory with GlobalWorkspace conscious filtering (ADR-0032).
 */
public final class PersonaRecall {

    private static final Logger log = LoggerFactory.getLogger(PersonaRecall.class);

    private PersonaRecall() {
    }

    public record RecallOutput(
            List<CognitiveResult> results,
            List<EngramCitation> citations,
            List<CognitiveResult> constitution,
            List<CognitiveResult> analogues,
            List<CognitiveResult> playbooks,
            List<CognitiveResult> workingState
    ) {}

    /**
     * Executes 4-cue persona self-recall.
     *
     * @param memory the bound SpectorMemory instance
     * @param soul the acting agent soul
     * @param situation the situation frame
     * @param mode the enactment mode
     * @return structured RecallOutput
     */
    public static RecallOutput recall(SpectorMemory memory, AgentSoul soul, SituationFrame situation, EnactMode mode) {
        if (memory == null) {
            return new RecallOutput(List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        }

        String query = situation != null && !situation.problem().isBlank() ? situation.problem() : "self";
        boolean allowSynthetic = (mode == EnactMode.SIMULATE);

        List<CognitiveResult> constitution = new ArrayList<>();
        List<CognitiveResult> analogues = new ArrayList<>();
        List<CognitiveResult> playbooks = new ArrayList<>();
        List<CognitiveResult> workingState = new ArrayList<>();

        // 1. Semantic Cue (Constitution & Invariants)
        try {
            RecallOptions semOpts = RecallOptions.builder()
                    .memoryTypes(MemoryType.SEMANTIC)
                    .topK(8)
                    .build();
            constitution = memory.recall(query, semOpts);
        } catch (Exception e) {
            log.debug("Semantic self-recall skipped or empty: {}", e.getMessage());
        }

        // 2. Episodic Cue (Autobiography, Reactions, Scars)
        try {
            RecallOptions epiOpts = RecallOptions.builder()
                    .memoryTypes(MemoryType.EPISODIC)
                    .topK(5)
                    .build();
            analogues = memory.recall(query, epiOpts);
        } catch (Exception e) {
            log.debug("Episodic self-recall skipped or empty: {}", e.getMessage());
        }

        // 3. Procedural Cue (Habits, Playbooks)
        try {
            RecallOptions procOpts = RecallOptions.builder()
                    .memoryTypes(MemoryType.PROCEDURAL)
                    .topK(5)
                    .build();
            playbooks = memory.recall(query, procOpts);
        } catch (Exception e) {
            log.debug("Procedural self-recall skipped or empty: {}", e.getMessage());
        }

        // 4. Working Cue (Affect, Active Loop)
        try {
            RecallOptions workOpts = RecallOptions.builder()
                    .memoryTypes(MemoryType.WORKING)
                    .topK(5)
                    .build();
            workingState = memory.recall(query, workOpts);
        } catch (Exception e) {
            log.debug("Working self-recall skipped or empty: {}", e.getMessage());
        }

        // Aggregate candidates with ID deduplication
        java.util.Map<String, CognitiveResult> uniqueCandidates = new java.util.LinkedHashMap<>();
        for (CognitiveResult cr : constitution) uniqueCandidates.putIfAbsent(cr.id(), cr);
        for (CognitiveResult cr : analogues) uniqueCandidates.putIfAbsent(cr.id(), cr);
        for (CognitiveResult cr : playbooks) uniqueCandidates.putIfAbsent(cr.id(), cr);
        for (CognitiveResult cr : workingState) uniqueCandidates.putIfAbsent(cr.id(), cr);
        List<CognitiveResult> allCandidates = new ArrayList<>(uniqueCandidates.values());

        // Apply Global Workspace conscious access bottleneck (~7 items) if AISME is active
        List<CognitiveResult> consciousAccess = allCandidates;
        AismeBundle bundle = memory.aismeBundle();
        if (bundle != null && bundle.globalWorkspace() != null) {
            GlobalWorkspace gw = bundle.globalWorkspace();
            try {
                consciousAccess = gw.filterForBroadcast(allCandidates);
            } catch (Exception e) {
                log.debug("GlobalWorkspace competition fallback: {}", e.getMessage());
            }
        }

        // Filter out synthetic rows if FACT mode (ADR-0031 Invariant I2)
        if (!allowSynthetic) {
            consciousAccess = consciousAccess.stream()
                    .filter(cr -> cr.source() == null || cr.source().toEngramSource() != com.spectrayan.spector.memory.model.EngramSource.SIMULATED)
                    .toList();
        }

        // Build citations
        List<EngramCitation> citations = new ArrayList<>();
        for (CognitiveResult cr : consciousAccess) {
            boolean isSynthetic = cr.source() != null && cr.source().toEngramSource() == com.spectrayan.spector.memory.model.EngramSource.SIMULATED;
            citations.add(new EngramCitation(
                    cr.id(),
                    cr.memoryType(),
                    List.of(cr.synapticTags() != null ? cr.synapticTags() : new String[0]),
                    cr.score(),
                    isSynthetic
            ));
        }

        return new RecallOutput(
                consciousAccess,
                citations,
                constitution,
                analogues,
                playbooks,
                workingState
        );
    }
}
