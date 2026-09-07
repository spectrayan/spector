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
package com.spectrayan.spector.memory.model.enactment;

import com.spectrayan.spector.memory.aisme.hopfield.AttractorState;
import com.spectrayan.spector.memory.aisme.policy.PolicyDecisionReport;
import com.spectrayan.spector.memory.model.CognitiveResult;

import java.util.List;
import java.util.Set;

/**
 * Complete, structured result of a Persona Enactment cycle (ADR-0032).
 */
public record Enactment(
        SituationFrame situation,
        CognitiveAppraisal appraisal,
        AttractorState activeAttractor,
        List<CognitiveResult> selfContext,
        PolicyDecisionReport policyReport,
        PersonaDeliberation deliberation,
        Set<String> intendedActs,
        String utterance,
        ConfidenceLevel confidence,
        String tense,
        List<EngramCitation> citations,
        List<String> vetoes
) {
    public Enactment {
        situation = (situation != null) ? situation : SituationFrame.of("");
        appraisal = (appraisal != null) ? appraisal : CognitiveAppraisal.neutral();
        selfContext = (selfContext != null) ? List.copyOf(selfContext) : List.of();
        policyReport = (policyReport != null) ? policyReport : PolicyDecisionReport.empty();
        deliberation = (deliberation != null) ? deliberation : PersonaDeliberation.empty();
        intendedActs = (intendedActs != null) ? Set.copyOf(intendedActs) : Set.of();
        utterance = (utterance != null) ? utterance : "";
        confidence = (confidence != null) ? confidence : ConfidenceLevel.INFERRED;
        tense = (tense != null) ? tense : "FACT";
        citations = (citations != null) ? List.copyOf(citations) : List.of();
        vetoes = (vetoes != null) ? List.copyOf(vetoes) : List.of();
    }
}
