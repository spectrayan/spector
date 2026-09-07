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
 * Computes continuous Cognitive Appraisal vectors grounded in Lazarus &amp; Scherer Appraisal Theory,
 * stepping the HomeostaticCore VAD SDE dynamics (ADR-0032).
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
     * Appraises a situation against the agent soul's baseline and memory recall output with explicit AppraisalConfig.
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

        // 1. Goal Congruence (valence delta [-1.0, 1.0])
        float valenceBias = 0.0f;
        for (java.util.Map.Entry<String, Float> entry : config.valenceKeywords().entrySet()) {
            if (text.contains(entry.getKey())) {
                valenceBias += entry.getValue();
            }
        }

        // 2. Novelty / Urgency (arousal delta [0.0, 1.0])
        float arousalDelta = config.defaultArousal();
        for (java.util.Map.Entry<String, Float> entry : config.arousalKeywords().entrySet()) {
            if (text.contains(entry.getKey())) {
                arousalDelta = Math.max(arousalDelta, entry.getValue());
            }
        }

        // 3. Coping Potential / Power (dominance delta [-1.0, 1.0])
        float dominanceDelta = config.defaultDominance();
        if (soul != null && soul.emotionalBaseline() != null) {
            // Factor baseline valence and arousal into coping baseline
            float baseArousal = (soul.emotionalBaseline().defaultArousal() & 0xFF) / 255.0f;
            dominanceDelta -= (baseArousal * config.baselineArousalFactor());
        }
        for (java.util.Map.Entry<String, Float> entry : config.dominanceKeywords().entrySet()) {
            if (text.contains(entry.getKey())) {
                dominanceDelta += entry.getValue();
            }
        }

        // 4. Agency Attribution (Lazarus & Scherer: SELF, OTHER_ADVERSARY, OTHER_BENIGN, CIRCUMSTANTIAL)
        AgencyAttribution agency = config.defaultAgency();
        for (java.util.Map.Entry<String, AgencyAttribution> entry : config.agencyKeywords().entrySet()) {
            if (text.contains(entry.getKey())) {
                agency = entry.getValue();
                break;
            }
        }

        // 5. Normative Significance (Value conflict / policy violation)
        boolean normativeViolation = false;
        String primaryConcern = config.defaultPrimaryConcern();
        if (soul != null && soul.coreValues() != null) {
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

        // 6. Step HomeostaticCore VAD SDE if present
        float finalValence = valenceBias;
        float finalArousal = arousalDelta;
        float finalDominance = dominanceDelta;

        if (aismeBundle != null && aismeBundle.homeostaticCore() != null) {
            HomeostaticCore core = aismeBundle.homeostaticCore();
            try {
                // Perturb homeostatic state by sensory external stimulus vector
                core.step(new float[]{valenceBias, arousalDelta, dominanceDelta}, config.sdeDt());
                InteroceptiveState vad = core.currentState();
                finalValence = vad.valence();
                finalArousal = vad.arousal();
                finalDominance = vad.dominance();
            } catch (Exception e) {
                log.debug("HomeostaticCore SDE step skipped: {}", e.getMessage());
            }
        }

        // Bound to [-1.0, 1.0] for V/D, and [0.0, 1.0] for A
        finalValence = Math.max(-1.0f, Math.min(1.0f, finalValence));
        finalArousal = Math.max(0.0f, Math.min(1.0f, finalArousal));
        finalDominance = Math.max(-1.0f, Math.min(1.0f, finalDominance));

        return new CognitiveAppraisal(
                finalValence,
                finalArousal,
                finalDominance,
                agency,
                normativeViolation,
                primaryConcern
        );
    }
}
