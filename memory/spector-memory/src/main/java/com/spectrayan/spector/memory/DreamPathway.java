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
import com.spectrayan.spector.memory.kernel.shape.DistributedMemoryTensor;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.function.Function;

/**
 * 7th Canonical Cognitive Pathway in Spector Memory orchestrating Dreaming,
 * Thought Experimentation, and Creative Imagination.
 *
 * <h3>Biological Analog: Offline Consolidation and Generative Recombination</h3>
 * <p>Executes continuous dream cycles during offline/sleep states, mind-wandering
 * daydreams during extended idle periods, and deliberate counterfactual thought experiments
 * for prospective decision making.</p>
 *
 * @since 1.4.0
 */
public final class DreamPathway implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(DreamPathway.class);

    private final CognitivePathway<DreamSignal> pathway;
    private final DreamConfig dreamConfig;
    private final PartitionManager partitionManager;
    private final AismeConfig aismeConfig;
    private final HebbianGraphBase hebbianGraph;
    private final DistributedMemoryTensor distributedMemoryTensor;
    private final DreamJournalMemory dreamJournalMemory;
    private final EntityDirectory entityDirectory;
    private final HyperEntityGraphMemory hyperEntityGraph;
    private final EmbeddingProvider embeddingProvider;
    private final ContinuousHopfieldNetwork hopfieldNetwork;

    private DreamPathway(final Builder builder) {
        this.dreamConfig = builder.dreamConfig != null ? builder.dreamConfig : DreamConfig.defaultConfig();
        this.partitionManager = builder.partitionManager;
        this.aismeConfig = builder.aismeConfig;
        this.hebbianGraph = builder.hebbianGraph;
        this.distributedMemoryTensor = builder.distributedMemoryTensor;
        this.dreamJournalMemory = builder.dreamJournalMemory;
        this.entityDirectory = builder.entityDirectory;
        this.hyperEntityGraph = builder.hyperEntityGraph;
        this.embeddingProvider = builder.embeddingProvider;
        this.hopfieldNetwork = builder.hopfieldNetwork;

        var pathwayBuilder = CognitivePathway.<DreamSignal>pathway("dream_pathway");
        if (builder.interceptor != null) {
            pathwayBuilder.withInterceptor(builder.interceptor);
        }

        // 1. Dream Gate (circadian & sleep pressure check)
        pathwayBuilder.gated("dream_gate", DreamGates.DREAMING_ENABLED, new DreamGateRelay(), ErrorPolicy.DEGRADE_GRACEFULLY);

        // 2. Salient Seed Selection (TMR)
        pathwayBuilder.gated("salient_seed", DreamGates.DREAMING_ENABLED, new SalientSeedRelay(), ErrorPolicy.DEGRADE_GRACEFULLY);

        // 3. Fragment Unpack (entity/role/affect decomposition)
        pathwayBuilder.gated("fragment_unpack", DreamGates.HAS_SEEDS, new FragmentUnpackRelay(), ErrorPolicy.DEGRADE_GRACEFULLY);

        // 4. Hyper-Associate (anti-centroid pairing)
        pathwayBuilder.gated("hyper_associate", DreamGates.HAS_FRAGMENTS, new HyperAssociateRelay(), ErrorPolicy.DEGRADE_GRACEFULLY);

        // 5. REM Replay (Hoel noise injection)
        pathwayBuilder.gated("rem_replay", DreamGates.HAS_SEEDS, new RemReplayRelay(), ErrorPolicy.DEGRADE_GRACEFULLY);

        // 6. Scene Construct (compositional scene graph generation)
        pathwayBuilder.gated("scene_construct", DreamGates.HAS_SEEDS, new SceneConstructRelay(), ErrorPolicy.DEGRADE_GRACEFULLY);

        // 7. Counterfactual Probe (predict-and-verify against world model)
        pathwayBuilder.gated("counterfactual_probe", DreamGates.HAS_CONSTRUCTED_SCENES, new CounterfactualProbeRelay(), ErrorPolicy.DEGRADE_GRACEFULLY);

        // 8. Langevin Discovery (interstitial concept mining over holographic tensor)
        pathwayBuilder.gated("langevin_discovery", DreamGates.LANGEVIN_ENABLED, new LangevinDiscoveryRelay(), ErrorPolicy.DEGRADE_GRACEFULLY);

        // 9. EFE Triage (4-outcome expected free energy triage)
        pathwayBuilder.gated("efe_triage", DreamGates.HAS_CONSTRUCTED_SCENES, new EfeTriageRelay(), ErrorPolicy.DEGRADE_GRACEFULLY);

        // 10. Concept Extract (persist residue, extract insight)
        pathwayBuilder.gated("concept_extract", DreamGates.DREAMING_ENABLED, new ConceptExtractRelay(), ErrorPolicy.DEGRADE_GRACEFULLY);

        // 11. Dream Journal (audit trail persistence)
        pathwayBuilder.gated("dream_journal", DreamGates.JOURNAL_ENABLED, new DreamJournalRelay(), ErrorPolicy.DEGRADE_GRACEFULLY);

        // 12. Dream Ingestion (persist insights with FLAG_DREAMED + Hebbian inhibition)
        pathwayBuilder.gated("dream_ingestion", DreamGates.DREAMING_ENABLED, new DreamIngestionRelay(), ErrorPolicy.DEGRADE_GRACEFULLY);

        this.pathway = pathwayBuilder.build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public DreamConfig config() {
        return dreamConfig;
    }

    /**
     * Conducts a full dreaming cycle over the supplied signal.
     *
     * @param signal the dream execution signal
     * @return resulting {@link DreamReport}
     */
    public DreamReport conduct(final DreamSignal signal) {
        Objects.requireNonNull(signal, "DreamSignal cannot be null");
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
     * Convenience method to execute a dream cycle.
     */
    public DreamReport dream(DreamMode mode, PartitionManager pm, AismeConfig aismeConfig) {
        DreamSignal signal = DreamSignal.builder()
                .mode(mode)
                .config(dreamConfig)
                .partitionManager(pm != null ? pm : partitionManager)
                .aismeConfig(aismeConfig != null ? aismeConfig : this.aismeConfig)
                .hebbianGraph(hebbianGraph)
                .distributedMemoryTensor(distributedMemoryTensor)
                .dreamJournalMemory(dreamJournalMemory)
                .entityDirectory(entityDirectory)
                .hyperEntityGraph(hyperEntityGraph)
                .embeddingProvider(embeddingProvider)
                .hopfieldNetwork(hopfieldNetwork)
                .build();

        return conduct(signal);
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
        private HebbianGraphBase hebbianGraph;
        private DistributedMemoryTensor distributedMemoryTensor;
        private DreamJournalMemory dreamJournalMemory;
        private EntityDirectory entityDirectory;
        private HyperEntityGraphMemory hyperEntityGraph;
        private EmbeddingProvider embeddingProvider;
        private ContinuousHopfieldNetwork hopfieldNetwork;
        private Function<SynapticRelay<DreamSignal>, SynapticRelay<DreamSignal>> interceptor;

        public Builder dreamConfig(DreamConfig dc) { this.dreamConfig = dc; return this; }
        public Builder partitionManager(PartitionManager pm) { this.partitionManager = pm; return this; }
        public Builder aismeConfig(AismeConfig ac) { this.aismeConfig = ac; return this; }
        public Builder hebbianGraph(HebbianGraphBase graph) { this.hebbianGraph = graph; return this; }
        public Builder distributedMemoryTensor(DistributedMemoryTensor dmt) { this.distributedMemoryTensor = dmt; return this; }
        public Builder dreamJournalMemory(DreamJournalMemory djm) { this.dreamJournalMemory = djm; return this; }
        public Builder entityDirectory(EntityDirectory ed) { this.entityDirectory = ed; return this; }
        public Builder hyperEntityGraph(HyperEntityGraphMemory heg) { this.hyperEntityGraph = heg; return this; }
        public Builder embeddingProvider(EmbeddingProvider ep) { this.embeddingProvider = ep; return this; }
        public Builder hopfieldNetwork(ContinuousHopfieldNetwork hn) { this.hopfieldNetwork = hn; return this; }
        public Builder interceptor(Function<SynapticRelay<DreamSignal>, SynapticRelay<DreamSignal>> inc) { this.interceptor = inc; return this; }

        public DreamPathway build() {
            return new DreamPathway(this);
        }
    }
}
