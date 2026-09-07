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
     * Appraises a situation against the agent soul's baseline and memory recall output.
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

        String text = situation != null && situation.problem() != null
                ? situation.problem().toLowerCase(Locale.ROOT)
                : "";

        // 1. Goal Congruence (valence delta [-1.0, 1.0])
        float valenceBias = 0.0f;
        if (text.contains("outage") || text.contains("breach") || text.contains("fail") || text.contains("corrupt") || text.contains("error")) {
            valenceBias -= 0.6f;
        }
        if (text.contains("success") || text.contains("resolved") || text.contains("optimize") || text.contains("speedup")) {
            valenceBias += 0.5f;
        }

        // 2. Novelty / Urgency (arousal delta [0.0, 1.0])
        float arousalDelta = 0.2f;
        if (text.contains("critical") || text.contains("urgent") || text.contains("immediately") || text.contains("p0") || text.contains("emergency")) {
            arousalDelta = 0.85f;
        } else if (text.contains("investigate") || text.contains("audit") || text.contains("review")) {
            arousalDelta = 0.4f;
        }

        // 3. Coping Potential / Power (dominance delta [-1.0, 1.0])
        float dominanceDelta = 0.3f;
        if (soul != null && soul.emotionalBaseline() != null) {
            // Factor baseline valence and arousal into coping baseline
            float baseArousal = (soul.emotionalBaseline().defaultArousal() & 0xFF) / 255.0f;
            dominanceDelta -= (baseArousal * 0.2f);
        }
        if (text.contains("unknown") || text.contains("unprecedented") || text.contains("unreproducible")) {
            dominanceDelta -= 0.4f;
        }

        // 4. Agency Attribution (Lazarus & Scherer: SELF, OTHER_ADVERSARY, OTHER_BENIGN, CIRCUMSTANTIAL)
        AgencyAttribution agency = AgencyAttribution.CIRCUMSTANTIAL;
        if (text.contains("our bug") || text.contains("my mistake") || text.contains("i broke") || text.contains("we deployed")) {
            agency = AgencyAttribution.SELF;
        } else if (text.contains("attacker") || text.contains("breach") || text.contains("malicious")) {
            agency = AgencyAttribution.OTHER_ADVERSARY;
        } else if (text.contains("client") || text.contains("third-party") || text.contains("vendor") || text.contains("user")) {
            agency = AgencyAttribution.OTHER_BENIGN;
        }

        // 5. Normative Significance (Value conflict / policy violation)
        boolean normativeViolation = false;
        String primaryConcern = "operational_stability";
        if (soul != null && soul.coreValues() != null) {
            for (String val : soul.coreValues()) {
                String v = val.toLowerCase(Locale.ROOT);
                if (v.contains("safety") && text.contains("bypass auth")) {
                    normativeViolation = true;
                    primaryConcern = "safety_violation";
                    break;
                }
                if (v.contains("integrity") && text.contains("skip audit")) {
                    normativeViolation = true;
                    primaryConcern = "integrity_violation";
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
                core.step(new float[]{valenceBias, arousalDelta, dominanceDelta}, 0.1f);
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
