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
package com.spectrayan.spector.memory.pathway;

import com.spectrayan.spector.memory.DefaultSpectorMemory;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.kernel.api.MemorySource;
import com.spectrayan.spector.memory.model.CognitiveProfile;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.MemoryPersistenceMode;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.memory.model.RecallMode;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.embedding.EmbeddingResult;
import com.spectrayan.spector.commons.pathway.PathwayEngine;
import com.spectrayan.spector.commons.pathway.PathwayComposer;
import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.memory.pathway.recall.relay.*;
import com.spectrayan.spector.memory.pathway.reflect.relay.*;
import com.spectrayan.spector.memory.pathway.remember.relay.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * End-to-end parity test verifying the Cognitive Pathway Engine architecture with
 * {@link com.spectrayan.spector.memory.pathway.recall.RecallPathway} and {@link com.spectrayan.spector.memory.pathway.remember.RememberPathway}.
 */
@DisplayName("PathwayParityTest")
class PathwayParityTest {

    private static final int DIMENSIONS = 32;

    private SpectorMemory legacyMemory;
    private SpectorMemory pathwayMemory;

    @BeforeEach
    void setUp() {
        final EmbeddingProvider provider = new MockEmbeddingProvider(DIMENSIONS);

        var legacyProps = new com.spectrayan.spector.config.properties.MemoryProperties()
                .setDimensions(DIMENSIONS)
                .setWorkingCapacity(50)
                .setEpisodicPartitionCapacity(100)
                .setSemanticCapacity(100)
                .setProceduralCapacity(100)
                .setPathwayEnabled(false);

        legacyMemory = DefaultSpectorMemory.builder(legacyProps)
                .embeddingProvider(provider)
                .persistenceMode(MemoryPersistenceMode.IN_MEMORY)
                .build();

        var pathwayProps = new com.spectrayan.spector.config.properties.MemoryProperties()
                .setDimensions(DIMENSIONS)
                .setWorkingCapacity(50)
                .setEpisodicPartitionCapacity(100)
                .setSemanticCapacity(100)
                .setProceduralCapacity(100)
                .setPathwayEnabled(true);

        pathwayMemory = DefaultSpectorMemory.builder(pathwayProps)
                .embeddingProvider(provider)
                .persistenceMode(MemoryPersistenceMode.IN_MEMORY)
                .build();
    }

    @AfterEach
    void tearDown() {
        if (legacyMemory != null) legacyMemory.close();
        if (pathwayMemory != null) pathwayMemory.close();
    }

    @Test
    @DisplayName("Core Ingest & Recall: produces identical results across all tiers and options")
    void testCoreIngestAndRecallParity() {
        populateStandardDataset(legacyMemory);
        populateStandardDataset(pathwayMemory);

        // Assert memory counts match exactly across all tiers
        assertThat(pathwayMemory.totalMemories()).isEqualTo(legacyMemory.totalMemories());
        for (final MemoryType type : MemoryType.values()) {
            assertThat(pathwayMemory.memoryCount(type))
                    .as("Tier count for " + type)
                    .isEqualTo(legacyMemory.memoryCount(type));
        }

        // Standard text recall
        assertRecallParity("database lock timeout", RecallOptions.builder().topK(10).build());

        // Recall with importance filter
        assertRecallParity("user settings UI", RecallOptions.builder().topK(5).minImportance(0.2f).build());

        // Recall restricted to specific memory tiers
        assertRecallParity("programming guidelines", RecallOptions.builder().topK(5).memoryTypes(MemoryType.PROCEDURAL, MemoryType.SEMANTIC).build());
    }

    @Test
    @DisplayName("Cognitive Profiles: all 6 profiles produce identical candidate ranking and scores")
    void testCognitiveProfileParity() {
        populateStandardDataset(legacyMemory);
        populateStandardDataset(pathwayMemory);

        for (final CognitiveProfile profile : CognitiveProfile.values()) {
            final RecallOptions options = RecallOptions.builder()
                    .topK(5)
                    .profile(profile)
                    .build();
            assertRecallParity("concurrent database transactions", options);
        }
    }

    @Test
    @DisplayName("Synaptic Tag Filtering: Bloom filter bitmasking matches legacy pipeline exactly")
    void testSynapticTagFilterParity() {
        populateStandardDataset(legacyMemory);
        populateStandardDataset(pathwayMemory);

        final RecallOptions options = RecallOptions.builder()
                .topK(10)
                .synapticFilter("database", "error")
                .build();

        assertRecallParity("query failure analysis", options);
    }

    @Test
    @DisplayName("MMR Diversity: maximal marginal relevance reranking yields identical diverse subsets")
    void testMmrDiversityParity() {
        populateStandardDataset(legacyMemory);
        populateStandardDataset(pathwayMemory);

        final RecallOptions options = RecallOptions.builder()
                .topK(5)
                .enableMmr(true)
                .mmrLambda(0.6f)
                .build();

        assertRecallParity("general preferences and rules", options);
    }

    @Test
    @DisplayName("Hybrid Search: BM25 lexical fusion produces identical reciprocal rank fusion scores")
    void testHybridSearchParity() {
        populateStandardDataset(legacyMemory);
        populateStandardDataset(pathwayMemory);

        final RecallOptions options = RecallOptions.builder()
                .topK(5)
                .enableTextSearch(true)
                .gamma(0.5f)
                .build();

        assertRecallParity("database lock", options);
    }

    @Test
    @DisplayName("Habituation & Satiation: repetition suppression degrades identically under RecallMode.LEARN")
    void testHabituationParity() {
        populateStandardDataset(legacyMemory);
        populateStandardDataset(pathwayMemory);

        final RecallOptions options = RecallOptions.builder()
                .topK(5)
                .recallMode(RecallMode.LEARN)
                .build();

        // 3 consecutive identical queries
        for (int i = 0; i < 3; i++) {
            assertRecallParity("frequently accessed database query", options);
        }
    }

    @Test
    @DisplayName("Deduplication & Idempotency: duplicate ID updates match between engines")
    void testDeduplicationParity() {
        legacyMemory.remember("dedup-1", "Initial text version.", MemoryType.SEMANTIC, MemorySource.USER_STATED, "tag1");
        pathwayMemory.remember("dedup-1", "Initial text version.", MemoryType.SEMANTIC, MemorySource.USER_STATED, "tag1");

        // Re-ingest with same ID
        legacyMemory.remember("dedup-1", "Updated text version.", MemoryType.SEMANTIC, MemorySource.USER_STATED, "tag1");
        pathwayMemory.remember("dedup-1", "Updated text version.", MemoryType.SEMANTIC, MemorySource.USER_STATED, "tag1");

        assertThat(pathwayMemory.totalMemories()).isEqualTo(legacyMemory.totalMemories());
        assertRecallParity("Updated text", RecallOptions.builder().topK(5).build());
    }

    private void populateStandardDataset(final SpectorMemory memory) {
        memory.remember("mem-1", "User prefers dark mode for UI themes.", MemoryType.EPISODIC, MemorySource.USER_STATED, "ui", "preferences");
        memory.remember("mem-2", "User prefers Java over Python for high performance.", MemoryType.EPISODIC, MemorySource.USER_STATED, "language", "preferences");
        memory.remember("mem-3", "Database lock timeout on table users during batch migration.", MemoryType.EPISODIC, MemorySource.OBSERVED, "error", "database");
        memory.remember("mem-4", "PostgreSQL connection pool exhausted under heavy load.", MemoryType.EPISODIC, MemorySource.OBSERVED, "error", "database", "sql");
        memory.remember("mem-5", "Java is a statically typed object-oriented language.", MemoryType.SEMANTIC, MemorySource.OBSERVED, "java", "programming");
        memory.remember("mem-6", "Relational databases use ACID transactions for consistency.", MemoryType.SEMANTIC, MemorySource.OBSERVED, "database", "acid");
        memory.remember("mem-7", "Always check null bounds before pointer dereferencing.", MemoryType.PROCEDURAL, MemorySource.PROCEDURAL, "rule", "safety");
        memory.remember("mem-8", "Run unit tests before submitting pull requests.", MemoryType.PROCEDURAL, MemorySource.PROCEDURAL, "rule", "git");
        memory.remember("mem-9", "Temporary buffer state for ongoing reasoning task.", MemoryType.WORKING, MemorySource.INFERRED, "scratch");
    }

    private void assertRecallParity(final String query, final RecallOptions options) {
        final List<CognitiveResult> legacyResults = legacyMemory.recall(query, options);
        final List<CognitiveResult> pathwayResults = pathwayMemory.recall(query, options);

        assertThat(pathwayResults)
                .as("Result size parity for query '" + query + "'")
                .hasSameSizeAs(legacyResults);

        java.util.Map<String, CognitiveResult> pathwayMap = pathwayResults.stream()
                .collect(java.util.stream.Collectors.toMap(CognitiveResult::id, r -> r, (r1, r2) -> r1));

        for (final CognitiveResult legacy : legacyResults) {
            final CognitiveResult pathway = pathwayMap.get(legacy.id());
            assertThat(pathway)
                    .as("Expected memory ID '" + legacy.id() + "' in pathway recall for query '" + query + "'")
                    .isNotNull();

            assertThat(pathway.memoryType())
                    .as("MemoryType parity for ID '" + legacy.id() + "' in query '" + query + "'")
                    .isEqualTo(legacy.memoryType());

            assertThat(pathway.score())
                    .as("Score parity for ID '" + legacy.id() + "' in query '" + query + "'")
                    .isCloseTo(legacy.score(), within(0.001f));
        }
    }

    private static class MockEmbeddingProvider implements EmbeddingProvider {
        private final int dims;

        MockEmbeddingProvider(final int dims) {
            this.dims = dims;
        }

        @Override
        public EmbeddingResult embed(final String text) {
            final Random rng = new Random(text.hashCode());
            final float[] vector = new float[dims];
            for (int i = 0; i < dims; i++) {
                vector[i] = (rng.nextFloat() - 0.5f) * 2.0f;
            }
            float norm = 0f;
            for (final float v : vector) norm += v * v;
            norm = (float) Math.sqrt(norm);
            if (norm > 0) {
                for (int i = 0; i < dims; i++) vector[i] /= norm;
            }
            return new EmbeddingResult(vector, text.split("\\s+").length, "mock-" + dims + "d");
        }

        @Override
        public int dimensions() { return dims; }

        @Override
        public String modelName() { return "mock-" + dims + "d"; }
    }

    @Nested
    @DisplayName("M6.4 Gate: Recipe vs Factory Relay Parity")
    class RecipeRelayParity {

        @Test
        @DisplayName("Remember: Recipe-built relay names and ordering match factory output exactly")
        @SuppressWarnings("deprecation")
        void rememberPathwayParity() {
            DedupGuardRelay dedup = Mockito.mock(DedupGuardRelay.class);
            SynapticTagTransductionRelay tags = Mockito.mock(SynapticTagTransductionRelay.class);
            DopaminergicSurpriseRelay surprise = Mockito.mock(DopaminergicSurpriseRelay.class);
            CorticalWriteTransactionRelay write = Mockito.mock(CorticalWriteTransactionRelay.class);
            SynapticGraphLinkingRelay graph = Mockito.mock(SynapticGraphLinkingRelay.class);
            KnowledgeGraphEnrichmentRelay kg = Mockito.mock(KnowledgeGraphEnrichmentRelay.class);

            PathwayEngine<RememberSignal> factoryPathway = RememberPathwayFactory.create(
                    dedup, tags, surprise, write, graph, kg);

            var composer = PathwayComposer.<RememberSignal>of("remember");
            new RememberRecipe(dedup, tags, surprise, write, graph, kg).compose(composer);
            PathwayEngine<RememberSignal> recipePathway = composer.build();

            assertThat(recipePathway.relayNames())
                    .as("Remember relay names and order must match factory")
                    .isEqualTo(factoryPathway.relayNames());
        }

        @Test
        @DisplayName("Recall: Recipe-built relay names and ordering match widest factory output exactly")
        @SuppressWarnings({"deprecation", "unchecked"})
        void recallPathwayParity() {
            SynapticRelay<RecallSignal> transduction = Mockito.mock(SynapticRelay.class);
            SynapticRelay<RecallSignal> prospective = Mockito.mock(SynapticRelay.class);
            SynapticRelay<RecallSignal> releaseGate = Mockito.mock(SynapticRelay.class);
            SynapticRelay<RecallSignal> homeostatic = Mockito.mock(SynapticRelay.class);
            SynapticRelay<RecallSignal> vector = Mockito.mock(SynapticRelay.class);
            SynapticRelay<RecallSignal> freeEnergy = Mockito.mock(SynapticRelay.class);
            SynapticRelay<RecallSignal> spacetime = Mockito.mock(SynapticRelay.class);
            SynapticRelay<RecallSignal> scoring = Mockito.mock(SynapticRelay.class);
            SynapticRelay<RecallSignal> graph = Mockito.mock(SynapticRelay.class);
            SynapticRelay<RecallSignal> hopfield = Mockito.mock(SynapticRelay.class);
            SynapticRelay<RecallSignal> evidence = Mockito.mock(SynapticRelay.class);
            SynapticRelay<RecallSignal> lateral = Mockito.mock(SynapticRelay.class);
            SynapticRelay<RecallSignal> bm25 = Mockito.mock(SynapticRelay.class);
            RrfRescoreRelay rrf = Mockito.mock(RrfRescoreRelay.class);
            SynapticRelay<RecallSignal> manifold = Mockito.mock(SynapticRelay.class);
            SynapticRelay<RecallSignal> constructive = Mockito.mock(SynapticRelay.class);
            SynapticRelay<RecallSignal> consciousness = Mockito.mock(SynapticRelay.class);
            SortAndTruncateRelay sort = Mockito.mock(SortAndTruncateRelay.class);
            CognitiveRerankRelay colbert = Mockito.mock(CognitiveRerankRelay.class);
            MmrDiversityRelay mmr = Mockito.mock(MmrDiversityRelay.class);
            TemperatureSoftmaxRelay temp = Mockito.mock(TemperatureSoftmaxRelay.class);
            SynapticRelay<RecallSignal> consciousAccess = Mockito.mock(SynapticRelay.class);
            SynapticRelay<RecallSignal> persistence = Mockito.mock(SynapticRelay.class);
            SynapticRelay<RecallSignal> epistemic = Mockito.mock(SynapticRelay.class);
            com.spectrayan.spector.commons.pathway.ConsolidationRelay<RecallSignal> consolidation =
                    Mockito.mock(com.spectrayan.spector.commons.pathway.ConsolidationRelay.class);

            PathwayEngine<RecallSignal> factoryPathway = RecallPathwayFactory.create(
                    null, transduction, prospective, releaseGate, homeostatic, vector,
                    freeEnergy, spacetime, scoring, graph, hopfield, evidence, lateral,
                    bm25, rrf, manifold, constructive, consciousness, sort, colbert,
                    mmr, temp, consciousAccess, persistence, epistemic, consolidation);

            var composer = PathwayComposer.<RecallSignal>of("recall");
            RecallRecipe.builder()
                    .transductionRelay(transduction)
                    .prospectiveRelay(prospective)
                    .governedReleaseGateRelay(releaseGate)
                    .homeostaticBiasRelay(homeostatic)
                    .vectorSearchRelay(vector)
                    .freeEnergyGuidedRelay(freeEnergy)
                    .spacetimeScoringRelay(spacetime)
                    .scoringRelay(scoring)
                    .graphExpansionRelay(graph)
                    .hopfieldAssociativeRelay(hopfield)
                    .evidenceFusionRelay(evidence)
                    .lateralInhibitionRelay(lateral)
                    .bm25SearchRelay(bm25)
                    .rrfRescoreRelay(rrf)
                    .manifoldRerankRelay(manifold)
                    .constructiveSimulationRelay(constructive)
                    .consciousnessContinuityRelay(consciousness)
                    .sortAndTruncateRelay(sort)
                    .cognitiveRerankRelay(colbert)
                    .mmrDiversityRelay(mmr)
                    .temperatureSoftmaxRelay(temp)
                    .consciousAccessRelay(consciousAccess)
                    .constructiveMemoryPersistenceRelay(persistence)
                    .epistemicLearningRelay(epistemic)
                    .consolidationRelay(consolidation)
                    .build()
                    .compose(composer);
            PathwayEngine<RecallSignal> recipePathway = composer.build();

            assertThat(recipePathway.relayNames())
                    .as("Recall relay names and order must match factory")
                    .isEqualTo(factoryPathway.relayNames());
        }

        @Test
        @DisplayName("Reflect: Recipe-built relay names and ordering match factory output exactly")
        @SuppressWarnings({"deprecation", "unchecked"})
        void reflectPathwayParity() {
            SynapticPruningRelay pruning = Mockito.mock(SynapticPruningRelay.class);
            EpisodicLogConsolidationRelay log = Mockito.mock(EpisodicLogConsolidationRelay.class);
            SoulDriftRefusionRelay soul = Mockito.mock(SoulDriftRefusionRelay.class);
            ProceduralCrystallizationRelay procedural = Mockito.mock(ProceduralCrystallizationRelay.class);
            ProactiveInterferenceRelay interference = Mockito.mock(ProactiveInterferenceRelay.class);
            HebbianHomeostasisRelay hebbian = Mockito.mock(HebbianHomeostasisRelay.class);
            TemporalPruningRelay temporal = Mockito.mock(TemporalPruningRelay.class);
            CrossLayerPromotionRelay promotion = Mockito.mock(CrossLayerPromotionRelay.class);
            EntityMaintenanceRelay entity = Mockito.mock(EntityMaintenanceRelay.class);
            SpectralSparsificationRelay sparsification = Mockito.mock(SpectralSparsificationRelay.class);
            SynapticRelay<ReflectSignal> manifold = Mockito.mock(SynapticRelay.class);
            SynapticRelay<ReflectSignal> softAnchor = Mockito.mock(SynapticRelay.class);
            WalJournalRelay wal = Mockito.mock(WalJournalRelay.class);
            IdiolectLearningRelay idiolect = Mockito.mock(IdiolectLearningRelay.class);

            PathwayEngine<ReflectSignal> factoryPathway = ReflectPathwayFactory.create(
                    null, pruning, log, soul, procedural, interference, hebbian,
                    temporal, promotion, entity, sparsification, manifold, softAnchor, wal, idiolect);

            var composer = PathwayComposer.<ReflectSignal>of("reflect");
            ReflectRecipe.builder()
                    .pruningRelay(pruning)
                    .logConsolidationRelay(log)
                    .soulDriftRelay(soul)
                    .proceduralRelay(procedural)
                    .interferenceRelay(interference)
                    .hebbianRelay(hebbian)
                    .temporalRelay(temporal)
                    .promotionRelay(promotion)
                    .entityRelay(entity)
                    .sparsificationRelay(sparsification)
                    .manifoldConsolidationRelay(manifold)
                    .softIdentityAnchorRelay(softAnchor)
                    .walRelay(wal)
                    .idiolectRelay(idiolect)
                    .build()
                    .compose(composer);
            PathwayEngine<ReflectSignal> recipePathway = composer.build();

            assertThat(recipePathway.relayNames())
                    .as("Reflect relay names and order must match factory")
                    .isEqualTo(factoryPathway.relayNames());
        }
    }
}
