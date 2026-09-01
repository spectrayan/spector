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
package com.spectrayan.spector.memory.aisme;

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

import static org.assertj.core.api.Assertions.assertThat;

import com.spectrayan.spector.memory.aisme.config.AismeConfig;
import com.spectrayan.spector.memory.model.AgentSoul;
import com.spectrayan.spector.memory.model.PersonaContext;
import com.spectrayan.spector.memory.model.SoulContext;
import com.spectrayan.spector.memory.model.TenantSoul;
import com.spectrayan.spector.memory.model.UserSoul;

import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Unit tests for {@link AismeBuilder} verifying polymorphic SoulContext and multi-soul wiring.
 */
class AismeBuilderTest {

    @Test
    void build_disabledConfig_returnsNull() {
        AismeBundle bundle = AismeBuilder.build(AismeConfig.disabled(), (SoulContext) null, 4, id -> null);
        assertThat(bundle).isNull();
    }

    @Test
    void build_withAgentSoul_createsBundle() {
        AgentSoul soul = AgentSoul.builder().id("agent-1").name("Jarvis").build();
        AismeConfig config = AismeConfig.defaultConfig();

        AismeBundle bundle = AismeBuilder.build(config, soul, 4, id -> null);
        assertThat(bundle).isNotNull();
        assertThat(bundle.primarySoul()).isEqualTo(soul);
        assertThat(bundle.agentSoul()).isEqualTo(soul);
        assertThat(bundle.soulContexts()).containsExactly(soul);
        assertThat(bundle.generativeSelfModel().soul()).isEqualTo(soul);
    }

    @Test
    void build_withUserSoul_createsBundle() {
        PersonaContext persona = PersonaContext.builder().aboutEmbedding(new float[]{0.1f, 0.2f, 0.3f, 0.4f}).build();
        UserSoul userSoul = new UserSoul("user-1", "Bharat", "CEO", persona, null);
        AismeConfig config = AismeConfig.defaultConfig();

        AismeBundle bundle = AismeBuilder.build(config, userSoul, 4, id -> null);
        assertThat(bundle).isNotNull();
        assertThat(bundle.primarySoul()).isEqualTo(userSoul);
        assertThat(bundle.agentSoul()).isNull(); // Not an AgentSoul
        assertThat(bundle.soulContexts()).containsExactly(userSoul);
        assertThat(bundle.generativeSelfModel().priorMean()).containsExactly(0.1f, 0.2f, 0.3f, 0.4f);
    }

    @Test
    void build_withMultiSoulContext_createsCompositePrior() {
        float[] emb1 = {1.0f, 0.0f};
        float[] emb2 = {0.0f, 1.0f};
        AgentSoul soul1 = AgentSoul.builder().id("agent-1").purposeEmbedding(emb1).build();
        TenantSoul soul2 = new TenantSoul("tenant-1", "Enterprise", "Compliance", null, null, emb2, (short) 1, null, null);

        AismeConfig config = AismeConfig.defaultConfig();
        AismeBundle bundle = AismeBuilder.build(config, soul1, 2, null, id -> null, List.of(soul1, soul2));

        assertThat(bundle).isNotNull();
        assertThat(bundle.soulContexts()).containsExactly(soul1, soul2);
        assertThat(bundle.generativeSelfModel().priorMean()[0]).isEqualTo(0.5f);
        assertThat(bundle.generativeSelfModel().priorMean()[1]).isEqualTo(0.5f);
    }
}
