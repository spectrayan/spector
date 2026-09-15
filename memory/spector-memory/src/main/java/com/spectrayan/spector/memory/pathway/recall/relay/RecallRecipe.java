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

import com.spectrayan.spector.commons.pathway.ErrorPolicy;
import com.spectrayan.spector.commons.pathway.GatedRelay;
import com.spectrayan.spector.commons.pathway.PathwayComposer;
import com.spectrayan.spector.commons.pathway.PathwayRecipe;
import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.memory.pathway.RelayNames;

import java.util.Objects;

/**
 * Declarative recipe for composing the Recall cognitive pathway (ADR-0035 §7.3).
 *
 * <p>Collapses the multiple positional factory overloads in {@link RecallPathwayFactory} into
 * a single unified recipe supporting both core retrieval relays and optional Active Inference
 * Self-Model Engine (AISME) stages.</p>
 */
public final class RecallRecipe implements PathwayRecipe<RecallSignal> {

    private final SynapticRelay<RecallSignal> transductionRelay;
    private final SynapticRelay<RecallSignal> prospectiveRelay;
    private final SynapticRelay<RecallSignal> governedReleaseGateRelay;
    private final SynapticRelay<RecallSignal> homeostaticBiasRelay;
    private final SynapticRelay<RecallSignal> vectorSearchRelay;
    private final SynapticRelay<RecallSignal> freeEnergyGuidedRelay;
    private final SynapticRelay<RecallSignal> spacetimeScoringRelay;
    private final SynapticRelay<RecallSignal> scoringRelay;
    private final SynapticRelay<RecallSignal> graphExpansionRelay;
    private final SynapticRelay<RecallSignal> hopfieldAssociativeRelay;
    private final SynapticRelay<RecallSignal> evidenceFusionRelay;
    private final SynapticRelay<RecallSignal> lateralInhibitionRelay;
    private final SynapticRelay<RecallSignal> bm25SearchRelay;
    private final RrfRescoreRelay rrfRescoreRelay;
    private final SynapticRelay<RecallSignal> manifoldRerankRelay;
    private final SynapticRelay<RecallSignal> constructiveSimulationRelay;
    private final SynapticRelay<RecallSignal> consciousnessContinuityRelay;
    private final SortAndTruncateRelay sortAndTruncateRelay;
    private final CognitiveRerankRelay cognitiveRerankRelay;
    private final MmrDiversityRelay mmrDiversityRelay;
    private final TemperatureSoftmaxRelay temperatureSoftmaxRelay;
    private final SynapticRelay<RecallSignal> consciousAccessRelay;
    private final SynapticRelay<RecallSignal> constructiveMemoryPersistenceRelay;
    private final SynapticRelay<RecallSignal> epistemicLearningRelay;
    private final SynapticRelay<RecallSignal> consolidationRelay;

    private RecallRecipe(final Builder builder) {
        this.transductionRelay = Objects.requireNonNull(builder.transductionRelay, "transductionRelay cannot be null");
        this.prospectiveRelay = Objects.requireNonNull(builder.prospectiveRelay, "prospectiveRelay cannot be null");
        this.governedReleaseGateRelay = Objects.requireNonNull(builder.governedReleaseGateRelay, "governedReleaseGateRelay cannot be null");
        this.homeostaticBiasRelay = builder.homeostaticBiasRelay;
        this.vectorSearchRelay = Objects.requireNonNull(builder.vectorSearchRelay, "vectorSearchRelay cannot be null");
        this.freeEnergyGuidedRelay = builder.freeEnergyGuidedRelay;
        this.spacetimeScoringRelay = builder.spacetimeScoringRelay;
        this.scoringRelay = Objects.requireNonNull(builder.scoringRelay, "scoringRelay cannot be null");
        this.graphExpansionRelay = Objects.requireNonNull(builder.graphExpansionRelay, "graphExpansionRelay cannot be null");
        this.hopfieldAssociativeRelay = builder.hopfieldAssociativeRelay;
        this.evidenceFusionRelay = Objects.requireNonNull(builder.evidenceFusionRelay, "evidenceFusionRelay cannot be null");
        this.lateralInhibitionRelay = builder.lateralInhibitionRelay;
        this.bm25SearchRelay = builder.bm25SearchRelay;
        this.rrfRescoreRelay = builder.rrfRescoreRelay;
        this.manifoldRerankRelay = builder.manifoldRerankRelay;
        this.constructiveSimulationRelay = builder.constructiveSimulationRelay;
        this.consciousnessContinuityRelay = builder.consciousnessContinuityRelay;
        this.sortAndTruncateRelay = builder.sortAndTruncateRelay;
        this.cognitiveRerankRelay = builder.cognitiveRerankRelay;
        this.mmrDiversityRelay = builder.mmrDiversityRelay;
        this.temperatureSoftmaxRelay = builder.temperatureSoftmaxRelay;
        this.consciousAccessRelay = builder.consciousAccessRelay;
        this.constructiveMemoryPersistenceRelay = builder.constructiveMemoryPersistenceRelay;
        this.epistemicLearningRelay = builder.epistemicLearningRelay;
        this.consolidationRelay = builder.consolidationRelay;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void compose(final PathwayComposer<RecallSignal> composer) {
        composer.relay(RelayNames.TRANSDUCTION, transductionRelay)
                .relay(RelayNames.PROSPECTIVE, prospectiveRelay)
                .relay(RelayNames.GOVERNED_RELEASE_GATE, governedReleaseGateRelay)
                .relay(RelayNames.VECTOR_SEARCH, vectorSearchRelay);

        if (homeostaticBiasRelay != null) {
            composer.gated(RelayNames.HOMEOSTATIC_BIAS, RecallGates.HOMEOSTASIS_ENABLED, homeostaticBiasRelay, ErrorPolicy.DEGRADE_GRACEFULLY);
        }

        if (freeEnergyGuidedRelay != null) {
            composer.gated(RelayNames.FREE_ENERGY_GUIDED, RecallGates.FREE_ENERGY_ENABLED, freeEnergyGuidedRelay, ErrorPolicy.DEGRADE_GRACEFULLY);
        }

        final var effectiveSpacetimeRelay = spacetimeScoringRelay != null ? spacetimeScoringRelay : new SpacetimeScoringRelay();
        composer.gated(RelayNames.SPACETIME_SCORING, RecallGates.SPACETIME_ENABLED, effectiveSpacetimeRelay, ErrorPolicy.DEGRADE_GRACEFULLY)
                .relay(RelayNames.SCORING, scoringRelay)
                .gated(RelayNames.BM25_SEARCH, RecallGates.TEXT_SEARCH_ENABLED, bm25SearchRelay, ErrorPolicy.DEGRADE_GRACEFULLY)
                .gated(RelayNames.RRF_RESCORE, RecallGates.RRF_FUSED, rrfRescoreRelay, ErrorPolicy.DEGRADE_GRACEFULLY)
                .relay(RelayNames.GRAPH_EXPANSION, graphExpansionRelay);

        if (hopfieldAssociativeRelay != null) {
            composer.gated(RelayNames.HOPFIELD_ASSOCIATIVE, RecallGates.HOPFIELD_ENABLED, hopfieldAssociativeRelay, ErrorPolicy.DEGRADE_GRACEFULLY);
        }

        composer.relay(RelayNames.EVIDENCE_FUSION, evidenceFusionRelay);

        if (lateralInhibitionRelay != null) {
            composer.gated(RelayNames.LATERAL_INHIBITION, RecallGates.LATERAL_INHIBITION_ENABLED, lateralInhibitionRelay, ErrorPolicy.DEGRADE_GRACEFULLY);
        }

        if (manifoldRerankRelay != null) {
            composer.gated(RelayNames.MANIFOLD_RERANK, RecallGates.MANIFOLD_ENABLED, manifoldRerankRelay, ErrorPolicy.DEGRADE_GRACEFULLY);
        }

        if (constructiveSimulationRelay != null) {
            composer.gated(RelayNames.CONSTRUCTIVE_SIMULATION, RecallGates.CONSTRUCTIVE_SIMULATION_ENABLED, constructiveSimulationRelay, ErrorPolicy.DEGRADE_GRACEFULLY);
        }

        if (consciousnessContinuityRelay != null) {
            composer.gated(RelayNames.CONSCIOUSNESS_CONTINUITY, RecallGates.CONSCIOUSNESS_CONTINUITY_ENABLED, consciousnessContinuityRelay, ErrorPolicy.DEGRADE_GRACEFULLY);
        }

        if (mmrDiversityRelay != null) {
            composer.gated(RelayNames.MMR_RERANK, RecallGates.MMR_ENABLED, mmrDiversityRelay, ErrorPolicy.DEGRADE_GRACEFULLY);
        }

        if (sortAndTruncateRelay != null) {
            composer.relay(RelayNames.SORT_TRUNCATE, sortAndTruncateRelay);
        }

        if (cognitiveRerankRelay != null) {
            composer.circuitBreaker(RelayNames.COLBERT_RERANK,
                    new GatedRelay<>(RelayNames.COLBERT_RERANK, RecallGates.RERANK_CONFIGURED, cognitiveRerankRelay),
                    5, 30_000L, ErrorPolicy.DEGRADE_GRACEFULLY);
        }

        if (temperatureSoftmaxRelay != null) {
            composer.relay(RelayNames.TEMPERATURE, temperatureSoftmaxRelay);
        }

        if (consciousAccessRelay != null) {
            composer.gated(RelayNames.CONSCIOUS_ACCESS, RecallGates.CONSCIOUS_ACCESS_ENABLED, consciousAccessRelay, ErrorPolicy.DEGRADE_GRACEFULLY);
        }

        if (constructiveMemoryPersistenceRelay != null) {
            composer.gated(RelayNames.CONSTRUCTIVE_PERSISTENCE, RecallGates.CONSTRUCTIVE_PERSISTENCE_ENABLED, constructiveMemoryPersistenceRelay, ErrorPolicy.DEGRADE_GRACEFULLY);
        }

        if (epistemicLearningRelay != null) {
            composer.gated(RelayNames.EPISTEMIC_LEARNING, RecallGates.EPISTEMIC_LEARNING_ENABLED, epistemicLearningRelay, ErrorPolicy.DEGRADE_GRACEFULLY);
        }

        if (consolidationRelay != null) {
            composer.relay(RelayNames.CONSOLIDATION, consolidationRelay, ErrorPolicy.DEGRADE_GRACEFULLY);
        }
    }

    public static final class Builder {
        private SynapticRelay<RecallSignal> transductionRelay;
        private SynapticRelay<RecallSignal> prospectiveRelay;
        private SynapticRelay<RecallSignal> governedReleaseGateRelay;
        private SynapticRelay<RecallSignal> homeostaticBiasRelay;
        private SynapticRelay<RecallSignal> vectorSearchRelay;
        private SynapticRelay<RecallSignal> freeEnergyGuidedRelay;
        private SynapticRelay<RecallSignal> spacetimeScoringRelay;
        private SynapticRelay<RecallSignal> scoringRelay;
        private SynapticRelay<RecallSignal> graphExpansionRelay;
        private SynapticRelay<RecallSignal> hopfieldAssociativeRelay;
        private SynapticRelay<RecallSignal> evidenceFusionRelay;
        private SynapticRelay<RecallSignal> lateralInhibitionRelay;
        private SynapticRelay<RecallSignal> bm25SearchRelay;
        private RrfRescoreRelay rrfRescoreRelay;
        private SynapticRelay<RecallSignal> manifoldRerankRelay;
        private SynapticRelay<RecallSignal> constructiveSimulationRelay;
        private SynapticRelay<RecallSignal> consciousnessContinuityRelay;
        private SortAndTruncateRelay sortAndTruncateRelay;
        private CognitiveRerankRelay cognitiveRerankRelay;
        private MmrDiversityRelay mmrDiversityRelay;
        private TemperatureSoftmaxRelay temperatureSoftmaxRelay;
        private SynapticRelay<RecallSignal> consciousAccessRelay;
        private SynapticRelay<RecallSignal> constructiveMemoryPersistenceRelay;
        private SynapticRelay<RecallSignal> epistemicLearningRelay;
        private SynapticRelay<RecallSignal> consolidationRelay;

        public Builder transductionRelay(SynapticRelay<RecallSignal> r) { this.transductionRelay = r; return this; }
        public Builder prospectiveRelay(SynapticRelay<RecallSignal> r) { this.prospectiveRelay = r; return this; }
        public Builder governedReleaseGateRelay(SynapticRelay<RecallSignal> r) { this.governedReleaseGateRelay = r; return this; }
        public Builder homeostaticBiasRelay(SynapticRelay<RecallSignal> r) { this.homeostaticBiasRelay = r; return this; }
        public Builder vectorSearchRelay(SynapticRelay<RecallSignal> r) { this.vectorSearchRelay = r; return this; }
        public Builder freeEnergyGuidedRelay(SynapticRelay<RecallSignal> r) { this.freeEnergyGuidedRelay = r; return this; }
        public Builder spacetimeScoringRelay(SynapticRelay<RecallSignal> r) { this.spacetimeScoringRelay = r; return this; }
        public Builder scoringRelay(SynapticRelay<RecallSignal> r) { this.scoringRelay = r; return this; }
        public Builder graphExpansionRelay(SynapticRelay<RecallSignal> r) { this.graphExpansionRelay = r; return this; }
        public Builder hopfieldAssociativeRelay(SynapticRelay<RecallSignal> r) { this.hopfieldAssociativeRelay = r; return this; }
        public Builder evidenceFusionRelay(SynapticRelay<RecallSignal> r) { this.evidenceFusionRelay = r; return this; }
        public Builder lateralInhibitionRelay(SynapticRelay<RecallSignal> r) { this.lateralInhibitionRelay = r; return this; }
        public Builder bm25SearchRelay(SynapticRelay<RecallSignal> r) { this.bm25SearchRelay = r; return this; }
        public Builder rrfRescoreRelay(RrfRescoreRelay r) { this.rrfRescoreRelay = r; return this; }
        public Builder manifoldRerankRelay(SynapticRelay<RecallSignal> r) { this.manifoldRerankRelay = r; return this; }
        public Builder constructiveSimulationRelay(SynapticRelay<RecallSignal> r) { this.constructiveSimulationRelay = r; return this; }
        public Builder consciousnessContinuityRelay(SynapticRelay<RecallSignal> r) { this.consciousnessContinuityRelay = r; return this; }
        public Builder sortAndTruncateRelay(SortAndTruncateRelay r) { this.sortAndTruncateRelay = r; return this; }
        public Builder cognitiveRerankRelay(CognitiveRerankRelay r) { this.cognitiveRerankRelay = r; return this; }
        public Builder mmrDiversityRelay(MmrDiversityRelay r) { this.mmrDiversityRelay = r; return this; }
        public Builder temperatureSoftmaxRelay(TemperatureSoftmaxRelay r) { this.temperatureSoftmaxRelay = r; return this; }
        public Builder consciousAccessRelay(SynapticRelay<RecallSignal> r) { this.consciousAccessRelay = r; return this; }
        public Builder constructiveMemoryPersistenceRelay(SynapticRelay<RecallSignal> r) { this.constructiveMemoryPersistenceRelay = r; return this; }
        public Builder epistemicLearningRelay(SynapticRelay<RecallSignal> r) { this.epistemicLearningRelay = r; return this; }
        public Builder consolidationRelay(SynapticRelay<RecallSignal> r) { this.consolidationRelay = r; return this; }

        public RecallRecipe build() {
            return new RecallRecipe(this);
        }
    }
}
