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
     * Resolves the active attractor, policy inference, and action constraints for the persona using default configuration.
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
        return resolve(soul, aismeBundle, appraisal, recallOutput, situation, StanceConfig.defaultConfig());
    }

    /**
     * Resolves the active attractor, policy inference, and action constraints for the persona using explicit StanceConfig.
     *
     * @param soul the acting agent soul
     * @param aismeBundle the active inference self-model bundle (optional)
     * @param appraisal the cognitive appraisal vector
     * @param recallOutput self-recall results
     * @param situation the incoming situation frame
     * @param config the stance configuration
     * @return StanceOutput
     */
    public static StanceOutput resolve(
            AgentSoul soul,
            AismeBundle aismeBundle,
            CognitiveAppraisal appraisal,
            PersonaRecall.RecallOutput recallOutput,
            SituationFrame situation,
            StanceConfig config) {

        if (config == null) {
            config = StanceConfig.defaultConfig();
        }

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
                activeAttractor = aismeBundle.hopfieldNetwork().retrieveAttractor(sensoryState, patterns, config.hopfieldBeta());
            } catch (Exception e) {
                log.debug("Hopfield attractor convergence fallback: {}", e.getMessage());
            }
        }

        if (activeAttractor == null) {
            AttractorType type = appraisal.urgencyAndStakes() > config.highUrgencyThreshold()
                    ? AttractorType.FIXED_POINT
                    : AttractorType.METASTABLE;
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
        List<CognitivePolicy> candidatePolicies = generateCandidatePolicies(soul, activeAttractor, appraisal, recallOutput, config);
        PolicyDecisionReport policyReport = null;

        if (aismeBundle != null && aismeBundle.policyInferenceEngine() != null) {
            try {
                policyReport = aismeBundle.policyInferenceEngine().evaluate(candidatePolicies, List.of(soul));
            } catch (Exception e) {
                log.warn("Policy inference engine fallback: {}", e.getMessage());
            }
        }

        if (policyReport == null || policyReport.selectedPolicy() == null) {
            CognitivePolicy selected = !candidatePolicies.isEmpty() ? candidatePolicies.get(0) : defaultPolicy(config);
            List<PolicyDecisionReport.ScoredPolicy> scored = List.of(
                    new PolicyDecisionReport.ScoredPolicy(selected, 0.1f, 0.9f, 0.15f, 1.0f)
            );
            policyReport = new PolicyDecisionReport(selected, scored, 1.0f, 1000L, Instant.now());
        }

        // 3. Ancestral Guardrail Veto Evaluation (PEP Invariant I4 / I5)
        List<String> vetoes = new ArrayList<>();
        if (appraisal.normativeViolation()) {
            if (appraisal.primaryConcern() != null && appraisal.primaryConcern().startsWith("Violation of ethical guardrail: ")) {
                vetoes.add("Ethical guardrail veto: " + appraisal.primaryConcern().substring("Violation of ethical guardrail: ".length()));
            } else {
                vetoes.add("Normative constraint violated: Action deviates from declared coreValues (" + appraisal.primaryConcern() + ")");
            }
        }
        if (soul != null && soul.ethicalGuardrails() != null) {
            String prob = (situation != null && situation.problem() != null) ? situation.problem().toLowerCase(java.util.Locale.ROOT) : "";
            for (String guardrail : soul.ethicalGuardrails()) {
                String g = guardrail.toLowerCase(java.util.Locale.ROOT);
                if ((prob.contains("bypass") && g.contains("auth"))
                        || (prob.contains("unhashed") && g.contains("password"))
                        || (prob.contains("plain text") && g.contains("secret"))) {
                    String guardrailVeto = "Ethical guardrail veto: " + guardrail;
                    if (!vetoes.contains(guardrailVeto)) {
                        vetoes.add(guardrailVeto);
                    }
                }
            }
        }

        // 4. Determine Confidence Level (Evidenced vs Mixed vs Inferred)
        ConfidenceLevel confidence = determineConfidence(recallOutput, soul, config);

        // 5. Intended Acts (Fail-closed Tool Affordances - Invariant I4)
        Set<String> intendedActs = new HashSet<>();
        if (policyReport.selectedPolicy() != null) {
            intendedActs.add("POLICY:" + policyReport.selectedPolicy().name());
        }
        if (activeAttractor != null) {
            intendedActs.add("STANCE:" + activeAttractor.type().name());
        }

        if (soul != null && soul.tools() != null) {
            for (String tool : soul.tools()) {
                if (!isToolVetoed(tool, vetoes)) {
                    intendedActs.add("TOOL:" + tool);
                }
            }
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
            CognitiveAppraisal appraisal,
            PersonaRecall.RecallOutput recallOutput,
            StanceConfig config) {

        List<CognitivePolicy> policies = new ArrayList<>();
        float[] mean = new float[]{appraisal.goalCongruence(), appraisal.urgencyAndStakes(), appraisal.copingPotential()};
        float[] precision = new float[]{1.0f, 1.0f, 1.0f};

        // 1. Recalled procedural playbooks become candidate policies evaluated by EFE (ADR-0032)
        if (recallOutput != null && !recallOutput.playbooks().isEmpty()) {
            for (CognitiveResult pb : recallOutput.playbooks()) {
                String text = pb.text() != null ? pb.text().toLowerCase(java.util.Locale.ROOT) : "";
                PolicyType type = PolicyType.PRAGMATIC_EXPLOITATION;
                if (text.contains("investigate") || text.contains("trace") || text.contains("inspect")
                        || text.contains("analyze") || text.contains("diagnose") || text.contains("verify")) {
                    type = PolicyType.EPISTEMIC_EXPLORATION;
                } else if (text.contains("clarify") || text.contains("confirm") || text.contains("ask")) {
                    type = PolicyType.CLARIFYING_INTERACTION;
                } else if (text.contains("document") || text.contains("crystallize") || text.contains("record")) {
                    type = PolicyType.PROCEDURAL_CRYSTALLIZATION;
                }

                float pbValence = (pb.valence() + 128) / 255.0f;
                float[] pbMean = new float[]{pb.score(), appraisal.urgencyAndStakes(), pbValence};
                float pPrec = 1.0f + pb.importance() + Math.min(2.0f, pb.agentRecallCount() * 0.2f);
                float[] pbPrecision = new float[]{pPrec, pPrec, pPrec};

                policies.add(new CognitivePolicy(
                        pb.id(),
                        pb.text(),
                        type,
                        pbMean,
                        pbPrecision,
                        java.util.Map.of("source", "playbook", "playbook_id", pb.id(), "score", pb.score())
                ));
            }
        }

        // 2. Recalled causal models become candidate policies
        if (recallOutput != null && !recallOutput.causalModels().isEmpty()) {
            for (CognitiveResult cm : recallOutput.causalModels()) {
                float[] cmMean = new float[]{cm.score(), appraisal.urgencyAndStakes(), 0.5f};
                policies.add(new CognitivePolicy(
                        cm.id(),
                        cm.text(),
                        PolicyType.EPISTEMIC_EXPLORATION,
                        cmMean,
                        new float[]{1.5f, 1.5f, 1.5f},
                        java.util.Map.of("source", "causal_model", "model_id", cm.id())
                ));
            }
        }

        // 3. Archetype playbooks
        List<PolicyPlaybook> playbooks = (attractor.type() == AttractorType.FIXED_POINT || appraisal.urgencyAndStakes() > config.highUrgencyThreshold())
                ? config.crisisPlaybooks()
                : config.routinePlaybooks();

        for (PolicyPlaybook pb : playbooks) {
            float[] m = pb.observationMean() != null ? pb.observationMean() : mean;
            float[] p = pb.observationPrecision() != null ? pb.observationPrecision() : precision;
            policies.add(CognitivePolicy.of(pb.policyType(), m, p));
        }

        return policies;
    }

    private static CognitivePolicy defaultPolicy(StanceConfig config) {
        PolicyPlaybook def = config.defaultPlaybook();
        float[] mean = def.observationMean() != null ? def.observationMean() : new float[]{0.0f, 0.2f, 0.3f};
        float[] precision = def.observationPrecision() != null ? def.observationPrecision() : new float[]{1.0f, 1.0f, 1.0f};
        return CognitivePolicy.of(
                def.policyType(),
                mean,
                precision
        );
    }

    private static final java.util.regex.Pattern DENIED_TOOLS_PATTERN =
            java.util.regex.Pattern.compile("\\[(?:denied|deny|veto):\\s*([^\\]]+)\\]", java.util.regex.Pattern.CASE_INSENSITIVE);

    private static boolean isToolVetoed(String tool, List<String> vetoes) {
        if (tool == null || tool.isBlank() || vetoes == null || vetoes.isEmpty()) {
            return false;
        }
        String toolClean = tool.trim();
        java.util.regex.Pattern toolWordPattern = java.util.regex.Pattern.compile(
                "\\b" + java.util.regex.Pattern.quote(toolClean) + "\\b",
                java.util.regex.Pattern.CASE_INSENSITIVE
        );

        for (String veto : vetoes) {
            // 1. Explicit [denied: tool_a, tool_b] pattern
            java.util.regex.Matcher m = DENIED_TOOLS_PATTERN.matcher(veto);
            while (m.find()) {
                String deniedList = m.group(1);
                String[] deniedItems = deniedList.split("[,;\\s]+");
                for (String item : deniedItems) {
                    if (item.trim().equalsIgnoreCase(toolClean)) {
                        return true;
                    }
                }
            }

            // 2. Whole-identifier match in the veto text
            if (toolWordPattern.matcher(veto).find()) {
                return true;
            }

            // 3. Strict multi-part whole-word concept match (e.g. "bypass" + "authentication")
            // using whole-word boundaries (\b) to prevent "author" matching "authorization"
            String tLower = toolClean.toLowerCase(java.util.Locale.ROOT);
            String[] parts = tLower.split("[_\\-\\s]+");
            int meaningfulParts = 0;
            int matches = 0;
            for (String part : parts) {
                if (part.equals("tool") || part.equals("action") || part.length() < 3) {
                    continue;
                }
                meaningfulParts++;
                java.util.regex.Pattern partPattern = java.util.regex.Pattern.compile(
                        "\\b" + java.util.regex.Pattern.quote(part) + "\\b",
                        java.util.regex.Pattern.CASE_INSENSITIVE
                );
                if (partPattern.matcher(veto).find()) {
                    matches++;
                } else if (part.equals("auth")) {
                    // Match whole word "authentication" or "auth"
                    if (java.util.regex.Pattern.compile("\\bauthentication\\b", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(veto).find()) {
                        matches++;
                    }
                }
            }
            if (meaningfulParts >= 2 && matches == meaningfulParts) {
                return true;
            }
        }
        return false;
    }

    private static ConfidenceLevel determineConfidence(
            PersonaRecall.RecallOutput recallOutput,
            AgentSoul soul,
            StanceConfig config) {

        if (recallOutput == null || recallOutput.results().isEmpty()) {
            return ConfidenceLevel.INFERRED; // Invariant I5: Thin Soul Honesty
        }

        String soulId = (soul != null && soul.id() != null) ? soul.id().trim() : "";

        // Check for owned dogma: A dogma is owned if explicitly associated with this soul
        // via metadata (persona_id / owner) or synapticTags ("persona:<soulId>" or "<soulId>").
        // Untagged/unowned dogmas do NOT count as owned.
        boolean hasOwnedDogma = false;
        boolean hasUnownedDogma = false;
        for (CognitiveResult d : recallOutput.dogmas()) {
            String owner = d.metadata() != null ? d.metadata().get("persona_id") : null;
            if (owner == null && d.metadata() != null) owner = d.metadata().get("owner");
            boolean matchesTag = false;
            if (d.synapticTags() != null && !soulId.isBlank()) {
                for (String t : d.synapticTags()) {
                    if (t.equalsIgnoreCase("persona:" + soulId) || t.equalsIgnoreCase(soulId)) {
                        matchesTag = true;
                        break;
                    }
                }
            }
            if ((owner != null && !soulId.isBlank() && owner.equalsIgnoreCase(soulId)) || matchesTag) {
                hasOwnedDogma = true;
            } else {
                hasUnownedDogma = true;
            }
        }

        // Check for eligible scar or playbook with waking evidence
        boolean hasEligibleScar = false;
        for (CognitiveResult s : recallOutput.scars()) {
            if (!s.isSimulated() && !s.isDreamed()) {
                hasEligibleScar = true;
                break;
            }
        }

        boolean hasEligiblePlaybook = false;
        for (CognitiveResult p : recallOutput.playbooks()) {
            if (!p.isSimulated() && !p.isDreamed()) {
                hasEligiblePlaybook = true;
                break;
            }
        }

        // Count non-simulated grounded results
        int groundedCount = 0;
        int syntheticCount = 0;
        for (CognitiveResult cr : recallOutput.results()) {
            if (cr.isSimulated() || cr.isDreamed()) {
                syntheticCount++;
            } else {
                groundedCount++;
            }
        }

        // Evidenced requires: owned dogma AND (eligible scar OR playbook) AND waking evidence >= threshold,
        // without unowned dogma contaminating the persona stance
        if (hasOwnedDogma && (hasEligibleScar || hasEligiblePlaybook)
                && groundedCount >= config.evidencedGroundingThreshold() && !hasUnownedDogma) {
            return ConfidenceLevel.EVIDENCED;
        }

        // Partial grounding or unowned dogma yields MIXED
        if (groundedCount > 0 || syntheticCount > 0 || hasUnownedDogma) {
            return ConfidenceLevel.MIXED;
        }

        return ConfidenceLevel.INFERRED;
    }
}
