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
package com.spectrayan.spector.memory.recall.relay;

import com.spectrayan.spector.commons.pathway.CognitivePathway;
import com.spectrayan.spector.commons.pathway.ConsolidationRelay;
import com.spectrayan.spector.commons.pathway.ErrorPolicy;
import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.memory.pathway.RelayNames;

import java.util.function.Function;

/**
 * Factory for creating the recall cognitive pathway with integrated Active Inference Self-Model Engine (AISME) relays.
 */
public final class RecallPathwayFactory {

    private RecallPathwayFactory() {}

    /**
     * Legacy factory overload without AISME relays (for backward compatibility).
     */
    public static CognitivePathway<RecallSignal> create(
            final SynapticRelay<RecallSignal> transductionRelay,
            final SynapticRelay<RecallSignal> prospectiveRelay,
            final SynapticRelay<RecallSignal> governedReleaseGateRelay,
            final SynapticRelay<RecallSignal> vectorSearchRelay,
            final SynapticRelay<RecallSignal> scoringRelay,
            final SynapticRelay<RecallSignal> graphExpansionRelay,
            final SynapticRelay<RecallSignal> evidenceFusionRelay,
            final SynapticRelay<RecallSignal> bm25SearchRelay,
            final RrfRescoreRelay rrfRescoreRelay,
            final SortAndTruncateRelay sortAndTruncateRelay,
            final CognitiveRerankRelay cognitiveRerankRelay,
            final MmrDiversityRelay mmrDiversityRelay,
            final TemperatureSoftmaxRelay temperatureSoftmaxRelay,
            final ConsolidationRelay<RecallSignal> consolidationRelay) {
        return create(null, transductionRelay, prospectiveRelay, governedReleaseGateRelay, null,
                vectorSearchRelay, null, scoringRelay, graphExpansionRelay, null, evidenceFusionRelay,
                null, bm25SearchRelay, rrfRescoreRelay, null, null, null, sortAndTruncateRelay,
                cognitiveRerankRelay, mmrDiversityRelay, temperatureSoftmaxRelay, null, null, null, consolidationRelay);
    }

    /**
     * Legacy factory overload with interceptor but without AISME relays.
     */
    public static CognitivePathway<RecallSignal> create(
            final Function<SynapticRelay<RecallSignal>, SynapticRelay<RecallSignal>> interceptor,
            final SynapticRelay<RecallSignal> transductionRelay,
            final SynapticRelay<RecallSignal> prospectiveRelay,
            final SynapticRelay<RecallSignal> governedReleaseGateRelay,
            final SynapticRelay<RecallSignal> vectorSearchRelay,
            final SynapticRelay<RecallSignal> scoringRelay,
            final SynapticRelay<RecallSignal> graphExpansionRelay,
            final SynapticRelay<RecallSignal> evidenceFusionRelay,
            final SynapticRelay<RecallSignal> bm25SearchRelay,
            final RrfRescoreRelay rrfRescoreRelay,
            final SortAndTruncateRelay sortAndTruncateRelay,
            final CognitiveRerankRelay cognitiveRerankRelay,
            final MmrDiversityRelay mmrDiversityRelay,
            final TemperatureSoftmaxRelay temperatureSoftmaxRelay,
            final ConsolidationRelay<RecallSignal> consolidationRelay) {
        return create(interceptor, transductionRelay, prospectiveRelay, governedReleaseGateRelay, null,
                vectorSearchRelay, null, scoringRelay, graphExpansionRelay, null, evidenceFusionRelay,
                null, bm25SearchRelay, rrfRescoreRelay, null, null, null, sortAndTruncateRelay,
                cognitiveRerankRelay, mmrDiversityRelay, temperatureSoftmaxRelay, null, null, null, consolidationRelay);
    }

    /**
     * Creates the full cognitive recall pathway with all AISME active inference and neuromodulatory relays.
     */
    public static CognitivePathway<RecallSignal> create(
            final Function<SynapticRelay<RecallSignal>, SynapticRelay<RecallSignal>> interceptor,
            final SynapticRelay<RecallSignal> transductionRelay,
            final SynapticRelay<RecallSignal> prospectiveRelay,
            final SynapticRelay<RecallSignal> governedReleaseGateRelay,
            final SynapticRelay<RecallSignal> homeostaticBiasRelay,
            final SynapticRelay<RecallSignal> vectorSearchRelay,
            final SynapticRelay<RecallSignal> freeEnergyGuidedRelay,
            final SynapticRelay<RecallSignal> scoringRelay,
            final SynapticRelay<RecallSignal> graphExpansionRelay,
            final SynapticRelay<RecallSignal> hopfieldAssociativeRelay,
            final SynapticRelay<RecallSignal> evidenceFusionRelay,
            final SynapticRelay<RecallSignal> bm25SearchRelay,
            final RrfRescoreRelay rrfRescoreRelay,
            final SynapticRelay<RecallSignal> manifoldRerankRelay,
            final SynapticRelay<RecallSignal> constructiveSimulationRelay,
            final SynapticRelay<RecallSignal> consciousnessContinuityRelay,
            final SortAndTruncateRelay sortAndTruncateRelay,
            final CognitiveRerankRelay cognitiveRerankRelay,
            final MmrDiversityRelay mmrDiversityRelay,
            final TemperatureSoftmaxRelay temperatureSoftmaxRelay,
            final SynapticRelay<RecallSignal> consciousAccessRelay,
            final ConsolidationRelay<RecallSignal> consolidationRelay) {
        return create(interceptor, transductionRelay, prospectiveRelay, governedReleaseGateRelay,
                homeostaticBiasRelay, vectorSearchRelay, freeEnergyGuidedRelay, scoringRelay,
                graphExpansionRelay, hopfieldAssociativeRelay, evidenceFusionRelay, null, bm25SearchRelay,
                rrfRescoreRelay, manifoldRerankRelay, constructiveSimulationRelay,
                consciousnessContinuityRelay, sortAndTruncateRelay, cognitiveRerankRelay,
                mmrDiversityRelay, temperatureSoftmaxRelay, consciousAccessRelay, null, null, consolidationRelay);
    }
    /**
     * Legacy factory overload with all AISME relays but without lateral inhibition relay.
     */
    public static CognitivePathway<RecallSignal> create(
            final Function<SynapticRelay<RecallSignal>, SynapticRelay<RecallSignal>> interceptor,
            final SynapticRelay<RecallSignal> transductionRelay,
            final SynapticRelay<RecallSignal> prospectiveRelay,
            final SynapticRelay<RecallSignal> governedReleaseGateRelay,
            final SynapticRelay<RecallSignal> homeostaticBiasRelay,
            final SynapticRelay<RecallSignal> vectorSearchRelay,
            final SynapticRelay<RecallSignal> freeEnergyGuidedRelay,
            final SynapticRelay<RecallSignal> scoringRelay,
            final SynapticRelay<RecallSignal> graphExpansionRelay,
            final SynapticRelay<RecallSignal> hopfieldAssociativeRelay,
            final SynapticRelay<RecallSignal> evidenceFusionRelay,
            final SynapticRelay<RecallSignal> bm25SearchRelay,
            final RrfRescoreRelay rrfRescoreRelay,
            final SynapticRelay<RecallSignal> manifoldRerankRelay,
            final SynapticRelay<RecallSignal> constructiveSimulationRelay,
            final SynapticRelay<RecallSignal> consciousnessContinuityRelay,
            final SortAndTruncateRelay sortAndTruncateRelay,
            final CognitiveRerankRelay cognitiveRerankRelay,
            final MmrDiversityRelay mmrDiversityRelay,
            final TemperatureSoftmaxRelay temperatureSoftmaxRelay,
            final SynapticRelay<RecallSignal> consciousAccessRelay,
            final SynapticRelay<RecallSignal> constructiveMemoryPersistenceRelay,
            final SynapticRelay<RecallSignal> epistemicLearningRelay,
            final ConsolidationRelay<RecallSignal> consolidationRelay) {
        return create(interceptor, transductionRelay, prospectiveRelay, governedReleaseGateRelay,
                homeostaticBiasRelay, vectorSearchRelay, freeEnergyGuidedRelay, scoringRelay,
                graphExpansionRelay, hopfieldAssociativeRelay, evidenceFusionRelay, null,
                bm25SearchRelay, rrfRescoreRelay, manifoldRerankRelay, constructiveSimulationRelay,
                consciousnessContinuityRelay, sortAndTruncateRelay, cognitiveRerankRelay,
                mmrDiversityRelay, temperatureSoftmaxRelay, consciousAccessRelay,
                constructiveMemoryPersistenceRelay, epistemicLearningRelay, consolidationRelay);
    }

    /**
     * Creates the full cognitive recall pathway with all AISME active inference and epistemic learning relays.
     */
    public static CognitivePathway<RecallSignal> create(
            final Function<SynapticRelay<RecallSignal>, SynapticRelay<RecallSignal>> interceptor,
            final SynapticRelay<RecallSignal> transductionRelay,
            final SynapticRelay<RecallSignal> prospectiveRelay,
            final SynapticRelay<RecallSignal> governedReleaseGateRelay,
            final SynapticRelay<RecallSignal> homeostaticBiasRelay,
            final SynapticRelay<RecallSignal> vectorSearchRelay,
            final SynapticRelay<RecallSignal> freeEnergyGuidedRelay,
            final SynapticRelay<RecallSignal> scoringRelay,
            final SynapticRelay<RecallSignal> graphExpansionRelay,
            final SynapticRelay<RecallSignal> hopfieldAssociativeRelay,
            final SynapticRelay<RecallSignal> evidenceFusionRelay,
            final SynapticRelay<RecallSignal> lateralInhibitionRelay,
            final SynapticRelay<RecallSignal> bm25SearchRelay,
            final RrfRescoreRelay rrfRescoreRelay,
            final SynapticRelay<RecallSignal> manifoldRerankRelay,
            final SynapticRelay<RecallSignal> constructiveSimulationRelay,
            final SynapticRelay<RecallSignal> consciousnessContinuityRelay,
            final SortAndTruncateRelay sortAndTruncateRelay,
            final CognitiveRerankRelay cognitiveRerankRelay,
            final MmrDiversityRelay mmrDiversityRelay,
            final TemperatureSoftmaxRelay temperatureSoftmaxRelay,
            final SynapticRelay<RecallSignal> consciousAccessRelay,
            final SynapticRelay<RecallSignal> constructiveMemoryPersistenceRelay,
            final SynapticRelay<RecallSignal> epistemicLearningRelay,
            final ConsolidationRelay<RecallSignal> consolidationRelay) {

        final var builder = CognitivePathway.<RecallSignal>pathway("recall");
        if (interceptor != null) {
            builder.withInterceptor(interceptor);
        }

        builder.relay(RelayNames.TRANSDUCTION, transductionRelay)
               .relay(RelayNames.PROSPECTIVE, prospectiveRelay)
               .relay(RelayNames.GOVERNED_RELEASE_GATE, governedReleaseGateRelay)
               .relay(RelayNames.VECTOR_SEARCH, vectorSearchRelay);

        if (homeostaticBiasRelay != null) {
            builder.gated(RelayNames.HOMEOSTATIC_BIAS, RecallGates.HOMEOSTASIS_ENABLED, homeostaticBiasRelay, ErrorPolicy.DEGRADE_GRACEFULLY);
        }

        if (freeEnergyGuidedRelay != null) {
            builder.gated(RelayNames.FREE_ENERGY_GUIDED, RecallGates.FREE_ENERGY_ENABLED, freeEnergyGuidedRelay, ErrorPolicy.DEGRADE_GRACEFULLY);
        }

        builder.gated(RelayNames.SPACETIME_SCORING, RecallGates.SPACETIME_ENABLED, new SpacetimeScoringRelay(), ErrorPolicy.DEGRADE_GRACEFULLY)
               .relay(RelayNames.SCORING, scoringRelay)
               .relay(RelayNames.GRAPH_EXPANSION, graphExpansionRelay);

        if (hopfieldAssociativeRelay != null) {
            builder.gated(RelayNames.HOPFIELD_ASSOCIATIVE, RecallGates.HOPFIELD_ENABLED, hopfieldAssociativeRelay, ErrorPolicy.DEGRADE_GRACEFULLY);
        }

        builder.relay(RelayNames.EVIDENCE_FUSION, evidenceFusionRelay);

        if (lateralInhibitionRelay != null) {
            builder.gated(RelayNames.LATERAL_INHIBITION, RecallGates.LATERAL_INHIBITION_ENABLED, lateralInhibitionRelay, ErrorPolicy.DEGRADE_GRACEFULLY);
        }

        builder.gated(RelayNames.BM25_SEARCH, RecallGates.TEXT_SEARCH_ENABLED, bm25SearchRelay, ErrorPolicy.DEGRADE_GRACEFULLY)
               .gated(RelayNames.RRF_RESCORE, RecallGates.RRF_FUSED, rrfRescoreRelay, ErrorPolicy.DEGRADE_GRACEFULLY);

        if (manifoldRerankRelay != null) {
            builder.gated(RelayNames.MANIFOLD_RERANK, RecallGates.MANIFOLD_ENABLED, manifoldRerankRelay, ErrorPolicy.DEGRADE_GRACEFULLY);
        }

        if (constructiveSimulationRelay != null) {
            builder.gated(RelayNames.CONSTRUCTIVE_SIMULATION, RecallGates.CONSTRUCTIVE_SIMULATION_ENABLED, constructiveSimulationRelay, ErrorPolicy.DEGRADE_GRACEFULLY);
        }

        if (consciousnessContinuityRelay != null) {
            builder.gated(RelayNames.CONSCIOUSNESS_CONTINUITY, RecallGates.CONSCIOUSNESS_CONTINUITY_ENABLED, consciousnessContinuityRelay, ErrorPolicy.DEGRADE_GRACEFULLY);
        }

        if (sortAndTruncateRelay != null) {
            builder.relay(RelayNames.SORT_TRUNCATE, sortAndTruncateRelay);
        }

        if (cognitiveRerankRelay != null) {
            builder.circuitBreaker(RelayNames.COLBERT_RERANK,
                    new com.spectrayan.spector.commons.pathway.GatedRelay<>(
                            RelayNames.COLBERT_RERANK, RecallGates.RERANK_CONFIGURED, cognitiveRerankRelay),
                    5, 30_000L, ErrorPolicy.DEGRADE_GRACEFULLY);
        }

        if (mmrDiversityRelay != null) {
            builder.gated(RelayNames.MMR_RERANK, RecallGates.MMR_ENABLED, mmrDiversityRelay, ErrorPolicy.DEGRADE_GRACEFULLY);
        }

        if (temperatureSoftmaxRelay != null) {
            builder.relay(RelayNames.TEMPERATURE, temperatureSoftmaxRelay);
        }

        if (consciousAccessRelay != null) {
            builder.gated(RelayNames.CONSCIOUS_ACCESS, RecallGates.CONSCIOUS_ACCESS_ENABLED, consciousAccessRelay, ErrorPolicy.DEGRADE_GRACEFULLY);
        }

        if (constructiveMemoryPersistenceRelay != null) {
            builder.gated(RelayNames.CONSTRUCTIVE_PERSISTENCE, RecallGates.CONSTRUCTIVE_PERSISTENCE_ENABLED, constructiveMemoryPersistenceRelay, ErrorPolicy.DEGRADE_GRACEFULLY);
        }

        if (epistemicLearningRelay != null) {
            builder.gated(RelayNames.EPISTEMIC_LEARNING, RecallGates.EPISTEMIC_LEARNING_ENABLED, epistemicLearningRelay, ErrorPolicy.DEGRADE_GRACEFULLY);
        }

        if (consolidationRelay != null) {
            builder.relay(RelayNames.CONSOLIDATION, consolidationRelay, ErrorPolicy.DEGRADE_GRACEFULLY);
        }

        return builder.build();
    }
}
