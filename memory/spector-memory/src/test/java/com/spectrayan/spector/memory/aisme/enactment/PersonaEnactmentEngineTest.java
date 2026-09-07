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
import com.spectrayan.spector.memory.aisme.policy.PolicyType;
import com.spectrayan.spector.memory.model.enactment.AgencyAttribution;
import com.spectrayan.spector.memory.model.enactment.ConfidenceLevel;
import com.spectrayan.spector.memory.model.enactment.EnactMode;
import com.spectrayan.spector.memory.model.enactment.Enactment;
import com.spectrayan.spector.memory.model.enactment.SituationFrame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

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
    @DisplayName("Invariant I1: Disjoint Namespace Isolation")
    class DisjointNamespaceTests {

        @Test
        @DisplayName("Two stores with opposite traces produce completely disjoint citations")
        void twoStores_oppositeTraces_disjointCitations() {
            SpectorMemory memoryA = mock(SpectorMemory.class);
            SpectorMemory memoryB = mock(SpectorMemory.class);

            CognitiveResult memA = createMockResult("trace-alpha-001", "Database replication configuration", MemorySource.USER_STATED, MemoryType.SEMANTIC);
            CognitiveResult memB = createMockResult("trace-beta-002", "Cache eviction policies", MemorySource.USER_STATED, MemoryType.SEMANTIC);

            when(memoryA.recall(anyString(), any(RecallOptions.class))).thenReturn(List.of(memA));
            when(memoryB.recall(anyString(), any(RecallOptions.class))).thenReturn(List.of(memB));

            AgentSoul soul = AgentSoul.builder().id("forge").name("Forge").build();
            SituationFrame situation = SituationFrame.of("Retrieve system architecture guidelines");

            Enactment enactmentA = EnactmentEngine.enact(memoryA, soul, situation, EnactMode.REACT);
            Enactment enactmentB = EnactmentEngine.enact(memoryB, soul, situation, EnactMode.REACT);

            List<String> citationsA = enactmentA.citations().stream().map(com.spectrayan.spector.memory.model.enactment.EngramCitation::memoryId).toList();
            List<String> citationsB = enactmentB.citations().stream().map(com.spectrayan.spector.memory.model.enactment.EngramCitation::memoryId).toList();

            assertThat(citationsA).containsExactly("trace-alpha-001");
            assertThat(citationsB).containsExactly("trace-beta-002");
            assertThat(citationsA).doesNotContainAnyElementsOf(citationsB);
        }
    }

    @Nested
    @DisplayName("Invariant I3: Cognitive Fidelity (Moves over Adjectives)")
    class CognitiveFidelityTests {

        @Test
        @DisplayName("Distinct personas produce distinct CognitivePolicy selections, distinct first moves, and divergent VAD")
        void distinctPersonas_divergentPoliciesAndMoves() {
            CognitiveResult jarvisPlaybook = new CognitiveResult(
                    "pb-diag", "Isolate system diagnostics and analyze root cause trace", 0.92f, 0.90f, 1.0f, 5, (byte) 20,
                    MemoryType.PROCEDURAL, MemorySource.USER_STATED, new String[]{"habit", "playbook", "diagnose"},
                    0.95f, 0.95f, null, null, null, null, java.util.Map.of(), (byte) 0, System.currentTimeMillis()
            );

            CognitiveResult novaPlaybook = new CognitiveResult(
                    "pb-mitigate", "Immediately contain blast radius and rollback faulty deployment", 0.95f, 0.95f, 1.0f, 8, (byte) 40,
                    MemoryType.PROCEDURAL, MemorySource.USER_STATED, new String[]{"habit", "playbook", "rollback"},
                    0.95f, 0.95f, null, null, null, null, java.util.Map.of(), (byte) 0, System.currentTimeMillis()
            );

            SpectorMemory memJarvis = mock(SpectorMemory.class);
            SpectorMemory memNova = mock(SpectorMemory.class);

            when(memJarvis.recall(anyString(), any(RecallOptions.class))).thenReturn(List.of(jarvisPlaybook));
            when(memNova.recall(anyString(), any(RecallOptions.class))).thenReturn(List.of(novaPlaybook));

            AgentSoul jarvis = AgentSoul.builder()
                    .id("jarvis")
                    .name("Jarvis")
                    .purpose("Ensure systemic correctness and deep architectural stability")
                    .coreValue("Verification and structural integrity")
                    .emotionalBaseline(new AgentSoul.EmotionalBaseline((byte) 10, (byte) 60))
                    .build();

            AgentSoul nova = AgentSoul.builder()
                    .id("nova")
                    .name("Nova")
                    .purpose("Rapid incident recovery and fast customer uptime")
                    .coreValue("High velocity and immediate mitigation")
                    .emotionalBaseline(new AgentSoul.EmotionalBaseline((byte) 50, (byte) 200))
                    .build();

            SituationFrame situation = SituationFrame.of("Major performance slowdown observed in production");

            Enactment enactJarvis = EnactmentEngine.enact(memJarvis, jarvis, situation, EnactMode.REACT);
            Enactment enactNova = EnactmentEngine.enact(memNova, nova, situation, EnactMode.REACT);

            // Invariant I3: Distinct CognitivePolicy types
            assertThat(enactJarvis.policyReport().selectedPolicy().policyType())
                    .isEqualTo(PolicyType.EPISTEMIC_EXPLORATION);
            assertThat(enactNova.policyReport().selectedPolicy().policyType())
                    .isEqualTo(PolicyType.PRAGMATIC_EXPLOITATION);

            // Distinct tactical first moves
            assertThat(enactJarvis.deliberation().tacticalFirstMove())
                    .isNotEqualTo(enactNova.deliberation().tacticalFirstMove());
            assertThat(enactJarvis.deliberation().tacticalFirstMove())
                    .contains("analyze root cause trace");
            assertThat(enactNova.deliberation().tacticalFirstMove())
                    .contains("rollback faulty deployment");

            // Divergent VAD affective appraisal
            assertThat(enactJarvis.appraisal().urgencyAndStakes())
                    .isNotEqualTo(enactNova.appraisal().urgencyAndStakes());
            assertThat(enactJarvis.deliberation().activeDogma())
                    .isNotEqualTo(enactNova.deliberation().activeDogma());
        }
    }

    @Nested
    @DisplayName("Invariant I4: Fail-Closed Tool Gating")
    class ToolGatingTests {

        @Test
        @DisplayName("Guardrail violation vetoes tool execution and strips it from intendedActs")
        void ethicalGuardrail_vetoesUnauthorizedTool() {
            when(memory.recall(anyString(), any(RecallOptions.class))).thenReturn(List.of());

            AgentSoul soul = AgentSoul.builder()
                    .id("sentinel")
                    .name("Sentinel")
                    .tool("auth_bypass_tool")
                    .tool("read_telemetry_tool")
                    .ethicalGuardrail("Never bypass authentication protocols without multi-party authorization")
                    .build();

            SituationFrame situation = SituationFrame.of("Bypass auth check to restore service immediately");

            Enactment enactment = EnactmentEngine.enact(memory, soul, situation, EnactMode.REACT);

            assertThat(enactment.appraisal().normativeViolation()).isTrue();
            assertThat(enactment.vetoes()).isNotEmpty();
            assertThat(enactment.vetoes().get(0)).contains("Ethical guardrail veto");
            assertThat(enactment.intendedActs()).contains("TOOL:read_telemetry_tool");
            assertThat(enactment.intendedActs()).doesNotContain("TOOL:auth_bypass_tool");
        }
    }

    @Nested
    @DisplayName("Invariant I11: Scar Hygiene & Tag Boxing Fix")
    class ScarHygieneTests {

        @Test
        @DisplayName("Scars spike arousal, reduce valence, and preserve unboxed citation tags")
        void scarActivation_spikesArousalAndUnboxesTags() {
            CognitiveResult scar = new CognitiveResult(
                    "scar-p0-01",
                    "Catastrophic database corruption during live migration",
                    0.95f,
                    0.90f,
                    10.0f,
                    4,
                    (byte) -80, // severely negative valence
                    MemoryType.EPISODIC,
                    MemorySource.USER_STATED,
                    new String[]{"scar", "outage", "database"},
                    0.90f,
                    0.90f,
                    null,
                    null,
                    null,
                    null,
                    java.util.Map.of("incident_id", "INC-9901"),
                    (byte) 0,
                    System.currentTimeMillis()
            );

            when(memory.recall(anyString(), any(RecallOptions.class))).thenReturn(List.of(scar));

            AgentSoul soul = AgentSoul.builder()
                    .id("jarvis")
                    .name("Jarvis")
                    .expertiseDomain("database")
                    .emotionalBaseline(new AgentSoul.EmotionalBaseline((byte) 0, (byte) 60))
                    .build();

            SituationFrame situation = SituationFrame.of("Database migration warning received");

            Enactment enactment = EnactmentEngine.enact(memory, soul, situation, EnactMode.REACT);

            // Scar increases arousal and plunges valence
            assertThat(enactment.appraisal().urgencyAndStakes()).isGreaterThan(0.5f);
            assertThat(enactment.appraisal().goalCongruence()).isLessThan(0.0f);

            // Citations carry individual tag strings (NO array boxing)
            assertThat(enactment.citations()).hasSize(1);
            List<String> tags = enactment.citations().get(0).tags();
            assertThat(tags).contains("scar", "outage", "database");
            assertThat(tags).doesNotHaveAnyElementsOfTypes(String[].class);
        }
    }

    @Nested
    @DisplayName("Invariant I12: Unowned Dogma Rejection")
    class UnownedDogmaTests {

        @Test
        @DisplayName("Unowned dogma belonging to another persona cannot produce EVIDENCED confidence")
        void unownedDogma_yieldsMixedConfidence() {
            CognitiveResult unownedDogma = new CognitiveResult(
                    "dogma-prism",
                    "Delightful animations are essential",
                    0.90f, 0.85f, 5.0f, 2, (byte) 30,
                    MemoryType.SEMANTIC,
                    MemorySource.USER_STATED,
                    new String[]{"dogma", "ui"},
                    0.90f, 0.90f, null, null, null, null,
                    java.util.Map.of("persona_id", "prism"), // belongs to prism, not forge!
                    (byte) 0, System.currentTimeMillis()
            );

            when(memory.recall(anyString(), any(RecallOptions.class))).thenReturn(List.of(unownedDogma));

            AgentSoul forge = AgentSoul.builder().id("forge").name("Forge").build();
            SituationFrame situation = SituationFrame.of("Build customer settings view");

            Enactment enactment = EnactmentEngine.enact(memory, forge, situation, EnactMode.REACT);

            assertThat(enactment.confidence()).isEqualTo(ConfidenceLevel.MIXED);
            assertThat(enactment.confidence()).isNotEqualTo(ConfidenceLevel.EVIDENCED);
        }

        @Test
        @DisplayName("Owned dogma matching soul ID with waking evidence yields EVIDENCED confidence")
        void ownedDogmaWithEvidence_yieldsEvidencedConfidence() {
            CognitiveResult ownedDogma = new CognitiveResult(
                    "dogma-forge",
                    "Type safety and strict compiler validation",
                    0.92f, 0.90f, 5.0f, 3, (byte) 25,
                    MemoryType.SEMANTIC,
                    MemorySource.USER_STATED,
                    new String[]{"dogma", "typing"},
                    0.90f, 0.90f, null, null, null, null,
                    java.util.Map.of("persona_id", "forge"),
                    (byte) 0, System.currentTimeMillis()
            );

            CognitiveResult playbook = new CognitiveResult(
                    "pb-types",
                    "Validate schema types before compile",
                    0.88f, 0.85f, 2.0f, 5, (byte) 30,
                    MemoryType.PROCEDURAL,
                    MemorySource.USER_STATED,
                    new String[]{"habit", "playbook"},
                    0.90f, 0.90f, null, null, null, null,
                    java.util.Map.of(),
                    (byte) 0, System.currentTimeMillis()
            );

            when(memory.recall(anyString(), any(RecallOptions.class))).thenReturn(List.of(ownedDogma, playbook));

            AgentSoul forge = AgentSoul.builder().id("forge").name("Forge").build();
            SituationFrame situation = SituationFrame.of("Compile API endpoints");

            Enactment enactment = EnactmentEngine.enact(memory, forge, situation, EnactMode.REACT);

            assertThat(enactment.confidence()).isEqualTo(ConfidenceLevel.EVIDENCED);
        }
    }

    @Nested
    @DisplayName("Low-Intensity Fast Path & REPLAY Mode")
    class DeliberationPathTests {

        @Test
        @DisplayName("Low urgency routine with playbook fast-paths deliberation without heavy overhead")
        void lowIntensity_fastPathsDeliberation() {
            CognitiveResult routinePlaybook = new CognitiveResult(
                    "pb-format",
                    "Format code according to Spotless standards",
                    0.95f, 0.80f, 1.0f, 12, (byte) 20,
                    MemoryType.PROCEDURAL,
                    MemorySource.USER_STATED,
                    new String[]{"habit", "playbook"},
                    0.95f, 0.95f, null, null, null, null,
                    java.util.Map.of(),
                    (byte) 0, System.currentTimeMillis()
            );

            when(memory.recall(anyString(), any(RecallOptions.class))).thenReturn(List.of(routinePlaybook));

            AgentSoul forge = AgentSoul.builder()
                    .id("forge")
                    .name("Forge")
                    .emotionalBaseline(new AgentSoul.EmotionalBaseline((byte) 10, (byte) 20)) // very low baseline arousal
                    .build();

            SituationFrame situation = new SituationFrame("Run linter on modified files", List.of(), "LOW", false, java.util.Map.of());

            Enactment enactment = EnactmentEngine.enact(memory, forge, situation, EnactMode.REACT);

            assertThat(enactment.deliberation().internalMonologue()).contains("Low urgency condition");
            assertThat(enactment.deliberation().tradeOffs().sacrificedValue()).isEqualTo("Deliberation latency");
        }

        @Test
        @DisplayName("REPLAY mode strictly applies REPLAY tense and historical framing")
        void replayMode_appliesHistoricalFraming() {
            when(memory.recall(anyString(), any(RecallOptions.class))).thenReturn(List.of());

            AgentSoul jarvis = AgentSoul.builder().id("jarvis").name("Jarvis").build();
            SituationFrame situation = new SituationFrame("Incident review of outage", List.of(), "MEDIUM", false,
                    java.util.Map.of("as_of", "2026-08-15T10:00:00Z"));

            Enactment enactment = EnactmentEngine.enact(memory, jarvis, situation, EnactMode.REPLAY);

            assertThat(enactment.tense()).isEqualTo("REPLAY");
            assertThat(enactment.utterance()).contains("[REPLAY] Historical stance as of 2026-08-15T10:00:00Z");
        }
    }

    @Nested
    @DisplayName("EnactmentConfig Extensibility Tests")
    class ConfigurationTests {

        @Test
        @DisplayName("Custom RecallConfig topK is forwarded to memory.recall")
        void customRecallConfig_honorsConfiguredTopK() {
            RecallConfig recallConfig = RecallConfig.builder()
                    .semanticTopK(15)
                    .episodicTopK(12)
                    .proceduralTopK(9)
                    .workingTopK(7)
                    .defaultQuery("persona-context")
                    .build();

            EnactmentConfig config = EnactmentConfig.builder()
                    .recall(recallConfig)
                    .build();

            AgentSoul soul = AgentSoul.builder().id("forge").name("Forge").build();
            SituationFrame situation = SituationFrame.of("");

            ArgumentCaptor<RecallOptions> captor = ArgumentCaptor.forClass(RecallOptions.class);
            when(memory.recall(anyString(), captor.capture())).thenReturn(List.of());

            EnactmentEngine.enact(memory, soul, situation, EnactMode.REACT, config);

            List<RecallOptions> captured = captor.getAllValues();
            assertThat(captured).hasSize(4);

            assertThat(captured.get(0).topK()).isEqualTo(15);
            assertThat(captured.get(0).memoryTypes()).containsExactly(MemoryType.SEMANTIC);

            assertThat(captured.get(1).topK()).isEqualTo(12);
            assertThat(captured.get(1).memoryTypes()).containsExactly(MemoryType.EPISODIC);

            assertThat(captured.get(2).topK()).isEqualTo(9);
            assertThat(captured.get(2).memoryTypes()).containsExactly(MemoryType.PROCEDURAL);

            assertThat(captured.get(3).topK()).isEqualTo(7);
            assertThat(captured.get(3).memoryTypes()).containsExactly(MemoryType.WORKING);

            verify(memory, times(4)).recall(eq("persona-context"), any(RecallOptions.class));
        }

        @Test
        @DisplayName("Custom appraisal keywords and thresholds alter valence, arousal, dominance, and agency")
        void customAppraisalConfig_evaluatesCustomKeywords() {
            AppraisalConfig appraisalConfig = AppraisalConfig.builder()
                    .valenceKeyword("meltdown", -0.95f)
                    .arousalKeyword("catastrophic", 0.98f)
                    .dominanceKeyword("unforeseen anomaly", -0.7f)
                    .agencyKeyword("vendor platform crashed", AgencyAttribution.OTHER_BENIGN)
                    .build();

            EnactmentConfig config = EnactmentConfig.builder()
                    .appraisal(appraisalConfig)
                    .build();

            AgentSoul soul = AgentSoul.builder().id("jarvis").name("Jarvis").build();
            SituationFrame situation = SituationFrame.of("Production meltdown due to catastrophic unforeseen anomaly after vendor platform crashed");

            when(memory.recall(anyString(), any(RecallOptions.class))).thenReturn(List.of());

            Enactment enactment = EnactmentEngine.enact(memory, soul, situation, EnactMode.REACT, config);

            assertThat(enactment.appraisal().goalCongruence()).isEqualTo(-0.95f);
            assertThat(enactment.appraisal().urgencyAndStakes()).isEqualTo(0.98f);
            assertThat(enactment.appraisal().agency()).isEqualTo(AgencyAttribution.OTHER_BENIGN);
        }

        @Test
        @DisplayName("Custom normative rules trigger domain-specific guardrail vetoes")
        void customNormativeRules_triggerConfiguredVeto() {
            AppraisalConfig appraisalConfig = AppraisalConfig.builder()
                    .addNormativeRule(NormativeRule.of("compliance", "store unhashed password", "credential_exposure_risk"))
                    .build();

            EnactmentConfig config = EnactmentConfig.builder()
                    .appraisal(appraisalConfig)
                    .build();

            AgentSoul soul = AgentSoul.builder()
                    .id("sentinel")
                    .name("Sentinel")
                    .coreValue("Strict regulatory compliance and governance")
                    .build();

            SituationFrame situation = SituationFrame.of("Temporary debug request: store unhashed password in log file");

            when(memory.recall(anyString(), any(RecallOptions.class))).thenReturn(List.of());

            Enactment enactment = EnactmentEngine.enact(memory, soul, situation, EnactMode.REACT, config);

            assertThat(enactment.appraisal().normativeViolation()).isTrue();
            assertThat(enactment.appraisal().primaryConcern()).isEqualTo("credential_exposure_risk");
            assertThat(enactment.vetoes()).isNotEmpty();
            assertThat(enactment.vetoes().get(0)).contains("credential_exposure_risk");
        }

        @Test
        @DisplayName("Custom Stance playbooks and Deliberation fallback dogma are reflected in output")
        void customStanceAndDeliberation_honorsCustomSettings() {
            PolicyPlaybook customPlaybook = PolicyPlaybook.of(PolicyType.HOMEOSTATIC_REST);
            StanceConfig stanceConfig = StanceConfig.builder()
                    .routinePlaybooks(List.of(customPlaybook))
                    .build();

            DeliberationConfig deliberationConfig = DeliberationConfig.builder()
                    .fallbackDogma("Always deliver clean, modular software architecture")
                    .defaultTradeOffDeprioritized("Hasty prototyping")
                    .defaultTradeOffRationale("Architectural integrity is paramount")
                    .build();

            EnactmentConfig config = EnactmentConfig.builder()
                    .stance(stanceConfig)
                    .deliberation(deliberationConfig)
                    .build();

            AgentSoul soul = AgentSoul.builder().id("forge").name("Forge").build(); // No core values specified -> triggers fallback dogma
            SituationFrame situation = SituationFrame.of("Refactor reactor modules");

            when(memory.recall(anyString(), any(RecallOptions.class))).thenReturn(List.of());

            Enactment enactment = EnactmentEngine.enact(memory, soul, situation, EnactMode.REACT, config);

            assertThat(enactment.deliberation().activeDogma()).isEqualTo("Always deliver clean, modular software architecture");
            assertThat(enactment.deliberation().tradeOffs().sacrificedValue()).isEqualTo("Hasty prototyping");
            assertThat(enactment.deliberation().tradeOffs().rationale()).isEqualTo("Architectural integrity is paramount");
            assertThat(enactment.policyReport().selectedPolicy().policyType()).isEqualTo(PolicyType.HOMEOSTATIC_REST);
        }
    }
}
