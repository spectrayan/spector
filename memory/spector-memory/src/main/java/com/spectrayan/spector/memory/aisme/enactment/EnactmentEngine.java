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
import com.spectrayan.spector.memory.model.AgentSoul;
import com.spectrayan.spector.memory.model.enactment.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure cognitive enactment engine operating on SpectorMemory and AISME (ADR-0032).
 */
public final class EnactmentEngine {

    private static final Logger log = LoggerFactory.getLogger(EnactmentEngine.class);

    private EnactmentEngine() {
    }

    /**
     * Executes the full System 1 cognitive loop of persona enactment using default configuration.
     *
     * @param memory the bound SpectorMemory instance (optional)
     * @param soul the agent soul persona
     * @param situation the incoming situation context
     * @param mode the enactment mode (REACT, DECIDE, SIMULATE, REPLAY)
     * @return complete, immutable Enactment result
     */
    public static Enactment enact(SpectorMemory memory, AgentSoul soul, SituationFrame situation, EnactMode mode) {
        return enact(memory, soul, situation, mode, EnactmentConfig.defaultConfig());
    }

    /**
     * Executes the full System 1 cognitive loop of persona enactment with explicit EnactmentConfig.
     *
     * @param memory the bound SpectorMemory instance (optional)
     * @param soul the agent soul persona
     * @param situation the incoming situation context
     * @param mode the enactment mode (REACT, DECIDE, SIMULATE, REPLAY)
     * @param config the enactment configuration
     * @return complete, immutable Enactment result
     */
    public static Enactment enact(
            SpectorMemory memory,
            AgentSoul soul,
            SituationFrame situation,
            EnactMode mode,
            EnactmentConfig config) {

        if (config == null) {
            config = EnactmentConfig.defaultConfig();
        }
        if (situation == null) {
            situation = SituationFrame.of("");
        }
        if (mode == null) {
            mode = EnactMode.REACT;
        }
        if (soul == null) {
            soul = AgentSoul.builder().id("default").name("Default Persona").build();
        }

        AismeBundle bundle = (memory != null) ? memory.aismeBundle() : null;

        // Step 1: Self-Recall (4-cue with Global Workspace conscious bottleneck)
        PersonaRecall.RecallOutput recallOutput = PersonaRecall.recall(memory, soul, situation, mode, config.recall());

        // Step 2: Cognitive Appraisal (VAD Dynamics via Lazarus & Scherer)
        CognitiveAppraisal appraisal = AppraisalEngine.appraise(situation, soul, bundle, recallOutput, config.appraisal());

        // Step 3: Stance Resolution (Hopfield Attractors + EFE Policy Selection)
        StanceResolver.StanceOutput stance = StanceResolver.resolve(soul, bundle, appraisal, recallOutput, situation, config.stance());

        // Step 4: System 2 Bounded Deliberation
        PersonaDeliberation deliberation = buildDeliberation(situation, soul, appraisal, stance, config.deliberation());

        // Step 5: Embodiment Utterance under Epistemic Tense (ADR-0031)
        String tense = switch (mode) {
            case SIMULATE -> "SIM";
            case REPLAY -> "REPLAY";
            default -> "FACT";
        };

        String utterance = generateEmbodiedUtterance(situation, soul, appraisal, stance, deliberation, mode);

        log.debug("Enactment completed for soul={}, mode={}, tense={}, confidence={}",
                soul.id(), mode, tense, stance.confidence());

        return new Enactment(
                situation,
                appraisal,
                stance.activeAttractor(),
                recallOutput.results(),
                stance.policyReport(),
                deliberation,
                stance.intendedActs(),
                utterance,
                stance.confidence(),
                tense,
                recallOutput.citations(),
                stance.vetoes()
        );
    }

    private static PersonaDeliberation buildDeliberation(
            SituationFrame situation,
            AgentSoul soul,
            CognitiveAppraisal appraisal,
            StanceResolver.StanceOutput stance,
            DeliberationConfig config) {

        String activeDogma = (soul.coreValues() != null && !soul.coreValues().isEmpty())
                ? soul.coreValues().get(0)
                : config.fallbackDogma();

        TradeOffSelection tradeOffs = new TradeOffSelection(
                activeDogma,
                config.defaultTradeOffDeprioritized(),
                config.defaultTradeOffRationale()
        );

        List<String> blindSpots = new ArrayList<>();
        if (appraisal.urgencyAndStakes() > config.urgencyBlindSpotThreshold()) {
            blindSpots.add(config.urgencyBlindSpotMessage());
        }
        if (appraisal.copingPotential() < config.copingDefensivenessThreshold()) {
            blindSpots.add(config.copingDefensivenessMessage());
        }

        String tacticalFirstMove = stance.policyReport().selectedPolicy() != null
                ? "Execute " + stance.policyReport().selectedPolicy().policyType().name() + " within authorized tool boundaries"
                : "Assess problem parameters and gather additional telemetry";

        String monologue = String.format(
                "Appraising '%s' as %s (V=%.2f, A=%.2f, D=%.2f). Under my dogma of '%s', my focus is to %s.",
                situation.problem(),
                appraisal.agency(),
                appraisal.goalCongruence(),
                appraisal.urgencyAndStakes(),
                appraisal.copingPotential(),
                activeDogma,
                tacticalFirstMove
        );

        return new PersonaDeliberation(
                monologue,
                activeDogma,
                tradeOffs,
                blindSpots,
                tacticalFirstMove
        );
    }

    private static String generateEmbodiedUtterance(
            SituationFrame situation,
            AgentSoul soul,
            CognitiveAppraisal appraisal,
            StanceResolver.StanceOutput stance,
            PersonaDeliberation deliberation,
            EnactMode mode) {

        StringBuilder sb = new StringBuilder();

        if (mode == EnactMode.SIMULATE) {
            sb.append("[SIMULATION] Hypothetical response for ").append(soul.name()).append(":\n");
        }

        if (stance.confidence() == ConfidenceLevel.INFERRED) {
            sb.append("(Hedging: Based on general principles rather than direct autobiographical precedents) ");
        }

        if (!stance.vetoes().isEmpty()) {
            sb.append("I must refuse or restrict action due to ethical/policy constraints: ")
                    .append(String.join(", ", stance.vetoes()))
                    .append(". ");
        }

        sb.append(deliberation.tacticalFirstMove()).append(". ");
        sb.append("Rationale: ").append(deliberation.tradeOffs().rationale());

        return sb.toString();
    }
}
