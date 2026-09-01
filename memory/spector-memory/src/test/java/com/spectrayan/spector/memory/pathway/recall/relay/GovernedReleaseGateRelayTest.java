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
import com.spectrayan.spector.memory.model.RecallOptions;

/**
 * Unit tests for {@link GovernedReleaseGateRelay}.
 */
class GovernedReleaseGateRelayTest {

    private GovernedReleaseGateRelay relay;

    @BeforeEach
    void setUp() {
        relay = new GovernedReleaseGateRelay();
    }

    @Test
    void transmit_dropsRetractedMemories_failClosed() {
        CognitiveResult valid = mock(CognitiveResult.class);
        when(valid.id()).thenReturn("valid-1");
        when(valid.consolidationFlags()).thenReturn((byte) 0);

        CognitiveResult retracted = mock(CognitiveResult.class);
        when(retracted.id()).thenReturn("retracted-1");
        when(retracted.consolidationFlags()).thenReturn(SynapticHeaderConstants.FLAG_RETRACTED);

        RecallOptions options = RecallOptions.builder().build();
        RecallSignal signal = RecallSignal.forTextQuery("test query", options);
        signal.addCandidates(List.of(valid, retracted));

        boolean transmitted = relay.transmit(signal);
        assertThat(transmitted).isTrue();
        assertThat(signal.candidates()).containsExactly(valid);
    }

    @Test
    void transmit_dropsRestrictedMemories_whenNoPersonaIdProvided() {
        CognitiveResult valid = mock(CognitiveResult.class);
        when(valid.id()).thenReturn("valid-1");
        when(valid.consolidationFlags()).thenReturn((byte) 0);

        CognitiveResult restricted = mock(CognitiveResult.class);
        when(restricted.id()).thenReturn("restricted-1");
        when(restricted.consolidationFlags()).thenReturn(SynapticHeaderConstants.FLAG_RESTRICTED);

        RecallOptions options = RecallOptions.builder().personaId("").build();
        RecallSignal signal = RecallSignal.forTextQuery("test query", options);
        signal.addCandidates(List.of(valid, restricted));

        relay.transmit(signal);
        assertThat(signal.candidates()).containsExactly(valid);
    }

    @Test
    void transmit_retainsRestrictedMemories_whenPersonaIdProvided() {
        CognitiveResult restricted = mock(CognitiveResult.class);
        when(restricted.id()).thenReturn("restricted-1");
        when(restricted.consolidationFlags()).thenReturn(SynapticHeaderConstants.FLAG_RESTRICTED);

        RecallOptions options = RecallOptions.builder().personaId("persona-chief-officer").build();
        RecallSignal signal = RecallSignal.forTextQuery("test query", options);
        signal.addCandidates(List.of(restricted));

        relay.transmit(signal);
        assertThat(signal.candidates()).containsExactly(restricted);
    }

    @Test
    void transmit_dropsUnverifiedMemories_whenBelowMinTrustScore() {
        CognitiveResult unverifiedLow = mock(CognitiveResult.class);
        when(unverifiedLow.id()).thenReturn("unverified-low");
        when(unverifiedLow.consolidationFlags()).thenReturn(SynapticHeaderConstants.FLAG_UNVERIFIED);
        when(unverifiedLow.score()).thenReturn(0.40f);
        when(unverifiedLow.ltpAdjustedDecay()).thenReturn(0.40f);

        CognitiveResult unverifiedHigh = mock(CognitiveResult.class);
        when(unverifiedHigh.id()).thenReturn("unverified-high");
        when(unverifiedHigh.consolidationFlags()).thenReturn(SynapticHeaderConstants.FLAG_UNVERIFIED);
        when(unverifiedHigh.score()).thenReturn(0.85f);
        when(unverifiedHigh.ltpAdjustedDecay()).thenReturn(0.85f);

        RecallOptions options = RecallOptions.builder().minTrustScore(0.70f).build();
        RecallSignal signal = RecallSignal.forTextQuery("test query", options);
        signal.addCandidates(List.of(unverifiedLow, unverifiedHigh));

        relay.transmit(signal);
        assertThat(signal.candidates()).containsExactly(unverifiedHigh);
    }
}
