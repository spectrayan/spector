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
            List<CognitiveResult> dogmas,
            List<CognitiveResult> causalModels,
            List<CognitiveResult> scars,
            List<CognitiveResult> livedEpisodes,
            List<CognitiveResult> playbooks,
            List<CognitiveResult> workingState
    ) {
        public RecallOutput {
            results = (results != null) ? List.copyOf(results) : List.of();
            citations = (citations != null) ? List.copyOf(citations) : List.of();
            dogmas = (dogmas != null) ? List.copyOf(dogmas) : List.of();
            causalModels = (causalModels != null) ? List.copyOf(causalModels) : List.of();
            scars = (scars != null) ? List.copyOf(scars) : List.of();
            livedEpisodes = (livedEpisodes != null) ? List.copyOf(livedEpisodes) : List.of();
            playbooks = (playbooks != null) ? List.copyOf(playbooks) : List.of();
            workingState = (workingState != null) ? List.copyOf(workingState) : List.of();
        }

        public static RecallOutput empty() {
            return new RecallOutput(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        }

        /** Backward-compatible view of constitution memories (dogmas + causal models). */
        public List<CognitiveResult> constitution() {
            List<CognitiveResult> combined = new ArrayList<>(dogmas);
            combined.addAll(causalModels);
            return combined;
        }

        /** Backward-compatible view of episodic analogues (scars + lived episodes). */
        public List<CognitiveResult> analogues() {
            List<CognitiveResult> combined = new ArrayList<>(scars);
            combined.addAll(livedEpisodes);
            return combined;
        }
    }

    /**
     * Executes 4-cue persona self-recall with default configuration.
     *
     * @param memory the bound SpectorMemory instance
     * @param soul the acting agent soul
     * @param situation the situation frame
     * @param mode the enactment mode
     * @return structured RecallOutput
     */
    public static RecallOutput recall(SpectorMemory memory, AgentSoul soul, SituationFrame situation, EnactMode mode) {
        return recall(memory, soul, situation, mode, RecallConfig.defaultConfig());
    }

    /**
     * Executes 4-cue persona self-recall with explicit RecallConfig.
     *
     * @param memory the bound SpectorMemory instance
     * @param soul the acting agent soul
     * @param situation the situation frame
     * @param mode the enactment mode
     * @param config the recall configuration
     * @return structured RecallOutput
     */
    public static RecallOutput recall(
            SpectorMemory memory,
            AgentSoul soul,
            SituationFrame situation,
            EnactMode mode,
            RecallConfig config) {

        if (config == null) {
            config = RecallConfig.defaultConfig();
        }
        if (memory == null) {
            return RecallOutput.empty();
        }

        String query = situation != null && !situation.problem().isBlank() ? situation.problem() : config.defaultQuery();
        boolean allowSynthetic = (mode == EnactMode.SIMULATE);
        boolean isReplay = (mode == EnactMode.REPLAY);
        String personaId = (soul != null && soul.id() != null) ? soul.id() : "";
        java.time.Instant replayTs = (isReplay && situation != null) ? situation.asOf() : null;

        List<CognitiveResult> dogmas = new ArrayList<>();
        List<CognitiveResult> causalModels = new ArrayList<>();
        List<CognitiveResult> scars = new ArrayList<>();
        List<CognitiveResult> livedEpisodes = new ArrayList<>();
        List<CognitiveResult> playbooks = new ArrayList<>();
        List<CognitiveResult> workingState = new ArrayList<>();

        // 1. Semantic Cue (Constitution & Invariants & Causal Models)
        try {
            RecallOptions.Builder semBldr = RecallOptions.builder()
                    .memoryTypes(MemoryType.SEMANTIC)
                    .topK(config.semanticTopK())
                    .allowSimulated(allowSynthetic)
                    .personaId(personaId);
            if (isReplay) {
                semBldr.recallMode(com.spectrayan.spector.memory.model.RecallMode.REPLAY);
                if (replayTs != null) {
                    semBldr.replayTimestamp(replayTs);
                }
            }
            List<CognitiveResult> semResults = memory.recall(query, semBldr.build());
            for (CognitiveResult cr : semResults) {
                if (hasAnyTag(cr, "causal_model", "mental_model", "heuristic", "architecture", "causal")) {
                    causalModels.add(cr);
                } else {
                    dogmas.add(cr);
                }
            }
        } catch (SecurityException | IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Semantic self-recall encountered error: {}", e.getMessage());
        }

        // 2. Episodic Cue (Autobiography: Scars vs Lived Episodes)
        try {
            RecallOptions.Builder epiBldr = RecallOptions.builder()
                    .memoryTypes(MemoryType.EPISODIC)
                    .topK(config.episodicTopK())
                    .allowSimulated(allowSynthetic)
                    .personaId(personaId);
            if (isReplay) {
                epiBldr.recallMode(com.spectrayan.spector.memory.model.RecallMode.REPLAY);
                if (replayTs != null) {
                    epiBldr.replayTimestamp(replayTs);
                }
            }
            List<CognitiveResult> epiResults = memory.recall(query, epiBldr.build());
            for (CognitiveResult cr : epiResults) {
                boolean isScar = hasAnyTag(cr, config.scarTags())
                        || cr.valence() <= config.scarMaxValence()
                        || cr.isNegativeOutcome();
                if (isScar) {
                    scars.add(cr);
                } else if (!cr.isSimulated()) {
                    livedEpisodes.add(cr);
                }
            }
        } catch (SecurityException | IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Episodic self-recall encountered error: {}", e.getMessage());
        }

        // 3. Procedural Cue (Habits, Playbooks)
        try {
            RecallOptions.Builder procBldr = RecallOptions.builder()
                    .memoryTypes(MemoryType.PROCEDURAL)
                    .topK(config.proceduralTopK())
                    .allowSimulated(allowSynthetic)
                    .personaId(personaId);
            if (isReplay) {
                procBldr.recallMode(com.spectrayan.spector.memory.model.RecallMode.REPLAY);
                if (replayTs != null) {
                    procBldr.replayTimestamp(replayTs);
                }
            }
            playbooks = memory.recall(query, procBldr.build());
        } catch (SecurityException | IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Procedural self-recall encountered error: {}", e.getMessage());
        }

        // 4. Working Cue (Affect, Active Loop)
        try {
            RecallOptions.Builder workBldr = RecallOptions.builder()
                    .memoryTypes(MemoryType.WORKING)
                    .topK(config.workingTopK())
                    .allowSimulated(allowSynthetic)
                    .personaId(personaId);
            if (isReplay) {
                workBldr.recallMode(com.spectrayan.spector.memory.model.RecallMode.REPLAY);
                if (replayTs != null) {
                    workBldr.replayTimestamp(replayTs);
                }
            }
            workingState = memory.recall(query, workBldr.build());
        } catch (SecurityException | IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Working self-recall encountered error: {}", e.getMessage());
        }

        // Aggregate candidates with ID deduplication
        java.util.Map<String, CognitiveResult> uniqueCandidates = new java.util.LinkedHashMap<>();
        for (CognitiveResult cr : dogmas) uniqueCandidates.putIfAbsent(cr.id(), cr);
        for (CognitiveResult cr : causalModels) uniqueCandidates.putIfAbsent(cr.id(), cr);
        for (CognitiveResult cr : scars) uniqueCandidates.putIfAbsent(cr.id(), cr);
        for (CognitiveResult cr : livedEpisodes) uniqueCandidates.putIfAbsent(cr.id(), cr);
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
                log.warn("GlobalWorkspace competition fallback: {}", e.getMessage());
            }
        }

        // Filter out synthetic rows if FACT or REPLAY mode (ADR-0031 Invariant I2)
        if (!allowSynthetic) {
            consciousAccess = consciousAccess.stream()
                    .filter(cr -> cr.source() == null || cr.source().toEngramSource() != com.spectrayan.spector.memory.model.EngramSource.SIMULATED)
                    .filter(cr -> !cr.isSimulated() && !cr.isDreamed())
                    .toList();
        }

        // Build citations with unboxed tag strings
        List<EngramCitation> citations = new ArrayList<>();
        for (CognitiveResult cr : consciousAccess) {
            boolean isSynthetic = cr.isSimulated() || cr.isDreamed();
            List<String> tags = cr.synapticTags() != null
                    ? java.util.Arrays.asList(cr.synapticTags())
                    : List.of();
            citations.add(new EngramCitation(
                    cr.id(),
                    cr.memoryType(),
                    tags,
                    cr.score(),
                    isSynthetic
            ));
        }

        return new RecallOutput(
                consciousAccess,
                citations,
                dogmas,
                causalModels,
                scars,
                livedEpisodes,
                playbooks,
                workingState
        );
    }

    private static boolean hasAnyTag(CognitiveResult cr, java.util.Collection<String> targetTags) {
        if (cr == null || cr.synapticTags() == null || targetTags == null || targetTags.isEmpty()) return false;
        for (String tag : cr.synapticTags()) {
            if (tag != null) {
                String lower = tag.toLowerCase(java.util.Locale.ROOT);
                for (String target : targetTags) {
                    if (target != null && (lower.equals(target.toLowerCase(java.util.Locale.ROOT))
                            || lower.contains(target.toLowerCase(java.util.Locale.ROOT)))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean hasAnyTag(CognitiveResult cr, String... targetTags) {
        return hasAnyTag(cr, java.util.List.of(targetTags));
    }
}
