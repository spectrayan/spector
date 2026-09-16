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
package com.spectrayan.spector.memory.pathway.dream;

import com.spectrayan.spector.kernel.id.MemoryId;

import com.spectrayan.spector.commons.pathway.PathwayEngine;
import com.spectrayan.spector.commons.pathway.PathwayComposer;
import com.spectrayan.spector.commons.pathway.DefaultPathwayComposer;
import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.commons.pathway.AbstractPathway;
import com.spectrayan.spector.commons.pathway.ConductionOutcome;
import com.spectrayan.spector.commons.pathway.DefaultPathwayContext;
import com.spectrayan.spector.config.properties.DreamProperties;
import com.spectrayan.spector.config.properties.AismeProperties;
import com.spectrayan.spector.memory.pathway.SoulVersionSource;
import com.spectrayan.spector.memory.pathway.remember.RememberPathway;
import com.spectrayan.spector.memory.aisme.hopfield.ContinuousHopfieldNetwork;
import com.spectrayan.spector.memory.graph.EntityDirectory;
import com.spectrayan.spector.kernel.store.HyperEntityGraphMemory;
import com.spectrayan.spector.kernel.store.HebbianGraphBase;
import com.spectrayan.spector.kernel.id.MemoryIdGenerator;
import com.spectrayan.spector.kernel.shape.DistributedMemoryTensor;
import com.spectrayan.spector.memory.model.SalienceProfile;
import com.spectrayan.spector.memory.model.SoulContext;
import com.spectrayan.spector.memory.pathway.dream.DreamJournalMemory;
import com.spectrayan.spector.memory.pathway.dream.relay.DreamRecipe;
import com.spectrayan.spector.kernel.api.DreamMode;
import com.spectrayan.spector.memory.pathway.dream.relay.DreamReport;
import com.spectrayan.spector.memory.pathway.dream.relay.DreamSignal;
import com.spectrayan.spector.memory.persist.PartitionManager;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.generation.LlmProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * The 7th canonical {@link PathwayEngine} in Spector — executing generative dreaming,
 * mind-wandering counterfactuals, and stochastic Langevin discovery over memory representations.
 *
 * <h3>Biological Analog: Offline REM Sleep Replay &amp; Waking Deliberate Imagination</h3>
 * <p>Implements active systems consolidation and regularizing generative replay through a 12-relay
 * synaptic pipeline, conditioned on the active soul identity and salience profile.</p>
 *
 * @since 1.4.0
 */
public final class DreamPathway extends AbstractPathway<DreamSignal, DreamReport> implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(DreamPathway.class);

    private final DreamProperties dreamProperties;
    private final PartitionManager partitionManager;
    /** Narrowed from RememberPathway to the one capability Dream actually needs (ADR-0035 §8.1b). */
    private final SoulVersionSource soulVersionSource;
    private final AismeProperties aismeConfig;
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
    private final LlmProvider llmProvider;
    private final MemoryIdGenerator idGenerator;

    private DreamPathway(final Builder builder) {
        super("dream", DreamSignal.class, DreamReport.class);
        this.dreamProperties = builder.dreamProperties != null ? builder.dreamProperties : new DreamProperties();
        this.partitionManager = builder.partitionManager;
        this.soulVersionSource = builder.soulVersionSource;
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
        this.llmProvider = builder.llmProvider;
        this.idGenerator = builder.idGenerator;

        // Composer rather than the raw builder so the LLM and nested-Remember stages can
        // carry resilience decorators (ADR-0036 §14 Dream row). The stage graph itself lives
        // in DreamRecipe so the parity gate can assert its shape.
        final PathwayComposer<DreamSignal> pathwayBuilder =
                new DefaultPathwayComposer<>("dream");
        if (builder.interceptor != null) {
            pathwayBuilder.withInterceptor(builder.interceptor);
        }
        new DreamRecipe().compose(pathwayBuilder);

        initEngine(pathwayBuilder.build());
    }

    public PathwayEngine<DreamSignal> pathway() {
        return engine();
    }

    public static Builder builder() {
        return new Builder();
    }

    public DreamProperties properties() {
        return dreamProperties;
    }

    public DreamProperties config() {
        return dreamProperties;
    }

    public SoulContext primarySoul() {
        return primarySoul;
    }

    public List<SoulContext> soulContexts() {
        return soulContexts;
    }

    /**
     * @deprecated Use {@code catalog.invoke(RememberPathway.class, ...)} via PathwayCatalog instead.
     *             Retained for backwards compatibility — will be removed in a future release.
     */
    @Deprecated(forRemoval = true, since = "1.5.0")
    /**
     * Returns the soul-version accessor bound into this pathway's conductions.
     *
     * @return soul version source, or {@code null} when none was supplied
     */
    public SoulVersionSource soulVersionSource() {
        return soulVersionSource;
    }

    public SalienceProfile salienceProfile() {
        return salienceProfile;
    }

    public LlmProvider llmProvider() {
        return llmProvider;
    }

    @Override
    protected DreamReport project(final DreamSignal signal) {
        ConductionOutcome outcome = signal.context() != null ? signal.context().outcome() : null;
        if (outcome != null && outcome.finish() == ConductionOutcome.Finish.SHORT_CIRCUITED) {
            return DreamReport.empty(outcome);
        }
        final DreamReport report = signal.buildReport();
        if (log.isDebugEnabled()) {
            log.debug("DreamPathway: cycle complete in {}ms — seeds={}, scenes={}, ingested={}, failed={}",
                    report.elapsed().toMillis(), report.seedsSampled(), report.scenesConstructed(),
                    report.insightsIngested(), report.failedPairsInhibited());
        }
        return report;
    }

    /**
     * Executes a dream cycle for an explicit namespace kernel and populated signal.
     *
     * @param kernel the namespace kernel
     * @param signal the dream signal
     * @return the dream report
     */
    public DreamReport execute(final com.spectrayan.spector.kernel.api.NamespaceKernel kernel, final DreamSignal signal) {
        Objects.requireNonNull(signal, "signal cannot be null");
        final DefaultPathwayContext.Builder ctxBuilder = signal.context() != null
                ? DefaultPathwayContext.from(signal.context())
                : DefaultPathwayContext.builder();
        if (kernel != null) {
            ctxBuilder.namespaceId(kernel.namespaceId());
            ctxBuilder.bindIfAbsent(com.spectrayan.spector.kernel.api.NamespaceKernel.class, kernel);
        }
        if (soulVersionSource != null) {
            ctxBuilder.bindIfAbsent(SoulVersionSource.class, soulVersionSource);
        }
        signal.bind(ctxBuilder.build());
        return conduct(signal);
    }

    /**
     * Executes a dream cycle for an explicit namespace kernel with soul contexts and salience profile.
     */
    public DreamReport execute(
            final com.spectrayan.spector.kernel.api.NamespaceKernel kernel,
            final DreamMode mode,
            final PartitionManager pm,
            final AismeProperties aismeConfig,
            final SoulContext primarySoul,
            final List<SoulContext> soulContexts,
            final SalienceProfile salienceProfile) {
        DreamSignal signal = DreamSignal.builder()
                .mode(mode)
                .config(dreamProperties)
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
                .llmProvider(llmProvider)
                .idGenerator(idGenerator)
                .build();

        return execute(kernel, signal);
    }

    /**
     * Convenience method to execute a dream cycle with soul contexts and salience profile.
     */
    public DreamReport dream(
            DreamMode mode,
            PartitionManager pm,
            AismeProperties aismeConfig,
            SoulContext primarySoul,
            List<SoulContext> soulContexts,
            SalienceProfile salienceProfile) {
        return execute(null, mode, pm, aismeConfig, primarySoul, soulContexts, salienceProfile);
    }

    /**
     * Convenience method to execute a dream cycle using the instance defaults.
     */
    public DreamReport dream(DreamMode mode, PartitionManager pm, AismeProperties aismeConfig) {
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
        private DreamProperties dreamProperties;
        private PartitionManager partitionManager;
        private SoulVersionSource soulVersionSource;
        private AismeProperties aismeConfig = AismeProperties.defaultConfig();
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
        private LlmProvider llmProvider;
        private MemoryIdGenerator idGenerator;
        private Function<SynapticRelay<DreamSignal>, SynapticRelay<DreamSignal>> interceptor;

        public Builder dreamProperties(DreamProperties dp) {
            this.dreamProperties = dp;
            return this;
        }

        public Builder dreamConfig(DreamProperties dp) {
            return dreamProperties(dp);
        }

        
        public Builder partitionManager(PartitionManager pm) { this.partitionManager = pm; return this; }
        /**
         * @deprecated Register RememberPathway in the PathwayCatalog instead.
         */
        /**
         * Supplies the soul-version accessor.
         *
         * @param svs soul version source (RememberPathway implements this)
         * @return this builder
         */
        public Builder soulVersionSource(SoulVersionSource svs) { this.soulVersionSource = svs; return this; }

        /**
         * @param rp the remember pathway, used only as a {@link SoulVersionSource}
         * @return this builder
         * @deprecated Pass {@link #soulVersionSource(SoulVersionSource)} instead. Dream needs
         *             only the soul version, not the whole pathway; nested writes go through
         *             the {@code PathwayCatalog}.
         */
        @Deprecated(forRemoval = true, since = "1.5.0")
        public Builder rememberPathway(RememberPathway rp) { this.soulVersionSource = rp; return this; }
        public Builder aismeConfig(AismeProperties ac) { this.aismeConfig = ac; return this; }
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
        public Builder llmProvider(LlmProvider llmProvider) { this.llmProvider = llmProvider; return this; }
        public Builder idGenerator(MemoryIdGenerator idGen) { this.idGenerator = idGen; return this; }
        public Builder interceptor(Function<SynapticRelay<DreamSignal>, SynapticRelay<DreamSignal>> inc) { this.interceptor = inc; return this; }

        public DreamPathway build() {
            return new DreamPathway(this);
        }
    }
}
