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
package com.spectrayan.spector.memory.aisme.fegr;

import static org.assertj.core.api.Assertions.assertThat;

import com.spectrayan.spector.memory.model.AgentSoul;
import com.spectrayan.spector.memory.model.CognitiveProfile;
import com.spectrayan.spector.memory.model.PersonaContext;
import com.spectrayan.spector.memory.model.SoulContext;
import com.spectrayan.spector.memory.model.TenantSoul;
import com.spectrayan.spector.memory.model.UserSoul;

import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Unit tests for {@link GenerativeSelfModel}.
 */
class GenerativeSelfModelTest {

    @Test
    void fromSoulAndProfile_hyperfocus_highPrecision() {
        GenerativeSelfModel model = GenerativeSelfModel.fromSoulAndProfile((SoulContext) null, CognitiveProfile.HYPERFOCUS, 4);
        assertThat(model.dimensions()).isEqualTo(4);
        assertThat(model.priorPrecision()[0]).isEqualTo(8.0f);
        assertThat(model.observationPrecision()[0]).isEqualTo(4.0f);
    }

    @Test
    void fromSoulAndProfile_divergent_flexiblePrecision() {
        GenerativeSelfModel model = GenerativeSelfModel.fromSoulAndProfile((SoulContext) null, CognitiveProfile.DIVERGENT, 4);
        assertThat(model.priorPrecision()[0]).isEqualTo(1.0f);
    }

    @Test
    void fromSoulAndProfile_withAgentSoulEmbedding_initializesPriorMean() {
        float[] idEmb = {0.1f, 0.2f, 0.3f, 0.4f};
        AgentSoul soul = AgentSoul.builder()
                .id("test-soul")
                .name("Persona")
                .purposeEmbedding(idEmb)
                .build();

        GenerativeSelfModel model = GenerativeSelfModel.fromSoulAndProfile(soul, CognitiveProfile.BALANCED, 4);
        assertThat(model.priorMean()).containsExactly(0.1f, 0.2f, 0.3f, 0.4f);
        assertThat(model.priorPrecision()[0]).isEqualTo(3.0f);
        assertThat(model.agentSoul()).isEqualTo(soul);
    }

    @Test
    void fromSoulAndProfile_withUserSoul_initializesPriorMeanFromPersona() {
        float[] userEmb = {0.5f, 0.6f, 0.7f, 0.8f};
        PersonaContext persona = PersonaContext.builder()
                .aboutEmbedding(userEmb)
                .build();
        UserSoul userSoul = new UserSoul("user-1", "Bharat", "CEO", persona, null);

        GenerativeSelfModel model = GenerativeSelfModel.fromSoulAndProfile(userSoul, CognitiveProfile.BALANCED, 4);
        assertThat(model.priorMean()).containsExactly(0.5f, 0.6f, 0.7f, 0.8f);
        assertThat(model.soul()).isEqualTo(userSoul);
        assertThat(model.agentSoul()).isNull();
    }

    @Test
    void fromSoulsAndProfile_multiSoulBlending() {
        float[] emb1 = {1.0f, 0.0f};
        float[] emb2 = {0.0f, 1.0f};

        AgentSoul soul1 = AgentSoul.builder().id("agent-1").purposeEmbedding(emb1).build();
        TenantSoul soul2 = new TenantSoul("tenant-1", "Corp", "Compliance", null, null, emb2, (short) 1, null, null);

        GenerativeSelfModel model = GenerativeSelfModel.fromSoulsAndProfile(List.of(soul1, soul2), CognitiveProfile.BALANCED, 2);
        // Blended mean should be average of [1, 0] and [0, 1] = [0.5, 0.5]
        assertThat(model.priorMean()[0]).isEqualTo(0.5f);
        assertThat(model.priorMean()[1]).isEqualTo(0.5f);
    }

    @Test
    void createInitialPosterior_matchesPriorParameters() {
        GenerativeSelfModel model = GenerativeSelfModel.fromSoulAndProfile((SoulContext) null, CognitiveProfile.BALANCED, 3);
        MentalStatePosterior initial = model.createInitialPosterior(12345L);

        assertThat(initial.dimensions()).isEqualTo(3);
        assertThat(initial.timestampMs()).isEqualTo(12345L);
        assertThat(initial.version()).isEqualTo(0);
        assertThat(initial.mean()).containsExactly(model.priorMean());
        assertThat(initial.precision()).containsExactly(model.priorPrecision());
    }
}
