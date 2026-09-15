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
package com.spectrayan.spector.memory.pathway.wander;

import com.spectrayan.spector.config.properties.AismeProperties;
import com.spectrayan.spector.memory.aisme.fegr.MentalStateTracker;
import com.spectrayan.spector.memory.aisme.homeostasis.HomeostaticCore;
import com.spectrayan.spector.memory.aisme.hopfield.ContinuousHopfieldNetwork;
import com.spectrayan.spector.memory.aisme.manifold.CognitiveManifold;
import com.spectrayan.spector.kernel.store.ContinuityMemory;
import com.spectrayan.spector.kernel.store.HebbianGraphBase;
import com.spectrayan.spector.kernel.shape.Memory;
import com.spectrayan.spector.memory.persist.PartitionManager;
import com.spectrayan.spector.memory.pathway.RelayNames;
import com.spectrayan.spector.memory.pathway.simulation.relay.SpacetimeSeedRelay;
import com.spectrayan.spector.memory.pathway.wander.relay.AutobiographicalSamplingRelay;
import com.spectrayan.spector.memory.pathway.wander.relay.HebbianSynapticReinforcementRelay;
import com.spectrayan.spector.memory.pathway.wander.relay.HopfieldMindWanderingRelay;
import com.spectrayan.spector.memory.pathway.wander.relay.IdleGateRelay;
import com.spectrayan.spector.memory.pathway.wander.relay.LongitudinalContinuityRelay;
import com.spectrayan.spector.memory.pathway.wander.relay.ManifoldSynergyRelay;
import com.spectrayan.spector.memory.pathway.wander.relay.WanderGates;
import com.spectrayan.spector.memory.pathway.wander.relay.WanderReport;
import com.spectrayan.spector.memory.pathway.wander.relay.WanderSignal;

import com.spectrayan.spector.commons.pathway.AbstractPathway;
import com.spectrayan.spector.commons.pathway.CognitivePathway;
import com.spectrayan.spector.commons.pathway.DefaultPathwayContext;
import com.spectrayan.spector.commons.pathway.ErrorPolicy;
import com.spectrayan.spector.commons.pathway.GatedRelay;
import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.core.quantization.ScalarQuantizer;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.function.Function;

/**
 * 4th Canonical Cognitive Pathway in Spector Memory orchestrating Default Mode Network (DMN)
 * spontaneous mind-wandering and longitudinal consciousness continuity (\(\Phi_{CC}\)) tracking.
 *
 * <h3>Biological Analog: Default Mode Network Wakeful Rest Activation</h3>
 * <p>Executes continuous Hopfield attractor discovery across active memory stores during idle states
 * and captures multi-epoch identity metrics into {@link ContinuityMemory}.</p>
 *
 * @since 1.2.0
 */
public final class WanderPathway extends AbstractPathway<WanderSignal, WanderReport> implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(WanderPathway.class);

    private final ScalarQuantizer quantizer;
    private final EmbeddingProvider embeddingProvider;
    private final MentalStateTracker mentalStateTracker;
    private final CognitiveManifold cognitiveManifold;
    private final ContinuousHopfieldNetwork hopfieldNetwork;
    private final HebbianGraphBase hebbianGraph;
    private final HomeostaticCore homeostaticCore;
    private final ContinuityMemory continuityMemory;
    private final AismeProperties aismeConfig;
    private final float[] soulPriorPreference;

    private WanderPathway(final Builder builder) {
        super("wander", WanderSignal.class, WanderReport.class);
        this.quantizer = builder.quantizer;
        this.embeddingProvider = builder.embeddingProvider;
        this.mentalStateTracker = builder.mentalStateTracker;
        this.cognitiveManifold = builder.cognitiveManifold;
        this.hopfieldNetwork = builder.hopfieldNetwork;
        this.hebbianGraph = builder.hebbianGraph;
        this.homeostaticCore = builder.homeostaticCore;
        this.continuityMemory = builder.continuityMemory;
        this.aismeConfig = builder.aismeConfig;
        this.soulPriorPreference = builder.soulPriorPreference;

        var pathwayBuilder = CognitivePathway.<WanderSignal>pathway("wander_pathway");
        if (builder.interceptor != null) {
            pathwayBuilder.withInterceptor(builder.interceptor);
        }

        // 1. Idle Gate
        pathwayBuilder.gated("idle_gate", WanderGates.IS_IDLE, new IdleGateRelay(), ErrorPolicy.DEGRADE_GRACEFULLY);

        // 2. Autobiographical Sampling
        pathwayBuilder.gated("autobiographical_sampling", WanderGates.DMN_ENABLED, new AutobiographicalSamplingRelay(), ErrorPolicy.DEGRADE_GRACEFULLY);

        // 2b. Spacetime Shortlist Seed Selection (ADR-0031)
        pathwayBuilder.gated(RelayNames.SPACETIME_SEED, WanderGates.DMN_ENABLED, new SpacetimeSeedRelay.WanderSeedRelay(), ErrorPolicy.DEGRADE_GRACEFULLY);

        // 3. Hopfield Mind Wandering
        pathwayBuilder.gated("hopfield_mind_wandering", WanderGates.DMN_ENABLED, new HopfieldMindWanderingRelay(), ErrorPolicy.DEGRADE_GRACEFULLY);

        // 4. Manifold Synergy Evaluation
        pathwayBuilder.gated("manifold_synergy", WanderGates.MANIFOLD_ENABLED, new ManifoldSynergyRelay(), ErrorPolicy.DEGRADE_GRACEFULLY);

        // 5. Hebbian Synaptic Reinforcement
        pathwayBuilder.gated("hebbian_reinforcement", WanderGates.DMN_ENABLED, new HebbianSynapticReinforcementRelay(), ErrorPolicy.DEGRADE_GRACEFULLY);

        // 6. Longitudinal Continuity Snapshot
        pathwayBuilder.gated("longitudinal_continuity", WanderGates.CONTINUITY_ENABLED, new LongitudinalContinuityRelay(), ErrorPolicy.DEGRADE_GRACEFULLY);

        final CognitivePathway<WanderSignal> engine = pathwayBuilder.build();
        initEngine(engine);
    }

    public CognitivePathway<WanderSignal> pathway() {
        return engine();
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    protected WanderReport project(final WanderSignal signal) {
        final WanderReport report = signal.buildReport();
        if (log.isDebugEnabled()) {
            log.debug("WanderPathway: cycle complete in {}ms — sampled={}, associations={}, snapshotRecorded={}",
                    report.elapsed().toMillis(), report.memoriesSampled(), report.associationsFormed(), report.snapshotRecorded());
        }
        return report;
    }

    /**
     * Executes the wandering pathway for an explicit namespace kernel and populated signal.
     *
     * @param kernel the namespace kernel
     * @param signal the wander execution signal
     * @return resulting {@link WanderReport}
     */
    public WanderReport execute(final com.spectrayan.spector.kernel.api.NamespaceKernel kernel, final WanderSignal signal) {
        Objects.requireNonNull(signal, "WanderSignal cannot be null");
        if (kernel != null) {
            signal.kernel(kernel);
        }
        if (signal.context() == null) {
            final DefaultPathwayContext.Builder ctxBuilder = DefaultPathwayContext.builder();
            if (kernel != null) {
                ctxBuilder.namespaceId(kernel.namespaceId());
                ctxBuilder.bind(com.spectrayan.spector.kernel.api.NamespaceKernel.class, kernel);
            }
            signal.bind(ctxBuilder.build());
        }
        return conduct(signal);
    }

    /**
     * Executes a mind-wandering cycle for an explicit namespace kernel.
     */
    public WanderReport execute(final com.spectrayan.spector.kernel.api.NamespaceKernel kernel,
                                final PartitionManager partitionManager,
                                final long lastActivityTimestampMs) {
        WanderSignal signal = WanderSignal.builder()
                .kernel(kernel)
                .partitionManager(partitionManager)
                .quantizer(quantizer)
                .embeddingProvider(embeddingProvider)
                .mentalStateTracker(mentalStateTracker)
                .cognitiveManifold(cognitiveManifold)
                .hopfieldNetwork(hopfieldNetwork)
                .hebbianGraph(hebbianGraph)
                .homeostaticCore(homeostaticCore)
                .continuityMemory(continuityMemory)
                .aismeConfig(aismeConfig)
                .soulPriorPreference(soulPriorPreference)
                .lastActivityTimestampMs(lastActivityTimestampMs)
                .idleThresholdSeconds(aismeConfig != null ? aismeConfig.dmnIdleIntervalSeconds() : 60)
                .build();

        return conduct(signal);
    }

    /**
     * Convenience method to execute a mind-wandering cycle.
     */
    public WanderReport wander(final PartitionManager partitionManager, final long lastActivityTimestampMs) {
        return execute(null, partitionManager, lastActivityTimestampMs);
    }

    public ContinuityMemory continuityMemory() {
        return continuityMemory;
    }

    @Override
    public void close() {
        if (continuityMemory != null) {
            continuityMemory.close();
        }
    }

    /**
     * Builder for {@link WanderPathway}.
     */
    public static final class Builder {
        private ScalarQuantizer quantizer;
        private EmbeddingProvider embeddingProvider;
        private MentalStateTracker mentalStateTracker;
        private CognitiveManifold cognitiveManifold;
        private ContinuousHopfieldNetwork hopfieldNetwork;
        private HebbianGraphBase hebbianGraph;
        private HomeostaticCore homeostaticCore;
        private ContinuityMemory continuityMemory;
        private AismeProperties aismeConfig = AismeProperties.defaultConfig();
        private float[] soulPriorPreference;
        private Function<SynapticRelay<WanderSignal>, SynapticRelay<WanderSignal>> interceptor;

        public Builder quantizer(ScalarQuantizer q) { this.quantizer = q; return this; }
        public Builder embeddingProvider(EmbeddingProvider ep) { this.embeddingProvider = ep; return this; }
        public Builder mentalStateTracker(MentalStateTracker mst) { this.mentalStateTracker = mst; return this; }
        public Builder cognitiveManifold(CognitiveManifold cm) { this.cognitiveManifold = cm; return this; }
        public Builder hopfieldNetwork(ContinuousHopfieldNetwork chn) { this.hopfieldNetwork = chn; return this; }
        public Builder hebbianGraph(HebbianGraphBase hg) { this.hebbianGraph = hg; return this; }
        public Builder homeostaticCore(HomeostaticCore hc) { this.homeostaticCore = hc; return this; }
        public Builder continuityMemory(ContinuityMemory crm) { this.continuityMemory = crm; return this; }
        public Builder aismeConfig(AismeProperties cfg) { this.aismeConfig = cfg; return this; }
        public Builder soulPriorPreference(float[] prior) { this.soulPriorPreference = prior; return this; }
        public Builder interceptor(Function<SynapticRelay<WanderSignal>, SynapticRelay<WanderSignal>> inc) { this.interceptor = inc; return this; }

        public WanderPathway build() {
            return new WanderPathway(this);
        }
    }
}
