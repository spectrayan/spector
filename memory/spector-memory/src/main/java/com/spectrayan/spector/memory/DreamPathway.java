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
package com.spectrayan.spector.memory;

import com.spectrayan.spector.commons.pathway.CognitivePathway;
import com.spectrayan.spector.commons.pathway.ErrorPolicy;
import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.memory.aisme.config.AismeConfig;
import com.spectrayan.spector.memory.aisme.hopfield.ContinuousHopfieldNetwork;
import com.spectrayan.spector.memory.dream.DreamJournalMemory;
import com.spectrayan.spector.memory.dream.relay.ConceptExtractRelay;
import com.spectrayan.spector.memory.dream.relay.CounterfactualProbeRelay;
import com.spectrayan.spector.memory.dream.relay.DreamConfig;
import com.spectrayan.spector.memory.dream.relay.DreamGateRelay;
import com.spectrayan.spector.memory.dream.relay.DreamGates;
import com.spectrayan.spector.memory.dream.relay.DreamIngestionRelay;
import com.spectrayan.spector.memory.dream.relay.DreamJournalRelay;
import com.spectrayan.spector.memory.dream.relay.DreamMode;
import com.spectrayan.spector.memory.dream.relay.DreamReport;
import com.spectrayan.spector.memory.dream.relay.DreamSignal;
import com.spectrayan.spector.memory.dream.relay.EfeTriageRelay;
import com.spectrayan.spector.memory.dream.relay.FragmentUnpackRelay;
import com.spectrayan.spector.memory.dream.relay.HyperAssociateRelay;
import com.spectrayan.spector.memory.dream.relay.LangevinDiscoveryRelay;
import com.spectrayan.spector.memory.dream.relay.RemReplayRelay;
import com.spectrayan.spector.memory.dream.relay.SalientSeedRelay;
import com.spectrayan.spector.memory.dream.relay.SceneConstructRelay;
import com.spectrayan.spector.memory.graph.EntityDirectory;
import com.spectrayan.spector.memory.graph.HyperEntityGraphMemory;
import com.spectrayan.spector.memory.hebbian.HebbianGraphBase;
import com.spectrayan.spector.memory.id.MemoryIdGenerator;
import com.spectrayan.spector.memory.kernel.shape.DistributedMemoryTensor;
import com.spectrayan.spector.memory.model.SalienceProfile;
import com.spectrayan.spector.memory.model.SoulContext;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * The 7th canonical {@link CognitivePathway} in Spector — executing generative dreaming,
 * mind-wandering counterfactuals, and stochastic Langevin discovery over memory representations.
 *
 * <h3>Biological Analog: Offline REM Sleep Replay &amp; Waking Deliberate Imagination</h3>
 * <p>Implements active systems consolidation and regularizing generative replay through a 12-relay
 * synaptic pipeline, conditioned on the active soul identity and salience profile.</p>
 *
 * @since 1.4.0
 */
public final class DreamPathway implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(DreamPathway.class);

    private final CognitivePathway<DreamSignal> pathway;
    private final DreamConfig dreamConfig;
    private final PartitionManager partitionManager;
    private final AismeConfig aismeConfig;
    private final SoulContext primarySoul;
    private final List<SoulContext> soulContexts;
    private final SalienceProfile salienceProfile;
    private final HebbianGraphBase hebbianGraph;
    private final DistributedMemoryTensor distributedMemoryTensor;
    private final DreamJournalMemory dreamJournalMemory;
    private final EntityDirectory entityDirectory;
    private final HyperEntityGraphMemory hyperEntityGraph;
    private final EmbeddingProvider embeddingProvider;
    private final ContinuousHopfieldNetwork hopfieldNetwork;
    private final MemoryIdGenerator idGenerator;

    private DreamPathway(final Builder builder) {
        this.dreamConfig = builder.dreamConfig != null ? builder.dreamConfig : DreamConfig.defaultConfig();
        this.partitionManager = builder.partitionManager;
        this.aismeConfig = builder.aismeConfig;
        this.primarySoul = builder.primarySoul;
        this.soulContexts = builder.soulContexts != null ? List.copyOf(builder.soulContexts) : List.of();
        this.salienceProfile = builder.salienceProfile;
        this.hebbianGraph = builder.hebbianGraph;
        this.distributedMemoryTensor = builder.distributedMemoryTensor;
        this.dreamJournalMemory = builder.dreamJournalMemory;
        this.entityDirectory = builder.entityDirectory;
        this.hyperEntityGraph = builder.hyperEntityGraph;
        this.embeddingProvider = builder.embeddingProvider;
        this.hopfieldNetwork = builder.hopfieldNetwork;
        this.idGenerator = builder.idGenerator;

        var pathwayBuilder = CognitivePathway.<DreamSignal>pathway("dream_pathway");
        if (builder.interceptor != null) {
            pathwayBuilder.withInterceptor(builder.interceptor);
        }

        // 1. Dream Gate (circadian & sleep pressure check)
        pathwayBuilder.gated("dream_gate", DreamGates.DREAMING_ENABLED, new DreamGateRelay(), ErrorPolicy.DEGRADE_GRACEFULLY);

        // 2. Salient Seed Selection (TMR + Soul / Salience Matching)
        pathwayBuilder.gated("salient_seed", DreamGates.DREAMING_ENABLED, new SalientSeedRelay(), ErrorPolicy.DEGRADE_GRACEFULLY);

        // 2b. Spacetime Shortlist Seed Selection (ADR-0031)
        pathwayBuilder.gated(com.spectrayan.spector.memory.pathway.RelayNames.SPACETIME_SEED, DreamGates.DREAMING_ENABLED, new com.spectrayan.spector.memory.simulation.relay.SpacetimeSeedRelay.DreamSeedRelay(), ErrorPolicy.DEGRADE_GRACEFULLY);

        // 3. Fragment Unpack (entity/role/affect decomposition)
        pathwayBuilder.gated("fragment_unpack", DreamGates.HAS_SEEDS, new FragmentUnpackRelay(), ErrorPolicy.DEGRADE_GRACEFULLY);

        // 4. Anti-Centroid Hyper-Association
        pathwayBuilder.gated("hyper_associate", DreamGates.HAS_FRAGMENTS, new HyperAssociateRelay(), ErrorPolicy.DEGRADE_GRACEFULLY);

        // 5. REM Compressed Replay with Hartmann Boundary Modulated Hoel Noise
        pathwayBuilder.gated("rem_replay", DreamGates.HAS_SEEDS, new RemReplayRelay(), ErrorPolicy.DEGRADE_GRACEFULLY);

        // 6. Compositional Scene Construction
        pathwayBuilder.gated("scene_construct", DreamGates.HAS_SEEDS, new SceneConstructRelay(), ErrorPolicy.DEGRADE_GRACEFULLY);

        // 7. Predictive Coding Reality Testing & Counterfactual Probing
        pathwayBuilder.gated("counterfactual_probe", DreamGates.HAS_CONSTRUCTED_SCENES, new CounterfactualProbeRelay(), ErrorPolicy.DEGRADE_GRACEFULLY);

        // 8. Langevin Stochastic SDE Discovery with Soul Attractor Potential
        pathwayBuilder.gated("langevin_discovery", DreamGates.LANGEVIN_ENABLED, new LangevinDiscoveryRelay(), ErrorPolicy.DEGRADE_GRACEFULLY);

        // 9. Prefrontal Multi-Soul EFE Triage & Ethical Reality Testing
        pathwayBuilder.gated("efe_triage", DreamGates.HAS_CONSTRUCTED_SCENES, new EfeTriageRelay(), ErrorPolicy.DEGRADE_GRACEFULLY);

        // 10. Distill Residue, Discard Scaffold (Concept Extraction)
        pathwayBuilder.gated("concept_extract", DreamGates.DREAMING_ENABLED, new ConceptExtractRelay(), ErrorPolicy.DEGRADE_GRACEFULLY);

        // 11. Dream Journal Recording (Audit Trail)
        pathwayBuilder.gated("dream_journal", DreamGates.JOURNAL_ENABLED, new DreamJournalRelay(), ErrorPolicy.DEGRADE_GRACEFULLY);

        // 12. Ingestion & Hebbian Synaptic Inhibition
        pathwayBuilder.gated("dream_ingestion", DreamGates.DREAMING_ENABLED, new DreamIngestionRelay(), ErrorPolicy.DEGRADE_GRACEFULLY);

        this.pathway = pathwayBuilder.build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public DreamConfig config() {
        return dreamConfig;
    }

    public SoulContext primarySoul() {
        return primarySoul;
    }

    public List<SoulContext> soulContexts() {
        return soulContexts;
    }

    public SalienceProfile salienceProfile() {
        return salienceProfile;
    }

    /**
     * Conducts a {@link DreamSignal} through the full 12-relay pipeline.
     */
    public DreamReport conduct(final DreamSignal signal) {
        Objects.requireNonNull(signal, "signal cannot be null");
        if (log.isTraceEnabled()) {
            log.trace("DreamPathway: initiating dream cycle in {} mode...", signal.mode());
        }
        try {
            pathway.conduct(signal);
            DreamReport report = signal.buildReport();
            if (log.isDebugEnabled()) {
                log.debug("DreamPathway: cycle complete in {}ms — seeds={}, scenes={}, ingested={}, failed={}",
                        report.elapsed().toMillis(), report.seedsSampled(), report.scenesConstructed(),
                        report.insightsIngested(), report.failedPairsInhibited());
            }
            return report;
        } catch (Exception e) {
            log.error("DreamPathway: dream cycle aborted due to error: {}", e.getMessage(), e);
            throw new IllegalStateException("DreamPathway execution failed: " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to execute a dream cycle with soul contexts and salience profile.
     */
    public DreamReport dream(
            DreamMode mode,
            PartitionManager pm,
            AismeConfig aismeConfig,
            SoulContext primarySoul,
            List<SoulContext> soulContexts,
            SalienceProfile salienceProfile) {
        DreamSignal signal = DreamSignal.builder()
                .mode(mode)
                .config(dreamConfig)
                .partitionManager(pm != null ? pm : partitionManager)
                .aismeConfig(aismeConfig != null ? aismeConfig : this.aismeConfig)
                .primarySoul(primarySoul != null ? primarySoul : this.primarySoul)
                .soulContexts(soulContexts != null ? soulContexts : this.soulContexts)
                .salienceProfile(salienceProfile != null ? salienceProfile : this.salienceProfile)
                .hebbianGraph(hebbianGraph)
                .distributedMemoryTensor(distributedMemoryTensor)
                .dreamJournalMemory(dreamJournalMemory)
                .entityDirectory(entityDirectory)
                .hyperEntityGraph(hyperEntityGraph)
                .embeddingProvider(embeddingProvider)
                .hopfieldNetwork(hopfieldNetwork)
                .idGenerator(idGenerator)
                .build();

        return conduct(signal);
    }

    /**
     * Convenience method to execute a dream cycle using the instance defaults.
     */
    public DreamReport dream(DreamMode mode, PartitionManager pm, AismeConfig aismeConfig) {
        return dream(mode, pm, aismeConfig, primarySoul, soulContexts, salienceProfile);
    }

    @Override
    public void close() {
        // Optional cleanup
    }

    /**
     * Builder for {@link DreamPathway}.
     */
    public static final class Builder {
        private DreamConfig dreamConfig;
        private PartitionManager partitionManager;
        private AismeConfig aismeConfig = AismeConfig.defaultConfig();
        private SoulContext primarySoul;
        private List<SoulContext> soulContexts;
        private SalienceProfile salienceProfile;
        private HebbianGraphBase hebbianGraph;
        private DistributedMemoryTensor distributedMemoryTensor;
        private DreamJournalMemory dreamJournalMemory;
        private EntityDirectory entityDirectory;
        private HyperEntityGraphMemory hyperEntityGraph;
        private EmbeddingProvider embeddingProvider;
        private ContinuousHopfieldNetwork hopfieldNetwork;
        private MemoryIdGenerator idGenerator;
        private Function<SynapticRelay<DreamSignal>, SynapticRelay<DreamSignal>> interceptor;

        public Builder dreamConfig(DreamConfig dc) { this.dreamConfig = dc; return this; }
        public Builder partitionManager(PartitionManager pm) { this.partitionManager = pm; return this; }
        public Builder aismeConfig(AismeConfig ac) { this.aismeConfig = ac; return this; }
        public Builder primarySoul(SoulContext soul) { this.primarySoul = soul; return this; }
        public Builder soulContexts(List<SoulContext> soulContexts) { this.soulContexts = soulContexts; return this; }
        public Builder salienceProfile(SalienceProfile profile) { this.salienceProfile = profile; return this; }
        public Builder hebbianGraph(HebbianGraphBase graph) { this.hebbianGraph = graph; return this; }
        public Builder distributedMemoryTensor(DistributedMemoryTensor dmt) { this.distributedMemoryTensor = dmt; return this; }
        public Builder dreamJournalMemory(DreamJournalMemory djm) { this.dreamJournalMemory = djm; return this; }
        public Builder entityDirectory(EntityDirectory ed) { this.entityDirectory = ed; return this; }
        public Builder hyperEntityGraph(HyperEntityGraphMemory heg) { this.hyperEntityGraph = heg; return this; }
        public Builder embeddingProvider(EmbeddingProvider ep) { this.embeddingProvider = ep; return this; }
        public Builder hopfieldNetwork(ContinuousHopfieldNetwork hn) { this.hopfieldNetwork = hn; return this; }
        public Builder idGenerator(MemoryIdGenerator idGen) { this.idGenerator = idGen; return this; }
        public Builder interceptor(Function<SynapticRelay<DreamSignal>, SynapticRelay<DreamSignal>> inc) { this.interceptor = inc; return this; }

        public DreamPathway build() {
            return new DreamPathway(this);
        }
    }
}
