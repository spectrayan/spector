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
package com.spectrayan.spector.memory.pathway.recall.relay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.spectrayan.spector.memory.kernel.layout.SynapticHeaderConstants;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.ConflictMode;
import com.spectrayan.spector.memory.model.RecallOptions;

/**
 * Unit tests for {@link EvidenceFusionRelay}.
 */
class EvidenceFusionRelayTest {

    private EvidenceFusionRelay relay;

    @BeforeEach
    void setUp() {
        relay = new EvidenceFusionRelay();
    }

    @Test
    void transmit_multiEvidenceMode_preservesAllCandidates() {
        CognitiveResult cand1 = mock(CognitiveResult.class);
        when(cand1.id()).thenReturn("c-1");
        when(cand1.consolidationFlags()).thenReturn(SynapticHeaderConstants.FLAG_CONTRADICTED);

        CognitiveResult cand2 = mock(CognitiveResult.class);
        when(cand2.id()).thenReturn("c-2");
        when(cand2.consolidationFlags()).thenReturn((byte) 0);

        RecallOptions options = RecallOptions.builder().conflictMode(ConflictMode.MULTI_EVIDENCE).build();
        RecallSignal signal = RecallSignal.forTextQuery("query", options);
        signal.addCandidates(List.of(cand1, cand2));

        relay.transmit(signal);
        assertThat(signal.candidates()).containsExactly(cand1, cand2);
    }

    @Test
    void transmit_failClosedMode_dropsContradictedCandidates() {
        CognitiveResult cand1 = mock(CognitiveResult.class);
        when(cand1.id()).thenReturn("c-1");
        when(cand1.consolidationFlags()).thenReturn(SynapticHeaderConstants.FLAG_CONTRADICTED);

        CognitiveResult cand2 = mock(CognitiveResult.class);
        when(cand2.id()).thenReturn("c-2");
        when(cand2.consolidationFlags()).thenReturn((byte) 0);

        RecallOptions options = RecallOptions.builder().conflictMode(ConflictMode.FAIL_CLOSED).build();
        RecallSignal signal = RecallSignal.forTextQuery("query", options);
        signal.addCandidates(List.of(cand1, cand2));

        relay.transmit(signal);
        assertThat(signal.candidates()).containsExactly(cand2);
    }

    @Test
    void transmit_highestConfidenceMode_selectsHighestScoringDuplicate() {
        CognitiveResult winner = mock(CognitiveResult.class);
        when(winner.id()).thenReturn("win");
        when(winner.text()).thenReturn("Alice is Lead Engineer");
        when(winner.score()).thenReturn(0.95f);
        when(winner.consolidationFlags()).thenReturn(SynapticHeaderConstants.FLAG_CONTRADICTED);

        CognitiveResult loser = mock(CognitiveResult.class);
        when(loser.id()).thenReturn("lose");
        when(loser.text()).thenReturn("Alice is Lead Engineer");
        when(loser.score()).thenReturn(0.80f);
        when(loser.consolidationFlags()).thenReturn(SynapticHeaderConstants.FLAG_CONTRADICTED);

        RecallOptions options = RecallOptions.builder().conflictMode(ConflictMode.HIGHEST_CONFIDENCE).build();
        RecallSignal signal = RecallSignal.forTextQuery("query", options);
        signal.addCandidates(List.of(winner, loser));

        relay.transmit(signal);
        assertThat(signal.candidates()).containsExactly(winner);
    }
}
