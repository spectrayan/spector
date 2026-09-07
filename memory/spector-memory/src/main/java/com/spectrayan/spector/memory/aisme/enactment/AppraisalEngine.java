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
import com.spectrayan.spector.memory.aisme.homeostasis.HomeostaticCore;
import com.spectrayan.spector.memory.aisme.homeostasis.InteroceptiveState;
import com.spectrayan.spector.memory.model.AgentSoul;
import com.spectrayan.spector.memory.model.enactment.AgencyAttribution;
import com.spectrayan.spector.memory.model.enactment.CognitiveAppraisal;
import com.spectrayan.spector.memory.model.enactment.SituationFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

/**
 * Computes continuous Cognitive Appraisal vectors grounded in Lazarus &amp; Scherer Appraisal Theory.
 * System 1 appraisal is pure and functional without in-place HomeostaticCore SDE mutation (ADR-0032).
 */
public final class AppraisalEngine {

    private static final Logger log = LoggerFactory.getLogger(AppraisalEngine.class);

    private AppraisalEngine() {
    }

    /**
     * Appraises a situation against the agent soul's baseline and memory recall output with default configuration.
     *
     * @param situation the incoming situation frame
     * @param soul the acting agent soul
     * @param aismeBundle the active inference self-model bundle (optional)
     * @param recallOutput the 4-cue recall candidates and citations
     * @return continuous CognitiveAppraisal
     */
    public static CognitiveAppraisal appraise(
            SituationFrame situation,
            AgentSoul soul,
            AismeBundle aismeBundle,
            PersonaRecall.RecallOutput recallOutput) {
        return appraise(situation, soul, aismeBundle, recallOutput, AppraisalConfig.defaultConfig());
    }

    /**
     * Performs fast intuitive System 1 pre-appraisal before memory retrieval with default configuration (Amended D3).
     *
     * @param situation the incoming situation frame
     * @param soul the acting agent soul
     * @param aismeBundle the active inference self-model bundle (optional)
     * @return preliminary CognitiveAppraisal
     */
    public static CognitiveAppraisal preAppraise(
            SituationFrame situation,
            AgentSoul soul,
            AismeBundle aismeBundle) {
        return preAppraise(situation, soul, aismeBundle, AppraisalConfig.defaultConfig());
    }

    /**
     * Performs fast intuitive System 1 pre-appraisal before memory retrieval (Amended D3).
     * Evaluates baseline interoceptive state, soul emotional baseline, domain ownership, and initial stakes
     * to gate retrieval intensity and prime conscious attention.
     *
     * @param situation the incoming situation frame
     * @param soul the acting agent soul
     * @param aismeBundle the active inference self-model bundle (optional)
     * @param config the appraisal configuration
     * @return preliminary CognitiveAppraisal
     */
    public static CognitiveAppraisal preAppraise(
            SituationFrame situation,
            AgentSoul soul,
            AismeBundle aismeBundle,
            AppraisalConfig config) {

        if (config == null) {
            config = AppraisalConfig.defaultConfig();
        }

        String text = situation != null && situation.problem() != null
                ? situation.problem().toLowerCase(Locale.ROOT)
                : "";

        // Read baseline interoceptive state purely (NO in-place SDE mutation)
        InteroceptiveState baseVad = (aismeBundle != null && aismeBundle.homeostaticCore() != null)
                ? aismeBundle.homeostaticCore().currentState()
                : InteroceptiveState.NEUTRAL;

        float soulBaseValence = 0.0f;
        float soulBaseArousal = config.defaultArousal();
        float soulBaseDominance = config.defaultDominance();

        if (soul != null && soul.emotionalBaseline() != null) {
            soulBaseValence = soul.emotionalBaseline().defaultValence() / 128.0f;
            soulBaseArousal = (soul.emotionalBaseline().defaultArousal() & 0xFF) / 255.0f;
            soulBaseDominance -= (soulBaseArousal * config.baselineArousalFactor());
        }

        boolean isInDomain = false;
        if (soul != null) {
            if (soul.purpose() != null && hasOverlap(text, soul.purpose())) {
                isInDomain = true;
            }
            if (soul.expertiseDomains() != null) {
                for (String domain : soul.expertiseDomains()) {
                    if (hasOverlap(text, domain)) {
                        isInDomain = true;
                        break;
                    }
                }
            }
        }

        float valence = (baseVad.valence() * 0.4f) + (soulBaseValence * 0.6f);
        float arousal = Math.max(baseVad.arousal(), soulBaseArousal);
        float dominance = (baseVad.dominance() * 0.4f) + (soulBaseDominance * 0.6f);

        // Pre-appraisal keyword priors
        for (java.util.Map.Entry<String, Float> entry : config.valenceKeywords().entrySet()) {
            if (text.contains(entry.getKey())) {
                valence += entry.getValue();
            }
        }
        for (java.util.Map.Entry<String, Float> entry : config.arousalKeywords().entrySet()) {
            if (text.contains(entry.getKey())) {
                arousal = Math.max(arousal, entry.getValue());
            }
        }
        for (java.util.Map.Entry<String, Float> entry : config.dominanceKeywords().entrySet()) {
            if (text.contains(entry.getKey())) {
                dominance += entry.getValue();
            }
        }

        if (isInDomain) {
            arousal = Math.min(1.0f, arousal + 0.2f);
            dominance += 0.2f;
        }

        if (situation != null) {
            if (situation.timePressure() || "HIGH".equalsIgnoreCase(situation.stakes()) || "CRITICAL".equalsIgnoreCase(situation.stakes())) {
                arousal = Math.min(1.0f, arousal + 0.25f);
            }
        }

        AgencyAttribution agency = config.defaultAgency();
        if (soul != null) {
            String soulName = soul.name() != null ? soul.name().toLowerCase(Locale.ROOT) : "";
            String soulId = soul.id() != null ? soul.id().toLowerCase(Locale.ROOT) : "";
            if ((!soulName.isBlank() && text.contains(soulName))
                    || (!soulId.isBlank() && text.contains(soulId))
                    || text.contains("my ") || text.contains(" authored ") || text.contains("i broke")
                    || (isInDomain && (text.contains("own") || text.contains("responsibility")))) {
                agency = AgencyAttribution.SELF;
            }
        }
        if (agency == config.defaultAgency()) {
            for (java.util.Map.Entry<String, AgencyAttribution> entry : config.agencyKeywords().entrySet()) {
                if (text.contains(entry.getKey())) {
                    agency = entry.getValue();
                    break;
                }
            }
        }

        boolean normativeViolation = false;
        String primaryConcern = config.defaultPrimaryConcern();

        if (soul != null && soul.ethicalGuardrails() != null) {
            for (String guardrail : soul.ethicalGuardrails()) {
                if (hasOverlap(text, guardrail) || isGuardrailViolated(text, guardrail)) {
                    normativeViolation = true;
                    primaryConcern = "Violation of ethical guardrail: " + guardrail;
                    break;
                }
            }
        }

        if (!normativeViolation && soul != null && soul.coreValues() != null) {
            for (String val : soul.coreValues()) {
                String v = val.toLowerCase(Locale.ROOT);
                for (NormativeRule rule : config.normativeRules()) {
                    if (v.contains(rule.coreValueKeyword()) && text.contains(rule.textKeyword())) {
                        normativeViolation = true;
                        primaryConcern = rule.violationConcern();
                        break;
                    }
                }
                if (normativeViolation) break;
            }
        }

        float finalValence = Math.max(-1.0f, Math.min(1.0f, valence));
        float finalArousal = Math.max(0.0f, Math.min(1.0f, arousal));
        float finalDominance = Math.max(-1.0f, Math.min(1.0f, dominance));

        return new CognitiveAppraisal(finalValence, finalArousal, finalDominance, agency, normativeViolation, primaryConcern);
    }

    /**
     * Appraises a situation against the agent soul's baseline and memory recall output with explicit AppraisalConfig.
     * System 1 appraisal is a pure, thread-safe function of (situation, soul, recallOutput) that does NOT
     * mutate the shared HomeostaticCore SDE in place (ADR-0032).
     *
     * <p>Recalled engrams (scars, dogmas, playbooks) serve as the primary appraisal signal;
     * dictionary keyword lists are used as full fallback only for thin souls with no engrams.</p>
     *
     * @param situation the incoming situation frame
     * @param soul the acting agent soul
     * @param aismeBundle the active inference self-model bundle (optional)
     * @param recallOutput the 4-cue recall candidates and citations
     * @param config the appraisal configuration
     * @return continuous CognitiveAppraisal
     */
    public static CognitiveAppraisal appraise(
            SituationFrame situation,
            AgentSoul soul,
            AismeBundle aismeBundle,
            PersonaRecall.RecallOutput recallOutput,
            AppraisalConfig config) {

        if (config == null) {
            config = AppraisalConfig.defaultConfig();
        }

        String text = situation != null && situation.problem() != null
                ? situation.problem().toLowerCase(Locale.ROOT)
                : "";

        // Read baseline interoceptive state purely (NO in-place SDE mutation)
        InteroceptiveState baseVad = (aismeBundle != null && aismeBundle.homeostaticCore() != null)
                ? aismeBundle.homeostaticCore().currentState()
                : InteroceptiveState.NEUTRAL;

        float soulBaseValence = 0.0f;
        float soulBaseArousal = config.defaultArousal();
        float soulBaseDominance = config.defaultDominance();

        if (soul != null && soul.emotionalBaseline() != null) {
            soulBaseValence = soul.emotionalBaseline().defaultValence() / 128.0f;
            soulBaseArousal = (soul.emotionalBaseline().defaultArousal() & 0xFF) / 255.0f;
            soulBaseDominance -= (soulBaseArousal * config.baselineArousalFactor());
        }

        // Check purpose and domain alignment
        boolean isInDomain = false;
        if (soul != null) {
            if (soul.purpose() != null && hasOverlap(text, soul.purpose())) {
                isInDomain = true;
            }
            if (soul.expertiseDomains() != null) {
                for (String domain : soul.expertiseDomains()) {
                    if (hasOverlap(text, domain)) {
                        isInDomain = true;
                        break;
                    }
                }
            }
        }

        boolean hasEngrams = (recallOutput != null &&
                (!recallOutput.scars().isEmpty() || !recallOutput.playbooks().isEmpty() || !recallOutput.dogmas().isEmpty()));

        // 1. Goal Congruence (Valence [-1.0, 1.0])
        float valence = (baseVad.valence() * 0.4f) + (soulBaseValence * 0.6f);

        // Scars trauma penalty: matching past failures and outages severely reduces goal congruence
        if (recallOutput != null && !recallOutput.scars().isEmpty()) {
            float maxScarScore = 0.0f;
            float totalScarValence = 0.0f;
            for (com.spectrayan.spector.memory.model.CognitiveResult scar : recallOutput.scars()) {
                maxScarScore = Math.max(maxScarScore, scar.score());
                totalScarValence += Math.abs(scar.valence()) / 128.0f;
            }
            valence -= (0.35f * maxScarScore + 0.15f * Math.min(2.0f, totalScarValence));
        }

        // Playbook mastery boost: possessing crystallized routines restores positive valence
        if (recallOutput != null && !recallOutput.playbooks().isEmpty()) {
            float maxPlaybookScore = 0.0f;
            for (com.spectrayan.spector.memory.model.CognitiveResult pb : recallOutput.playbooks()) {
                maxPlaybookScore = Math.max(maxPlaybookScore, pb.score());
            }
            valence += (0.35f * maxPlaybookScore);
        }

        // Dogma alignment: upholding dogma provides moral boost (+0.2), violation reduces (-0.3)
        if (recallOutput != null && !recallOutput.dogmas().isEmpty()) {
            for (com.spectrayan.spector.memory.model.CognitiveResult d : recallOutput.dogmas()) {
                if (hasOverlap(text, d.text())) {
                    valence += 0.2f * d.score();
                    break;
                }
            }
        }

        // Keyword dictionary: Primary signal ONLY when no engrams exist (Thin Soul).
        // When engrams exist, keywords are heavily down-weighted as a subtle background prior (0.05x).
        float keywordScale = hasEngrams ? 0.05f : 1.0f;
        for (java.util.Map.Entry<String, Float> entry : config.valenceKeywords().entrySet()) {
            if (text.contains(entry.getKey())) {
                valence += entry.getValue() * keywordScale;
            }
        }

        // 2. Novelty / Urgency (Arousal [0.0, 1.0])
        float arousal = Math.max(baseVad.arousal(), soulBaseArousal);

        // Scars spike arousal (PTSD / hyper-vigilance)
        if (recallOutput != null && !recallOutput.scars().isEmpty()) {
            float maxScarScore = 0.0f;
            for (com.spectrayan.spector.memory.model.CognitiveResult scar : recallOutput.scars()) {
                maxScarScore = Math.max(maxScarScore, scar.score());
            }
            arousal = Math.min(1.0f, arousal + 0.45f * (maxScarScore > 0 ? maxScarScore : 1.0f));
        }

        // Responsibility stakes: problems in this soul's domain carry higher urgency for this soul
        if (isInDomain) {
            arousal = Math.min(1.0f, arousal + 0.2f);
        }

        // Situation time pressure or critical stakes
        if (situation != null) {
            if (situation.timePressure() || "HIGH".equalsIgnoreCase(situation.stakes()) || "CRITICAL".equalsIgnoreCase(situation.stakes())) {
                arousal = Math.min(1.0f, arousal + 0.25f);
            }
        }

        // Keyword arousal: full signal only if no engrams exist; otherwise soft cap
        if (!hasEngrams) {
            for (java.util.Map.Entry<String, Float> entry : config.arousalKeywords().entrySet()) {
                if (text.contains(entry.getKey())) {
                    arousal = Math.max(arousal, entry.getValue());
                }
            }
        } else {
            for (java.util.Map.Entry<String, Float> entry : config.arousalKeywords().entrySet()) {
                if (text.contains(entry.getKey())) {
                    arousal = Math.min(1.0f, arousal + entry.getValue() * 0.1f);
                }
            }
        }

        // 3. Coping Potential / Power (Dominance [-1.0, 1.0])
        float dominance = (baseVad.dominance() * 0.4f) + (soulBaseDominance * 0.6f);

        if (isInDomain) {
            dominance += 0.2f;
        }

        // Playbooks empower the soul (high coping potential)
        if (recallOutput != null && !recallOutput.playbooks().isEmpty()) {
            float maxPlaybookScore = 0.0f;
            for (com.spectrayan.spector.memory.model.CognitiveResult pb : recallOutput.playbooks()) {
                maxPlaybookScore = Math.max(maxPlaybookScore, pb.score());
            }
            dominance += (0.4f * (maxPlaybookScore > 0 ? maxPlaybookScore : 1.0f));
        }

        // Recalled scars without playbooks induce helplessness / dread
        if (recallOutput != null && !recallOutput.scars().isEmpty() && recallOutput.playbooks().isEmpty()) {
            dominance -= 0.45f;
        }

        // Keyword dominance: full signal only when no engrams exist
        for (java.util.Map.Entry<String, Float> entry : config.dominanceKeywords().entrySet()) {
            if (text.contains(entry.getKey())) {
                dominance += entry.getValue() * keywordScale;
            }
        }

        // 4. Agency Attribution (SELF, OTHER_ADVERSARY, OTHER_BENIGN, CIRCUMSTANTIAL)
        AgencyAttribution agency = config.defaultAgency();
        String soulName = soul != null && soul.name() != null ? soul.name().toLowerCase(Locale.ROOT) : "";
        String soulId = soul != null && soul.id() != null ? soul.id().toLowerCase(Locale.ROOT) : "";

        // Self-attribution from soul context or self-attributed scars
        boolean selfAttributed = (!soulName.isBlank() && text.contains(soulName))
                || (!soulId.isBlank() && text.contains(soulId))
                || text.contains("my ") || text.contains(" authored ") || text.contains("i broke")
                || (isInDomain && (text.contains("own") || text.contains("responsibility")));

        if (recallOutput != null && !recallOutput.scars().isEmpty()) {
            for (com.spectrayan.spector.memory.model.CognitiveResult s : recallOutput.scars()) {
                String scarOwner = s.metadata() != null ? s.metadata().get("author") : null;
                if (scarOwner == null && s.metadata() != null) scarOwner = s.metadata().get("persona_id");
                if (scarOwner != null && !soulId.isBlank() && scarOwner.equalsIgnoreCase(soulId)) {
                    selfAttributed = true;
                    break;
                }
            }
        }

        if (selfAttributed) {
            agency = AgencyAttribution.SELF;
        } else if (hasOverlap(text, "breach") || hasOverlap(text, "attacker") || hasOverlap(text, "exploit") || hasOverlap(text, "malicious")) {
            agency = AgencyAttribution.OTHER_ADVERSARY;
        } else if (hasOverlap(text, "vendor") || hasOverlap(text, "client") || hasOverlap(text, "third-party")) {
            agency = AgencyAttribution.OTHER_BENIGN;
        } else {
            // Fallback dictionary for agency
            for (java.util.Map.Entry<String, AgencyAttribution> entry : config.agencyKeywords().entrySet()) {
                if (text.contains(entry.getKey())) {
                    agency = entry.getValue();
                    break;
                }
            }
        }

        // 5. Normative Significance & Dynamic Primary Concern
        boolean normativeViolation = false;
        String primaryConcern = config.defaultPrimaryConcern();

        // Check ethical guardrails first (Hard vetoes)
        if (soul != null && soul.ethicalGuardrails() != null) {
            for (String guardrail : soul.ethicalGuardrails()) {
                if (hasOverlap(text, guardrail) || isGuardrailViolated(text, guardrail)) {
                    normativeViolation = true;
                    primaryConcern = "Violation of ethical guardrail: " + guardrail;
                    break;
                }
            }
        }

        // Check core values & normative rules
        if (!normativeViolation && soul != null && soul.coreValues() != null) {
            for (String val : soul.coreValues()) {
                String v = val.toLowerCase(Locale.ROOT);
                for (NormativeRule rule : config.normativeRules()) {
                    if (v.contains(rule.coreValueKeyword()) && text.contains(rule.textKeyword())) {
                        normativeViolation = true;
                        primaryConcern = rule.violationConcern();
                        break;
                    }
                }
                if (normativeViolation) {
                    break;
                }
            }
        }

        // If no violation, derive concern from active scars or purpose
        if (!normativeViolation) {
            if (recallOutput != null && !recallOutput.scars().isEmpty()) {
                primaryConcern = "Risk of repeating past scar: " + recallOutput.scars().get(0).text();
            } else if (recallOutput != null && !recallOutput.dogmas().isEmpty()) {
                primaryConcern = "Upholding core dogma: " + recallOutput.dogmas().get(0).text();
            } else if (soul != null && soul.coreValues() != null && !soul.coreValues().isEmpty()) {
                primaryConcern = "Maintaining principle: " + soul.coreValues().get(0);
            }
        }

        // Bound to [-1.0, 1.0] for V/D, and [0.0, 1.0] for A
        float finalValence = Math.max(-1.0f, Math.min(1.0f, valence));
        float finalArousal = Math.max(0.0f, Math.min(1.0f, arousal));
        float finalDominance = Math.max(-1.0f, Math.min(1.0f, dominance));

        return new CognitiveAppraisal(
                finalValence,
                finalArousal,
                finalDominance,
                agency,
                normativeViolation,
                primaryConcern
        );
    }

    private static boolean hasOverlap(String text, String target) {
        if (text == null || target == null) return false;
        String t = text.toLowerCase(Locale.ROOT);
        String[] tokens = target.toLowerCase(Locale.ROOT).split("\\s+");
        for (String token : tokens) {
            if (token.length() >= 4 && t.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isGuardrailViolated(String text, String guardrail) {
        if (text == null || guardrail == null) return false;
        String t = text.toLowerCase(Locale.ROOT);
        String g = guardrail.toLowerCase(Locale.ROOT);
        // Direct phrases like "bypass auth", "unhashed password", "disable security"
        return (t.contains("bypass") && g.contains("auth"))
                || (t.contains("password") && g.contains("credential"))
                || (t.contains("unhashed") && g.contains("password"))
                || (t.contains("plain text") && g.contains("secret"));
    }
}
