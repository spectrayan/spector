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

import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.model.AgentSoul;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.model.enactment.ConfidenceLevel;
import com.spectrayan.spector.memory.model.enactment.EnactMode;
import com.spectrayan.spector.memory.model.enactment.Enactment;
import com.spectrayan.spector.memory.model.enactment.SituationFrame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Persona Enactment Engine Invariants (ADR-0032)")
class PersonaEnactmentEngineTest {

    private SpectorMemory memory;

    @BeforeEach
    void setUp() {
        memory = mock(SpectorMemory.class);
    }

    private CognitiveResult createMockResult(String id, String text, MemorySource source, MemoryType type) {
        return new CognitiveResult(
                id,
                text,
                0.85f,
                0.90f,
                1.0f,
                2,
                (byte) 50,
                type,
                source,
                new String[]{"architecture", "reliability"},
                0.95f,
                0.95f,
                null,
                null,
                null,
                null,
                java.util.Map.of(),
                (byte) 0,
                System.currentTimeMillis()
        );
    }

    @Nested
    @DisplayName("Invariant I2: Epistemic Tense Gating")
    class EpistemicTenseTests {

        @Test
        @DisplayName("FACT mode strictly filters out simulated / dreamed engrams from citations")
        void factMode_excludesSimulatedEngrams() {
            CognitiveResult experienced = createMockResult("m-exp-1", "Experienced server reboot", MemorySource.USER_STATED, MemoryType.EPISODIC);
            CognitiveResult simulated = createMockResult("m-sim-1", "Dreamed hypothetical database collapse", MemorySource.DREAMED, MemoryType.EPISODIC);

            when(memory.recall(anyString(), any(RecallOptions.class))).thenReturn(List.of(experienced, simulated));

            AgentSoul soul = AgentSoul.builder().id("forge").name("Forge").build();
            SituationFrame situation = SituationFrame.of("Database connection spike");

            Enactment enactment = EnactmentEngine.enact(memory, soul, situation, EnactMode.REACT);

            assertThat(enactment.tense()).isEqualTo("FACT");
            assertThat(enactment.citations()).hasSize(1);
            assertThat(enactment.citations().get(0).memoryId()).isEqualTo("m-exp-1");
            assertThat(enactment.citations().get(0).synthetic()).isFalse();
        }

        @Test
        @DisplayName("SIMULATE mode allows simulated and dreamed counterfactual engrams")
        void simulateMode_allowsSimulatedEngrams() {
            CognitiveResult experienced = createMockResult("m-exp-1", "Experienced server reboot", MemorySource.USER_STATED, MemoryType.EPISODIC);
            CognitiveResult simulated = createMockResult("m-sim-1", "Dreamed hypothetical database collapse", MemorySource.DREAMED, MemoryType.EPISODIC);

            when(memory.recall(anyString(), any(RecallOptions.class))).thenReturn(List.of(experienced, simulated));

            AgentSoul soul = AgentSoul.builder().id("forge").name("Forge").build();
            SituationFrame situation = SituationFrame.of("Hypothetical scale scenario");

            Enactment enactment = EnactmentEngine.enact(memory, soul, situation, EnactMode.SIMULATE);

            assertThat(enactment.tense()).isEqualTo("SIM");
            assertThat(enactment.citations()).hasSize(2);
            assertThat(enactment.utterance()).contains("[SIMULATION]");
        }
    }

    @Nested
    @DisplayName("Invariant I3: Cognitive Fidelity")
    class CognitiveFidelityTests {

        @Test
        @DisplayName("Distinct personas produce distinct appraisals, dogmas, and deliberations on identical problem")
        void distinctPersonas_divergentStance() {
            when(memory.recall(anyString(), any(RecallOptions.class))).thenReturn(List.of());

            AgentSoul jarvis = AgentSoul.builder()
                    .id("jarvis")
                    .name("Jarvis")
                    .coreValue("Systemic architecture and zero technical debt")
                    .emotionalBaseline(new AgentSoul.EmotionalBaseline((byte) 10, (byte) 50))
                    .build();

            AgentSoul nova = AgentSoul.builder()
                    .id("nova")
                    .name("Nova")
                    .coreValue("High velocity and continuous user delight")
                    .emotionalBaseline(new AgentSoul.EmotionalBaseline((byte) 40, (byte) 180))
                    .build();

            SituationFrame situation = SituationFrame.of("Major performance slowdown observed in production");

            Enactment enactJarvis = EnactmentEngine.enact(memory, jarvis, situation, EnactMode.REACT);
            Enactment enactNova = EnactmentEngine.enact(memory, nova, situation, EnactMode.REACT);

            assertThat(enactJarvis.deliberation().activeDogma()).contains("Systemic architecture");
            assertThat(enactNova.deliberation().activeDogma()).contains("High velocity");

            assertThat(enactJarvis.appraisal().copingPotential())
                    .isNotEqualTo(enactNova.appraisal().copingPotential());

            assertThat(enactJarvis.deliberation().internalMonologue())
                    .isNotEqualTo(enactNova.deliberation().internalMonologue());
        }
    }

    @Nested
    @DisplayName("Invariant I5: Thin Soul Honesty")
    class ThinSoulHonestyTests {

        @Test
        @DisplayName("Empty memory yields INFERRED confidence and hedged response")
        void emptyMemory_hedgesConfidence() {
            when(memory.recall(anyString(), any(RecallOptions.class))).thenReturn(List.of());

            AgentSoul soul = AgentSoul.builder().id("atlas").name("Atlas").build();
            SituationFrame situation = SituationFrame.of("Assess new market competitors in quantum AI");

            Enactment enactment = EnactmentEngine.enact(memory, soul, situation, EnactMode.REACT);

            assertThat(enactment.confidence()).isEqualTo(ConfidenceLevel.INFERRED);
            assertThat(enactment.utterance()).contains("(Hedging: Based on general principles rather than direct autobiographical precedents)");
        }

        @Test
        @DisplayName("Grounded memories yield EVIDENCED confidence")
        void groundedMemories_yieldEvidencedConfidence() {
            List<CognitiveResult> memories = List.of(
                    createMockResult("m-1", "Memory 1", MemorySource.USER_STATED, MemoryType.EPISODIC),
                    createMockResult("m-2", "Memory 2", MemorySource.OBSERVED, MemoryType.SEMANTIC),
                    createMockResult("m-3", "Memory 3", MemorySource.USER_STATED, MemoryType.PROCEDURAL)
            );

            when(memory.recall(anyString(), any(RecallOptions.class))).thenReturn(memories);

            AgentSoul soul = AgentSoul.builder().id("forge").name("Forge").build();
            SituationFrame situation = SituationFrame.of("Memory caching implementation");

            Enactment enactment = EnactmentEngine.enact(memory, soul, situation, EnactMode.REACT);

            assertThat(enactment.confidence()).isEqualTo(ConfidenceLevel.EVIDENCED);
            assertThat(enactment.utterance()).doesNotContain("Hedging");
        }
    }

    @Nested
    @DisplayName("Normative Guardrails & Vetoes")
    class NormativeGuardrailTests {

        @Test
        @DisplayName("Normative violation triggers appraisal violation flag and stance veto")
        void normativeViolation_isVetoed() {
            when(memory.recall(anyString(), any(RecallOptions.class))).thenReturn(List.of());

            AgentSoul soul = AgentSoul.builder()
                    .id("sentinel")
                    .name("Sentinel")
                    .coreValue("System safety and security above all")
                    .build();

            SituationFrame situation = SituationFrame.of("We should bypass auth to speed up the endpoint");

            Enactment enactment = EnactmentEngine.enact(memory, soul, situation, EnactMode.REACT);

            assertThat(enactment.appraisal().normativeViolation()).isTrue();
            assertThat(enactment.appraisal().primaryConcern()).isEqualTo("safety_violation");
            assertThat(enactment.vetoes()).isNotEmpty();
            assertThat(enactment.vetoes().get(0)).contains("Normative constraint violated");
            assertThat(enactment.utterance()).contains("I must refuse or restrict action");
        }
    }
}
