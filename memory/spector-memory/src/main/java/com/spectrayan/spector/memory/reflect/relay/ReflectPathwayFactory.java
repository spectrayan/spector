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
package com.spectrayan.spector.memory.reflect.relay;

import com.spectrayan.spector.commons.pathway.CognitivePathway;
import com.spectrayan.spector.commons.pathway.ErrorPolicy;
import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.memory.pathway.RelayNames;

import java.util.function.Function;

/**
 * Factory for creating the biological sleep consolidation (Reflect) cognitive pathway.
 */
public final class ReflectPathwayFactory {

    private ReflectPathwayFactory() {}

    /**
     * Legacy factory without manifold consolidation.
     */
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
                interferenceRelay, hebbianRelay, temporalRelay, promotionRelay, entityRelay, null, walRelay, null);
    }

    /**
     * Creates the standard reflect cognitive pathway with optional manifold consolidation.
     */
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
                interferenceRelay, hebbianRelay, temporalRelay, promotionRelay, entityRelay, manifoldConsolidationRelay, walRelay, null);
    }

    /**
     * Creates the reflect cognitive pathway with an optional stage interceptor/decorator.
     */
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
            final SynapticRelay<ReflectSignal> manifoldConsolidationRelay,
            final WalJournalRelay walRelay,
            final IdiolectLearningRelay idiolectRelay) {

        final var builder = CognitivePathway.<ReflectSignal>pathway("reflect");
        if (interceptor != null) {
            builder.withInterceptor(interceptor);
        }
        builder.relay(RelayNames.SYNAPTIC_PRUNING, pruningRelay, ErrorPolicy.FAIL_FAST)
                .relay(RelayNames.EPISODIC_CONSOLIDATION, logConsolidationRelay, ErrorPolicy.DEGRADE_GRACEFULLY)
                .relay(RelayNames.SOUL_DRIFT_REFUSION, soulDriftRelay, ErrorPolicy.DEGRADE_GRACEFULLY)
                .relay(RelayNames.PROCEDURAL_CRYSTALLIZATION, proceduralRelay, ErrorPolicy.DEGRADE_GRACEFULLY)
                .relay(RelayNames.PROACTIVE_INTERFERENCE, interferenceRelay, ErrorPolicy.DEGRADE_GRACEFULLY)
                .relay(RelayNames.HEBBIAN_HOMEOSTASIS, hebbianRelay, ErrorPolicy.DEGRADE_GRACEFULLY)
                .relay(RelayNames.TEMPORAL_PRUNING, temporalRelay, ErrorPolicy.DEGRADE_GRACEFULLY)
                .relay(RelayNames.CROSS_LAYER_PROMOTION, promotionRelay, ErrorPolicy.DEGRADE_GRACEFULLY)
                .relay(RelayNames.ENTITY_MAINTENANCE, entityRelay, ErrorPolicy.DEGRADE_GRACEFULLY);

        if (manifoldConsolidationRelay != null) {
            builder.relay(RelayNames.MANIFOLD_CONSOLIDATION, manifoldConsolidationRelay, ErrorPolicy.DEGRADE_GRACEFULLY);
        }

        if (idiolectRelay != null) {
            builder.relay("idiolectLearning", idiolectRelay, ErrorPolicy.DEGRADE_GRACEFULLY);
        }

        builder.relay(RelayNames.WAL_JOURNAL, walRelay, ErrorPolicy.FAIL_FAST);
        return builder.build();
    }
}
