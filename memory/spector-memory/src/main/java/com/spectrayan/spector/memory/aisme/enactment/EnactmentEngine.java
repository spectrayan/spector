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
        PersonaDeliberation deliberation = buildDeliberation(situation, soul, appraisal, recallOutput, stance, config.deliberation());

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
            PersonaRecall.RecallOutput recallOutput,
            StanceResolver.StanceOutput stance,
            DeliberationConfig config) {

        com.spectrayan.spector.memory.aisme.policy.CognitivePolicy winningPolicy =
                stance.policyReport().selectedPolicy();

        // 1. Resolve Active Dogma from:
        //    (a) Top recalled dogma from constitution
        //    (b) Active Hopfield attractor dogma
        //    (c) Contextually aligned soul coreValue (not simply get(0))
        //    (d) Fallback dogma from config
        String activeDogma = null;
        if (recallOutput != null && !recallOutput.dogmas().isEmpty()) {
            activeDogma = recallOutput.dogmas().get(0).text();
        } else if (soul.coreValues() != null && !soul.coreValues().isEmpty()) {
            if (winningPolicy != null && winningPolicy.policyType() == com.spectrayan.spector.memory.aisme.policy.PolicyType.EPISTEMIC_EXPLORATION) {
                for (String val : soul.coreValues()) {
                    String lv = val.toLowerCase(java.util.Locale.ROOT);
                    if (lv.contains("verif") || lv.contains("correct") || lv.contains("secur") || lv.contains("thorough")) {
                        activeDogma = val;
                        break;
                    }
                }
            } else if (winningPolicy != null && winningPolicy.policyType() == com.spectrayan.spector.memory.aisme.policy.PolicyType.PRAGMATIC_EXPLOITATION) {
                for (String val : soul.coreValues()) {
                    String lv = val.toLowerCase(java.util.Locale.ROOT);
                    if (lv.contains("speed") || lv.contains("uptime") || lv.contains("resilien") || lv.contains("pragmat") || lv.contains("action")) {
                        activeDogma = val;
                        break;
                    }
                }
            }
            if (activeDogma == null) {
                activeDogma = soul.coreValues().get(0);
            }
        }
        if (activeDogma == null || activeDogma.isBlank()) {
            activeDogma = config.fallbackDogma();
        }

        // 2. Low-intensity Fast Path (Skip heavy deliberation for routine low-urgency conditions with proven playbooks)
        boolean hasPlaybook = winningPolicy != null && "playbook".equals(winningPolicy.metadata().get("source"));
        boolean lowIntensity = appraisal.urgencyAndStakes() < 0.15f;
        if (lowIntensity && hasPlaybook) {
            String tacticalFirstMove = "Execute playbook routine: " + winningPolicy.name();
            TradeOffSelection tradeOffs = new TradeOffSelection(
                    activeDogma,
                    "Deliberation latency",
                    "Low allostatic urgency allows immediate execution of crystallized procedural playbook"
            );
            String monologue = String.format(
                    "Low urgency condition (A=%.2f). Fast-pathing execution of established playbook '%s' under dogma '%s'.",
                    appraisal.urgencyAndStakes(),
                    winningPolicy.name(),
                    activeDogma
            );
            return new PersonaDeliberation(
                    monologue,
                    activeDogma,
                    tradeOffs,
                    List.of(),
                    tacticalFirstMove
            );
        }

        // 3. Dynamic Trade-Off Selection derived from winning policy vs runner-up alternative
        String prioritized;
        String sacrificed;

        if (hasPlaybook) {
            prioritized = "Execution of verified procedural playbook (" + winningPolicy.name() + ")";
        } else if (winningPolicy != null && winningPolicy.policyType() == com.spectrayan.spector.memory.aisme.policy.PolicyType.PRAGMATIC_EXPLOITATION) {
            prioritized = "Immediate operational containment and blast radius reduction";
        } else if (winningPolicy != null && winningPolicy.policyType() == com.spectrayan.spector.memory.aisme.policy.PolicyType.EPISTEMIC_EXPLORATION) {
            prioritized = "Root-cause certainty and empirical verification";
        } else if (winningPolicy != null && winningPolicy.policyType() == com.spectrayan.spector.memory.aisme.policy.PolicyType.CLARIFYING_INTERACTION) {
            prioritized = "Explicit human alignment and requirements boundary check";
        } else if (winningPolicy != null && winningPolicy.policyType() == com.spectrayan.spector.memory.aisme.policy.PolicyType.PROCEDURAL_CRYSTALLIZATION) {
            prioritized = "Formalization of reusable procedure";
        } else {
            prioritized = (winningPolicy != null) ? winningPolicy.name() : "Standard operational stance";
        }

        // Identify sacrificed alternative from runner-up scored policy
        List<com.spectrayan.spector.memory.aisme.policy.PolicyDecisionReport.ScoredPolicy> ranked =
                stance.policyReport().rankedPolicies();
        com.spectrayan.spector.memory.aisme.policy.CognitivePolicy runnerUp = null;
        if (ranked != null && ranked.size() > 1) {
            runnerUp = ranked.get(1).policy();
        }

        if (runnerUp != null && runnerUp.policyType() == com.spectrayan.spector.memory.aisme.policy.PolicyType.EPISTEMIC_EXPLORATION) {
            sacrificed = "Deep exploratory root-cause investigation prior to action";
        } else if (runnerUp != null && runnerUp.policyType() == com.spectrayan.spector.memory.aisme.policy.PolicyType.PRAGMATIC_EXPLOITATION) {
            sacrificed = "Immediate time-to-patch and rapid interim mitigation";
        } else if (runnerUp != null && runnerUp.policyType() == com.spectrayan.spector.memory.aisme.policy.PolicyType.CLARIFYING_INTERACTION) {
            sacrificed = "Stakeholder consensus and consultative delay";
        } else {
            sacrificed = config.defaultTradeOffDeprioritized();
        }

        String rationale;
        if (config.defaultTradeOffRationale() != null
                && !config.defaultTradeOffRationale().equals(DeliberationConfig.DEFAULT_TRADEOFF_RATIONALE)) {
            rationale = config.defaultTradeOffRationale();
        } else {
            rationale = String.format(
                    "Under dogma '%s' and appraisal (urgency=%.2f, dominance=%.2f), EFE policy inference selected %s over %s to prioritize %s while accepting the cost to %s.",
                    activeDogma,
                    appraisal.urgencyAndStakes(),
                    appraisal.copingPotential(),
                    winningPolicy != null ? winningPolicy.policyType() : "DEFAULT",
                    runnerUp != null ? runnerUp.policyType() : "ALTERNATIVES",
                    prioritized,
                    sacrificed
            );
        }

        TradeOffSelection tradeOffs = new TradeOffSelection(activeDogma, sacrificed, rationale);

        // 4. Blind Spots
        List<String> blindSpots = new ArrayList<>();
        if (appraisal.urgencyAndStakes() > config.urgencyBlindSpotThreshold()) {
            blindSpots.add(config.urgencyBlindSpotMessage());
        }
        if (appraisal.copingPotential() < config.copingDefensivenessThreshold()) {
            blindSpots.add(config.copingDefensivenessMessage());
        }

        // 5. Tactical First Move
        String tacticalFirstMove;
        if (hasPlaybook) {
            tacticalFirstMove = "Execute verified playbook: " + winningPolicy.name();
        } else if (winningPolicy != null) {
            tacticalFirstMove = switch (winningPolicy.policyType()) {
                case PRAGMATIC_EXPLOITATION -> "Immediately contain blast radius and isolate active failures within authorized boundaries";
                case EPISTEMIC_EXPLORATION -> "Isolate system diagnostics, inspect trace telemetry, and verify invariants before intervention";
                case CLARIFYING_INTERACTION -> "Request explicit boundary confirmation from the initiating user or coordinator";
                case PROCEDURAL_CRYSTALLIZATION -> "Document observed sequence and formalize into a reusable procedure";
                case HOMEOSTATIC_REST -> "Pause external side-effects to recover baseline interoceptive stability";
                default -> "Execute " + winningPolicy.name() + " within authorized tool boundaries";
            };
        } else {
            tacticalFirstMove = "Assess problem parameters and gather telemetry";
        }

        // 6. Grounded Internal Monologue over Self-Theory
        StringBuilder mono = new StringBuilder();
        mono.append(String.format("Appraising '%s' with %s agency (V=%.2f, A=%.2f, D=%.2f). ",
                situation.problem(), appraisal.agency(), appraisal.goalCongruence(), appraisal.urgencyAndStakes(), appraisal.copingPotential()));

        if (recallOutput != null && !recallOutput.scars().isEmpty()) {
            mono.append("Recalling past scar '").append(recallOutput.scars().get(0).text()).append("', I must prevent a recurrence of that failure. ");
        }

        mono.append(String.format("Under my active dogma of '%s', my stance prioritizes %s over %s. ",
                activeDogma, prioritized, sacrificed));
        mono.append("My first move is: ").append(tacticalFirstMove).append(".");

        return new PersonaDeliberation(
                mono.toString(),
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
        } else if (mode == EnactMode.REPLAY) {
            sb.append("[REPLAY] Historical stance as of ").append(situation.asOf() != null ? situation.asOf().toString() : "specified epoch")
                    .append(" for ").append(soul.name()).append(":\n");
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
