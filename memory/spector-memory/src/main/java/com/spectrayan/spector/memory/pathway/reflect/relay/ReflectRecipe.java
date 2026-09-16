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

import com.spectrayan.spector.commons.pathway.ErrorPolicy;
import com.spectrayan.spector.commons.pathway.PathwayComposer;
import com.spectrayan.spector.commons.pathway.PathwayRecipe;
import com.spectrayan.spector.commons.pathway.Specification;
import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.memory.pathway.PathwayResilience;
import com.spectrayan.spector.memory.pathway.RelayNames;

import java.util.Objects;

/**
 * Declarative recipe for composing the biological sleep consolidation (Reflect) cognitive pathway
 * (ADR-0035 §7.3, §7.4).
 *
 * <p>Replaces positional factory overloads in {@link ReflectPathwayFactory}. Converts the former
 * {@code companion} lambda into a formal {@link Specification} gate so skips become observable
 * on {@link com.spectrayan.spector.commons.pathway.ConductionOutcome}.</p>
 */
public final class ReflectRecipe implements PathwayRecipe<ReflectSignal> {

    /**
     * Observable gate specification for companion relays governed by {@link com.spectrayan.spector.memory.model.SweepSpec}.
     */
    public static final Specification<ReflectSignal> COMPANION_RELAYS_ENABLED =
            Specification.of("companion relays disabled by sweep specification",
                    signal -> signal.sweepSpec() == null || signal.sweepSpec().runCompanionRelays());

    private final SynapticPruningRelay pruningRelay;
    private final EpisodicLogConsolidationRelay logConsolidationRelay;
    private final SoulDriftRefusionRelay soulDriftRelay;
    private final ProceduralCrystallizationRelay proceduralRelay;
    private final ProactiveInterferenceRelay interferenceRelay;
    private final HebbianHomeostasisRelay hebbianRelay;
    private final TemporalPruningRelay temporalRelay;
    private final CrossLayerPromotionRelay promotionRelay;
    private final EntityMaintenanceRelay entityRelay;
    private final SpectralSparsificationRelay sparsificationRelay;
    private final SynapticRelay<ReflectSignal> manifoldConsolidationRelay;
    private final SynapticRelay<ReflectSignal> softIdentityAnchorRelay;
    private final WalJournalRelay walRelay;
    private final IdiolectLearningRelay idiolectRelay;

    private ReflectRecipe(final Builder builder) {
        this.pruningRelay = Objects.requireNonNull(builder.pruningRelay, "pruningRelay cannot be null");
        this.logConsolidationRelay = Objects.requireNonNull(builder.logConsolidationRelay, "logConsolidationRelay cannot be null");
        this.soulDriftRelay = Objects.requireNonNull(builder.soulDriftRelay, "soulDriftRelay cannot be null");
        this.proceduralRelay = Objects.requireNonNull(builder.proceduralRelay, "proceduralRelay cannot be null");
        this.interferenceRelay = Objects.requireNonNull(builder.interferenceRelay, "interferenceRelay cannot be null");
        this.hebbianRelay = Objects.requireNonNull(builder.hebbianRelay, "hebbianRelay cannot be null");
        this.temporalRelay = Objects.requireNonNull(builder.temporalRelay, "temporalRelay cannot be null");
        this.promotionRelay = Objects.requireNonNull(builder.promotionRelay, "promotionRelay cannot be null");
        this.entityRelay = Objects.requireNonNull(builder.entityRelay, "entityRelay cannot be null");
        this.sparsificationRelay = builder.sparsificationRelay;
        this.manifoldConsolidationRelay = builder.manifoldConsolidationRelay;
        this.softIdentityAnchorRelay = builder.softIdentityAnchorRelay;
        this.walRelay = Objects.requireNonNull(builder.walRelay, "walRelay cannot be null");
        this.idiolectRelay = builder.idiolectRelay;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public void compose(final PathwayComposer<ReflectSignal> composer) {
        composer.gated(RelayNames.SYNAPTIC_PRUNING, COMPANION_RELAYS_ENABLED, pruningRelay, ErrorPolicy.FAIL_FAST);

        // Episodic consolidation ingests gists through the nested Remember pathway.
        // Admission control only — breaker + bulkhead, no timeout (ADR-0036 §7.3, §14).
        // Shares the pathway:remember breaker and bulkhead with Dream, so a sick Remember
        // throttles sleep and reflection together instead of each discovering it alone.
        composer.stage(RelayNames.EPISODIC_CONSOLIDATION)
                .relay(logConsolidationRelay)
                .policy(ErrorPolicy.DEGRADE_GRACEFULLY)
                .breaker(PathwayResilience.nestedRemember())
                .bulkhead(PathwayResilience.PATHWAY_REMEMBER,
                        PathwayResilience.nestedRememberBulkhead())
                .add();

        composer.gated(RelayNames.SOUL_DRIFT_REFUSION, COMPANION_RELAYS_ENABLED, soulDriftRelay, ErrorPolicy.DEGRADE_GRACEFULLY);

        // Procedural crystallization also writes via nested Remember.
        composer.stage(RelayNames.PROCEDURAL_CRYSTALLIZATION)
                .relay(proceduralRelay)
                .gate(COMPANION_RELAYS_ENABLED)
                .policy(ErrorPolicy.DEGRADE_GRACEFULLY)
                .breaker(PathwayResilience.nestedRemember())
                .bulkhead(PathwayResilience.PATHWAY_REMEMBER,
                        PathwayResilience.nestedRememberBulkhead())
                .add();

        composer.gated(RelayNames.PROACTIVE_INTERFERENCE, COMPANION_RELAYS_ENABLED, interferenceRelay, ErrorPolicy.DEGRADE_GRACEFULLY)
                .gated(RelayNames.HEBBIAN_HOMEOSTASIS, COMPANION_RELAYS_ENABLED, hebbianRelay, ErrorPolicy.DEGRADE_GRACEFULLY)
                .gated(RelayNames.TEMPORAL_PRUNING, COMPANION_RELAYS_ENABLED, temporalRelay, ErrorPolicy.DEGRADE_GRACEFULLY)
                .gated(RelayNames.CROSS_LAYER_PROMOTION, COMPANION_RELAYS_ENABLED, promotionRelay, ErrorPolicy.DEGRADE_GRACEFULLY)
                .gated(RelayNames.ENTITY_MAINTENANCE, COMPANION_RELAYS_ENABLED, entityRelay, ErrorPolicy.DEGRADE_GRACEFULLY);

        if (sparsificationRelay != null) {
            composer.gated(RelayNames.SPECTRAL_SPARSIFICATION, COMPANION_RELAYS_ENABLED, sparsificationRelay, ErrorPolicy.DEGRADE_GRACEFULLY);
        }

        if (manifoldConsolidationRelay != null) {
            composer.gated(RelayNames.MANIFOLD_CONSOLIDATION, COMPANION_RELAYS_ENABLED, manifoldConsolidationRelay, ErrorPolicy.DEGRADE_GRACEFULLY);
        }

        if (softIdentityAnchorRelay != null) {
            composer.gated(RelayNames.SOFT_IDENTITY_ANCHOR, COMPANION_RELAYS_ENABLED, softIdentityAnchorRelay, ErrorPolicy.DEGRADE_GRACEFULLY);
        }

        if (idiolectRelay != null) {
            composer.gated("idiolectLearning", COMPANION_RELAYS_ENABLED, idiolectRelay, ErrorPolicy.DEGRADE_GRACEFULLY);
        }

        composer.relay(RelayNames.WAL_JOURNAL, walRelay, ErrorPolicy.FAIL_FAST);
    }

    public static final class Builder {
        private SynapticPruningRelay pruningRelay;
        private EpisodicLogConsolidationRelay logConsolidationRelay;
        private SoulDriftRefusionRelay soulDriftRelay;
        private ProceduralCrystallizationRelay proceduralRelay;
        private ProactiveInterferenceRelay interferenceRelay;
        private HebbianHomeostasisRelay hebbianRelay;
        private TemporalPruningRelay temporalRelay;
        private CrossLayerPromotionRelay promotionRelay;
        private EntityMaintenanceRelay entityRelay;
        private SpectralSparsificationRelay sparsificationRelay;
        private SynapticRelay<ReflectSignal> manifoldConsolidationRelay;
        private SynapticRelay<ReflectSignal> softIdentityAnchorRelay;
        private WalJournalRelay walRelay;
        private IdiolectLearningRelay idiolectRelay;

        public Builder pruningRelay(SynapticPruningRelay r) { this.pruningRelay = r; return this; }
        public Builder logConsolidationRelay(EpisodicLogConsolidationRelay r) { this.logConsolidationRelay = r; return this; }
        public Builder soulDriftRelay(SoulDriftRefusionRelay r) { this.soulDriftRelay = r; return this; }
        public Builder proceduralRelay(ProceduralCrystallizationRelay r) { this.proceduralRelay = r; return this; }
        public Builder interferenceRelay(ProactiveInterferenceRelay r) { this.interferenceRelay = r; return this; }
        public Builder hebbianRelay(HebbianHomeostasisRelay r) { this.hebbianRelay = r; return this; }
        public Builder temporalRelay(TemporalPruningRelay r) { this.temporalRelay = r; return this; }
        public Builder promotionRelay(CrossLayerPromotionRelay r) { this.promotionRelay = r; return this; }
        public Builder entityRelay(EntityMaintenanceRelay r) { this.entityRelay = r; return this; }
        public Builder sparsificationRelay(SpectralSparsificationRelay r) { this.sparsificationRelay = r; return this; }
        public Builder manifoldConsolidationRelay(SynapticRelay<ReflectSignal> r) { this.manifoldConsolidationRelay = r; return this; }
        public Builder softIdentityAnchorRelay(SynapticRelay<ReflectSignal> r) { this.softIdentityAnchorRelay = r; return this; }
        public Builder walRelay(WalJournalRelay r) { this.walRelay = r; return this; }
        public Builder idiolectRelay(IdiolectLearningRelay r) { this.idiolectRelay = r; return this; }

        public ReflectRecipe build() {
            return new ReflectRecipe(this);
        }
    }
}
