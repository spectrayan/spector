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
package com.spectrayan.spector.memory.pathway.reflect.relay;

import com.spectrayan.spector.commons.pathway.CognitivePathway;
import com.spectrayan.spector.commons.pathway.ErrorPolicy;
import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.memory.pathway.RelayNames;

import com.spectrayan.spector.commons.pathway.PathwayComposer;

import java.util.function.Function;

/**
 * Factory for creating the biological sleep consolidation (Reflect) cognitive pathway.
 *
 * @deprecated Use {@link ReflectRecipe} with {@link PathwayComposer} instead.
 */
@Deprecated(forRemoval = true, since = "1.5.0")
public final class ReflectPathwayFactory {

    private ReflectPathwayFactory() {}

    /**
     * Legacy factory without manifold consolidation or sparsification.
     *
     * @deprecated Use {@link ReflectRecipe} instead.
     */
    @Deprecated(forRemoval = true, since = "1.5.0")
    public static CognitivePathway<ReflectSignal> create(
            final SynapticPruningRelay pruningRelay,
            final EpisodicLogConsolidationRelay logConsolidationRelay,
            final SoulDriftRefusionRelay soulDriftRelay,
            final ProceduralCrystallizationRelay proceduralRelay,
            final ProactiveInterferenceRelay interferenceRelay,
            final HebbianHomeostasisRelay hebbianRelay,
            final TemporalPruningRelay temporalRelay,
            final CrossLayerPromotionRelay promotionRelay,
            final EntityMaintenanceRelay entityRelay,
            final WalJournalRelay walRelay) {
        return create(null, pruningRelay, logConsolidationRelay, soulDriftRelay, proceduralRelay,
                interferenceRelay, hebbianRelay, temporalRelay, promotionRelay, entityRelay,
                null, null, null, walRelay, null);
    }

    /**
     * Creates the standard reflect cognitive pathway with optional manifold consolidation.
     *
     * @deprecated Use {@link ReflectRecipe} instead.
     */
    @Deprecated(forRemoval = true, since = "1.5.0")
    public static CognitivePathway<ReflectSignal> create(
            final SynapticPruningRelay pruningRelay,
            final EpisodicLogConsolidationRelay logConsolidationRelay,
            final SoulDriftRefusionRelay soulDriftRelay,
            final ProceduralCrystallizationRelay proceduralRelay,
            final ProactiveInterferenceRelay interferenceRelay,
            final HebbianHomeostasisRelay hebbianRelay,
            final TemporalPruningRelay temporalRelay,
            final CrossLayerPromotionRelay promotionRelay,
            final EntityMaintenanceRelay entityRelay,
            final SynapticRelay<ReflectSignal> manifoldConsolidationRelay,
            final WalJournalRelay walRelay) {
        return create(null, pruningRelay, logConsolidationRelay, soulDriftRelay, proceduralRelay,
                interferenceRelay, hebbianRelay, temporalRelay, promotionRelay, entityRelay,
                null, manifoldConsolidationRelay, null, walRelay, null);
    }

    /**
     * Creates the reflect cognitive pathway with an optional stage interceptor/decorator and soft identity anchor relay.
     *
     * @deprecated Use {@link ReflectRecipe} instead.
     */
    @Deprecated(forRemoval = true, since = "1.5.0")
    public static CognitivePathway<ReflectSignal> create(
            final Function<SynapticRelay<ReflectSignal>, SynapticRelay<ReflectSignal>> interceptor,
            final SynapticPruningRelay pruningRelay,
            final EpisodicLogConsolidationRelay logConsolidationRelay,
            final SoulDriftRefusionRelay soulDriftRelay,
            final ProceduralCrystallizationRelay proceduralRelay,
            final ProactiveInterferenceRelay interferenceRelay,
            final HebbianHomeostasisRelay hebbianRelay,
            final TemporalPruningRelay temporalRelay,
            final CrossLayerPromotionRelay promotionRelay,
            final EntityMaintenanceRelay entityRelay,
            final SpectralSparsificationRelay sparsificationRelay,
            final SynapticRelay<ReflectSignal> manifoldConsolidationRelay,
            final SynapticRelay<ReflectSignal> softIdentityAnchorRelay,
            final WalJournalRelay walRelay,
            final IdiolectLearningRelay idiolectRelay) {

        final var composer = PathwayComposer.<ReflectSignal>of("reflect");
        if (interceptor != null) {
            composer.withInterceptor(interceptor);
        }

        ReflectRecipe.builder()
                .pruningRelay(pruningRelay)
                .logConsolidationRelay(logConsolidationRelay)
                .soulDriftRelay(soulDriftRelay)
                .proceduralRelay(proceduralRelay)
                .interferenceRelay(interferenceRelay)
                .hebbianRelay(hebbianRelay)
                .temporalRelay(temporalRelay)
                .promotionRelay(promotionRelay)
                .entityRelay(entityRelay)
                .sparsificationRelay(sparsificationRelay)
                .manifoldConsolidationRelay(manifoldConsolidationRelay)
                .softIdentityAnchorRelay(softIdentityAnchorRelay)
                .walRelay(walRelay)
                .idiolectRelay(idiolectRelay)
                .build()
                .compose(composer);

        return composer.build();
    }
}
