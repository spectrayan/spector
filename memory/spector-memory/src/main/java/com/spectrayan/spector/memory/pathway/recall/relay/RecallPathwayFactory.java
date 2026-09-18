/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.spectrayan.spector.memory.pathway.recall.relay;

import com.spectrayan.spector.commons.pathway.PathwayEngine;
import com.spectrayan.spector.commons.pathway.ConsolidationRelay;
import com.spectrayan.spector.commons.pathway.ErrorPolicy;
import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.memory.pathway.RelayNames;

import java.util.function.Function;

/**
 * Factory for creating the recall cognitive pathway with integrated Active Inference Self-Model Engine (AISME) relays.
 *
 * @deprecated Use {@link RecallRecipe} with {@link com.spectrayan.spector.commons.pathway.PathwayComposer} instead.
 */
@Deprecated(forRemoval = true, since = "1.5.0")
public final class RecallPathwayFactory {

    private RecallPathwayFactory() {}

    /**
     * Legacy factory overload without AISME relays (for backward compatibility).
     *
     * @deprecated Use {@link RecallRecipe} instead.
     */
    @Deprecated(forRemoval = true, since = "1.5.0")
    public static PathwayEngine<RecallSignal> create(
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
    public static PathwayEngine<RecallSignal> create(
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
    public static PathwayEngine<RecallSignal> create(
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
    public static PathwayEngine<RecallSignal> create(
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
    public static PathwayEngine<RecallSignal> create(
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
        return create(interceptor, transductionRelay, prospectiveRelay, governedReleaseGateRelay,
                homeostaticBiasRelay, vectorSearchRelay, freeEnergyGuidedRelay, null, scoringRelay,
                graphExpansionRelay, hopfieldAssociativeRelay, evidenceFusionRelay, lateralInhibitionRelay,
                bm25SearchRelay, rrfRescoreRelay, manifoldRerankRelay, constructiveSimulationRelay,
                consciousnessContinuityRelay, sortAndTruncateRelay, cognitiveRerankRelay,
                mmrDiversityRelay, temperatureSoftmaxRelay, consciousAccessRelay,
                constructiveMemoryPersistenceRelay, epistemicLearningRelay, consolidationRelay);
    }

    /**
     * Creates the full cognitive recall pathway with custom spacetime scoring relay injection.
     */
    public static PathwayEngine<RecallSignal> create(
            final Function<SynapticRelay<RecallSignal>, SynapticRelay<RecallSignal>> interceptor,
            final SynapticRelay<RecallSignal> transductionRelay,
            final SynapticRelay<RecallSignal> prospectiveRelay,
            final SynapticRelay<RecallSignal> governedReleaseGateRelay,
            final SynapticRelay<RecallSignal> homeostaticBiasRelay,
            final SynapticRelay<RecallSignal> vectorSearchRelay,
            final SynapticRelay<RecallSignal> freeEnergyGuidedRelay,
            final SynapticRelay<RecallSignal> spacetimeScoringRelay,
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

        final var composer = com.spectrayan.spector.commons.pathway.PathwayComposer.<RecallSignal>of("recall");
        if (interceptor != null) {
            composer.withInterceptor(interceptor);
        }

        RecallRecipe.builder()
                .transductionRelay(transductionRelay)
                .prospectiveRelay(prospectiveRelay)
                .governedReleaseGateRelay(governedReleaseGateRelay)
                .homeostaticBiasRelay(homeostaticBiasRelay)
                .vectorSearchRelay(vectorSearchRelay)
                .freeEnergyGuidedRelay(freeEnergyGuidedRelay)
                .spacetimeScoringRelay(spacetimeScoringRelay)
                .scoringRelay(scoringRelay)
                .graphExpansionRelay(graphExpansionRelay)
                .hopfieldAssociativeRelay(hopfieldAssociativeRelay)
                .evidenceFusionRelay(evidenceFusionRelay)
                .lateralInhibitionRelay(lateralInhibitionRelay)
                .bm25SearchRelay(bm25SearchRelay)
                .rrfRescoreRelay(rrfRescoreRelay)
                .manifoldRerankRelay(manifoldRerankRelay)
                .constructiveSimulationRelay(constructiveSimulationRelay)
                .consciousnessContinuityRelay(consciousnessContinuityRelay)
                .sortAndTruncateRelay(sortAndTruncateRelay)
                .cognitiveRerankRelay(cognitiveRerankRelay)
                .mmrDiversityRelay(mmrDiversityRelay)
                .temperatureSoftmaxRelay(temperatureSoftmaxRelay)
                .consciousAccessRelay(consciousAccessRelay)
                .constructiveMemoryPersistenceRelay(constructiveMemoryPersistenceRelay)
                .epistemicLearningRelay(epistemicLearningRelay)
                .consolidationRelay(consolidationRelay)
                .build()
                .compose(composer);

        return composer.build();
    }
}
