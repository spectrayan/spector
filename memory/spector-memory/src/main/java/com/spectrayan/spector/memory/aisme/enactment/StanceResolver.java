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

import com.spectrayan.spector.memory.aisme.AismeBundle;
import com.spectrayan.spector.memory.aisme.hopfield.AttractorState;
import com.spectrayan.spector.memory.aisme.hopfield.AttractorType;
import com.spectrayan.spector.memory.aisme.policy.CognitivePolicy;
import com.spectrayan.spector.memory.aisme.policy.PolicyDecisionReport;
import com.spectrayan.spector.memory.aisme.policy.PolicyType;
import com.spectrayan.spector.memory.model.AgentSoul;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.enactment.ConfidenceLevel;
import com.spectrayan.spector.memory.model.enactment.CognitiveAppraisal;
import com.spectrayan.spector.memory.model.enactment.SituationFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Resolves the persona stance by integrating Continuous Hopfield Attractor convergence,
 * Expected Free Energy (EFE) policy selection, and ancestral guardrail vetoes (ADR-0032).
 */
public final class StanceResolver {

    private static final Logger log = LoggerFactory.getLogger(StanceResolver.class);

    private StanceResolver() {
    }

    public record StanceOutput(
            AttractorState activeAttractor,
            PolicyDecisionReport policyReport,
            ConfidenceLevel confidence,
            Set<String> intendedActs,
            List<String> vetoes
    ) {}

    /**
     * Resolves the active attractor, policy inference, and action constraints for the persona.
     *
     * @param soul the acting agent soul
     * @param aismeBundle the active inference self-model bundle (optional)
     * @param appraisal the cognitive appraisal vector
     * @param recallOutput self-recall results
     * @param situation the incoming situation frame
     * @return StanceOutput
     */
    public static StanceOutput resolve(
            AgentSoul soul,
            AismeBundle aismeBundle,
            CognitiveAppraisal appraisal,
            PersonaRecall.RecallOutput recallOutput,
            SituationFrame situation) {

        // 1. Hopfield Attractor Convergence
        AttractorState activeAttractor = null;
        float[] sensoryState = new float[]{appraisal.goalCongruence(), appraisal.urgencyAndStakes(), appraisal.copingPotential()};

        if (aismeBundle != null && aismeBundle.hopfieldNetwork() != null && recallOutput != null && !recallOutput.results().isEmpty()) {
            try {
                // If candidates have embeddings, relax into nearest attractor basin
                List<CognitiveResult> items = recallOutput.results();
                float[][] patterns = new float[items.size()][sensoryState.length];
                for (int i = 0; i < items.size(); i++) {
                    patterns[i] = new float[]{
                            items.get(i).score(),
                            items.get(i).importance(),
                            (items.get(i).valence() + 128) / 255.0f
                    };
                }
                activeAttractor = aismeBundle.hopfieldNetwork().retrieveAttractor(sensoryState, patterns, 2.0f);
            } catch (Exception e) {
                log.debug("Hopfield attractor convergence fallback: {}", e.getMessage());
            }
        }

        if (activeAttractor == null) {
            AttractorType type = appraisal.urgencyAndStakes() > 0.7f ? AttractorType.FIXED_POINT : AttractorType.METASTABLE;
            activeAttractor = new AttractorState(
                    sensoryState,
                    new float[]{1.0f},
                    type,
                    appraisal.urgencyAndStakes(),
                    1,
                    System.currentTimeMillis()
            );
        }

        // 2. Expected Free Energy Policy Inference (System 1 Active Policy Ranking)
        List<CognitivePolicy> candidatePolicies = generateCandidatePolicies(soul, activeAttractor, appraisal);
        PolicyDecisionReport policyReport = null;

        if (aismeBundle != null && aismeBundle.policyInferenceEngine() != null) {
            try {
                policyReport = aismeBundle.policyInferenceEngine().evaluate(candidatePolicies, List.of(soul));
            } catch (Exception e) {
                log.debug("Policy inference engine fallback: {}", e.getMessage());
            }
        }

        if (policyReport == null || policyReport.selectedPolicy() == null) {
            CognitivePolicy selected = !candidatePolicies.isEmpty() ? candidatePolicies.get(0) : defaultPolicy();
            List<PolicyDecisionReport.ScoredPolicy> scored = List.of(
                    new PolicyDecisionReport.ScoredPolicy(selected, 0.1f, 0.9f, 0.15f, 1.0f)
            );
            policyReport = new PolicyDecisionReport(selected, scored, 1.0f, 1000L, Instant.now());
        }

        // 3. Ancestral Guardrail Veto Evaluation (PEP Invariant I5)
        List<String> vetoes = new ArrayList<>();
        if (appraisal.normativeViolation()) {
            vetoes.add("Normative constraint violated: Action deviates from declared coreValues (" + appraisal.primaryConcern() + ")");
        }

        // 4. Determine Confidence Level (Evidenced vs Mixed vs Inferred)
        ConfidenceLevel confidence = determineConfidence(recallOutput, soul);

        // 5. Intended Acts (Action affordances)
        Set<String> intendedActs = new HashSet<>();
        if (policyReport.selectedPolicy() != null) {
            intendedActs.add("POLICY:" + policyReport.selectedPolicy().name());
        }
        if (activeAttractor != null) {
            intendedActs.add("STANCE:" + activeAttractor.type().name());
        }

        return new StanceOutput(
                activeAttractor,
                policyReport,
                confidence,
                intendedActs,
                vetoes
        );
    }

    private static List<CognitivePolicy> generateCandidatePolicies(
            AgentSoul soul,
            AttractorState attractor,
            CognitiveAppraisal appraisal) {

        List<CognitivePolicy> policies = new ArrayList<>();
        float[] mean = new float[]{appraisal.goalCongruence(), appraisal.urgencyAndStakes(), appraisal.copingPotential()};
        float[] precision = new float[]{1.0f, 1.0f, 1.0f};

        if (attractor.type() == AttractorType.FIXED_POINT || appraisal.urgencyAndStakes() > 0.7f) {
            policies.add(CognitivePolicy.of(PolicyType.PRAGMATIC_EXPLOITATION, mean, precision));
            policies.add(CognitivePolicy.of(PolicyType.CLARIFYING_INTERACTION, mean, precision));
        } else {
            policies.add(CognitivePolicy.of(PolicyType.EPISTEMIC_EXPLORATION, mean, precision));
            policies.add(CognitivePolicy.of(PolicyType.PROCEDURAL_CRYSTALLIZATION, mean, precision));
            policies.add(CognitivePolicy.of(PolicyType.PRAGMATIC_EXPLOITATION, mean, precision));
        }

        return policies;
    }

    private static CognitivePolicy defaultPolicy() {
        return CognitivePolicy.of(
                PolicyType.EPISTEMIC_EXPLORATION,
                new float[]{0.0f, 0.2f, 0.3f},
                new float[]{1.0f, 1.0f, 1.0f}
        );
    }

    private static ConfidenceLevel determineConfidence(PersonaRecall.RecallOutput recallOutput, AgentSoul soul) {
        if (recallOutput == null || recallOutput.results().isEmpty()) {
            return ConfidenceLevel.INFERRED; // Invariant I5: Thin Soul Honesty
        }

        int groundedCount = 0;
        int syntheticCount = 0;

        for (CognitiveResult cr : recallOutput.results()) {
            if (cr.source() != null && cr.source().toEngramSource() == com.spectrayan.spector.memory.model.EngramSource.SIMULATED) {
                syntheticCount++;
            } else {
                groundedCount++;
            }
        }

        if (groundedCount >= 3) {
            return ConfidenceLevel.EVIDENCED;
        } else if (groundedCount > 0 || syntheticCount > 0) {
            return ConfidenceLevel.MIXED;
        } else {
            return ConfidenceLevel.INFERRED;
        }
    }
}
