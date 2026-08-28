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
import com.spectrayan.spector.commons.template.TemplateEngine;
import com.spectrayan.spector.config.SpectorPropertyConstants;
import com.spectrayan.spector.core.quantization.ScalarQuantizer;
import com.spectrayan.spector.memory.aisme.manifold.CognitiveManifold;
import com.spectrayan.spector.memory.aisme.relay.SoftIdentityAnchorRelay;
import com.spectrayan.spector.memory.cortex.CentroidRouter;
import com.spectrayan.spector.memory.graph.EntityDirectory;
import com.spectrayan.spector.memory.graph.HyperEntityGraphMemory;
import com.spectrayan.spector.memory.graph.TypeNormalizer;
import com.spectrayan.spector.memory.hebbian.HebbianGraphBase;
import com.spectrayan.spector.memory.hippocampus.CircadianPolicy;
import com.spectrayan.spector.memory.index.MemoryIndex;
import com.spectrayan.spector.memory.model.ReflectReport;
import com.spectrayan.spector.memory.model.SalienceProfile;
import com.spectrayan.spector.memory.RememberPathway;
import com.spectrayan.spector.memory.reflect.relay.CrossLayerPromotionRelay;
import com.spectrayan.spector.memory.reflect.relay.EntityMaintenanceRelay;
import com.spectrayan.spector.memory.reflect.relay.EpisodicLogConsolidationRelay;
import com.spectrayan.spector.memory.reflect.relay.HebbianHomeostasisRelay;
import com.spectrayan.spector.memory.reflect.relay.ProactiveInterferenceRelay;
import com.spectrayan.spector.memory.reflect.relay.ProceduralCrystallizationRelay;
import com.spectrayan.spector.memory.reflect.relay.ReflectPathwayFactory;
import com.spectrayan.spector.memory.reflect.relay.ReflectSignal;
import com.spectrayan.spector.memory.reflect.relay.SoulDriftRefusionRelay;
import com.spectrayan.spector.memory.reflect.relay.SpectralSparsificationRelay;
import com.spectrayan.spector.memory.reflect.relay.SynapticPruningRelay;
import com.spectrayan.spector.memory.reflect.relay.TemporalPruningRelay;
import com.spectrayan.spector.memory.reflect.relay.WalJournalRelay;
import com.spectrayan.spector.memory.sync.MemoryWal;
import com.spectrayan.spector.memory.temporal.TemporalChainMemory;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.generation.LlmProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Orchestrates biological sleep consolidation (reflection) using the composable Cognitive Pathway Engine.
 *
 * <p>Consolidates NREM deep sleep downscaling, REM replay gist extraction, identity soul-drift
 * re-fusion (#503), proactive interference, Hebbian synaptic homeostasis, temporal pruning,
 * STC cross-layer promotion, Riemannian cognitive manifold consolidation, Soft Identity Anchor Lyapunov
 * restoring force, and entity maintenance into a unified, observable 14-relay pipeline.</p>
 */
public final class ReflectPathway implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ReflectPathway.class);

    private final CognitivePathway<ReflectSignal> pathway;
    private final ScalarQuantizer quantizer;
    private final EmbeddingProvider embeddingProvider;
    private final LlmProvider textGenerator;
    private final ImportanceProvider importanceProvider;
    private final CircadianPolicy policy;
    private final CentroidRouter centroidRouter;
    private final TemplateEngine templateEngine;

    private final HebbianGraphBase hebbianGraph;
    private final TemporalChainMemory temporalChain;
    private final EntityDirectory entityDirectory;
    private final HyperEntityGraphMemory hyperEntityGraph;
    private final MemoryWal wal;
    private final TypeNormalizer typeNormalizer;

    private final int minClusterSize;
    private final boolean pinSourceEpisodes;
    private final int pinnedQuota;
    private final boolean soulDriftRefusionEnabled;
    private final int soulDriftRefusionBatchSize;
    private final int temporalRetentionDays;
    private final boolean entityResolutionEnabled;
    private final boolean entityShadowMode;
    private final float entityCosineThreshold;
    private final com.spectrayan.spector.memory.aisme.fegr.MentalStateTracker mentalStateTracker;
    private final CognitiveManifold cognitiveManifold;
    private final boolean softIdentityAnchorEnabled;
    private final float identityAnchorEta;
    private final float identityLyapunovThreshold;

    private ReflectPathway(final Builder builder) {
        this.quantizer = builder.quantizer;
        this.embeddingProvider = builder.embeddingProvider;
        this.textGenerator = builder.textGenerator;
        this.importanceProvider = builder.importanceProvider != null ? builder.importanceProvider : ImportanceProvider.baseline();
        this.policy = builder.policy != null ? builder.policy : CircadianPolicy.DEFAULT;
        this.centroidRouter = builder.centroidRouter;
        this.templateEngine = builder.templateEngine != null ? builder.templateEngine : TemplateEngine.getDefault();

        this.hebbianGraph = builder.hebbianGraph;
        this.temporalChain = builder.temporalChain;
        this.entityDirectory = builder.entityDirectory;
        this.hyperEntityGraph = builder.hyperEntityGraph;
        this.wal = builder.wal;
        this.typeNormalizer = builder.typeNormalizer;

        this.minClusterSize = builder.minClusterSize;
        this.pinSourceEpisodes = builder.pinSourceEpisodes;
        this.pinnedQuota = builder.pinnedQuota;
        this.soulDriftRefusionEnabled = builder.soulDriftRefusionEnabled;
        this.soulDriftRefusionBatchSize = builder.soulDriftRefusionBatchSize;
        this.temporalRetentionDays = builder.temporalRetentionDays;
        this.entityResolutionEnabled = builder.entityResolutionEnabled;
        this.entityShadowMode = builder.entityShadowMode;
        this.entityCosineThreshold = builder.entityCosineThreshold;
        this.mentalStateTracker = builder.mentalStateTracker;
        this.cognitiveManifold = builder.cognitiveManifold;
        this.softIdentityAnchorEnabled = builder.softIdentityAnchorEnabled;
        this.identityAnchorEta = builder.identityAnchorEta;
        this.identityLyapunovThreshold = builder.identityLyapunovThreshold;

        final var manifoldRelay = builder.manifoldConsolidationRelay != null
                ? builder.manifoldConsolidationRelay
                : (builder.cognitiveManifold != null
                        ? new com.spectrayan.spector.memory.aisme.relay.ManifoldConsolidationRelay(
                                builder.cognitiveManifold, null, builder.coActivationSupplier)
                        : null);

        final var anchorRelay = builder.softIdentityAnchorRelay != null
                ? builder.softIdentityAnchorRelay
                : new SoftIdentityAnchorRelay(builder.identityAnchorEta, builder.identityLyapunovThreshold);

        this.pathway = ReflectPathwayFactory.create(
                builder.interceptor,
                new SynapticPruningRelay(),
                new EpisodicLogConsolidationRelay(),
                new SoulDriftRefusionRelay(),
                new ProceduralCrystallizationRelay(),
                new ProactiveInterferenceRelay(),
                new HebbianHomeostasisRelay(),
                new TemporalPruningRelay(),
                new CrossLayerPromotionRelay(),
                new EntityMaintenanceRelay(),
                new SpectralSparsificationRelay(),
                manifoldRelay,
                anchorRelay,
                new WalJournalRelay(),
                new com.spectrayan.spector.memory.reflect.relay.IdiolectLearningRelay()
        );
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Conducts a full biological sleep consolidation cycle over the supplied signal.
     *
     * @param signal the populated reflection signal
     * @return the resulting {@link ReflectReport}
     */
    public ReflectReport conduct(final ReflectSignal signal) {
        Objects.requireNonNull(signal, "ReflectSignal cannot be null");
        log.info("ReflectPathway: initiating sleep consolidation cycle...");
        try {
            pathway.conduct(signal);
            ReflectReport report = signal.buildReport();
            log.info("ReflectPathway: sleep cycle complete in {}ms — consolidated={}, tombstoned={}, compacted={}, soulRefused={}",
                    report.duration().toMillis(), report.consolidatedCount(), report.tombstonedCount(),
                    report.compactedPartitions(), report.soulRefusedCount());
            return report;
        } catch (Exception e) {
            log.error("ReflectPathway: reflection cycle aborted due to error: {}", e.getMessage(), e);
            throw new IllegalStateException("ReflectPathway execution failed: " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to execute a sleep reflection cycle.
     */
    public ReflectReport reflect(final PartitionManager partitionManager,
                                 final MemoryIndex index,
                                 final RememberPathway rememberPathway,
                                 final SalienceProfile salienceProfile) {
        ReflectSignal signal = ReflectSignal.builder()
                .partitionManager(partitionManager)
                .index(index)
                .quantizer(quantizer)
                .rememberPathway(rememberPathway)
                .embeddingProvider(embeddingProvider)
                .textGenerator(textGenerator)
                .importanceProvider(importanceProvider)
                .salienceProfile(salienceProfile)
                .policy(policy)
                .centroidRouter(centroidRouter)
                .templateEngine(templateEngine)
                .hebbianGraph(hebbianGraph)
                .temporalChain(temporalChain)
                .entityDirectory(entityDirectory)
                .hyperEntityGraph(hyperEntityGraph)
                .wal(wal)
                .typeNormalizer(typeNormalizer)
                .minClusterSize(minClusterSize)
                .pinSourceEpisodes(pinSourceEpisodes)
                .pinnedQuota(pinnedQuota)
                .soulDriftRefusionEnabled(soulDriftRefusionEnabled)
                .soulDriftRefusionBatchSize(soulDriftRefusionBatchSize)
                .temporalRetentionDays(temporalRetentionDays)
                .entityResolutionEnabled(entityResolutionEnabled)
                .entityShadowMode(entityShadowMode)
                .entityCosineThreshold(entityCosineThreshold)
                .mentalStateTracker(mentalStateTracker)
                .cognitiveManifold(cognitiveManifold)
                .softIdentityAnchorEnabled(softIdentityAnchorEnabled)
                .identityAnchorEta(identityAnchorEta)
                .identityLyapunovThreshold(identityLyapunovThreshold)
                .build();

        return conduct(signal);
    }

    @Override
    public void close() {
        // Any resources closed gracefully
    }

    public static final class Builder {
        private ScalarQuantizer quantizer;
        private EmbeddingProvider embeddingProvider;
        private LlmProvider textGenerator;
        private ImportanceProvider importanceProvider;
        private CircadianPolicy policy = CircadianPolicy.DEFAULT;
        private CentroidRouter centroidRouter;
        private TemplateEngine templateEngine;

        private HebbianGraphBase hebbianGraph;
        private TemporalChainMemory temporalChain;
        private EntityDirectory entityDirectory;
        private HyperEntityGraphMemory hyperEntityGraph;
        private MemoryWal wal;
        private TypeNormalizer typeNormalizer;

        private int minClusterSize = SpectorPropertyConstants.DEFAULT_MEMORY_REFLECT_MIN_CLUSTER_SIZE;
        private boolean pinSourceEpisodes = false;
        private int pinnedQuota = 10_000;
        private boolean soulDriftRefusionEnabled = SpectorPropertyConstants.DEFAULT_CONSOLIDATION_SOUL_DRIFT_REFUSION_ENABLED;
        private int soulDriftRefusionBatchSize = SpectorPropertyConstants.DEFAULT_CONSOLIDATION_SOUL_DRIFT_REFUSION_BATCH_SIZE;
        private int temporalRetentionDays = 30;
        private boolean entityResolutionEnabled = false;
        private boolean entityShadowMode = true;
        private float entityCosineThreshold = 0.85f;

        private CognitiveManifold cognitiveManifold;
        private com.spectrayan.spector.memory.aisme.relay.ManifoldConsolidationRelay manifoldConsolidationRelay;
        private com.spectrayan.spector.memory.aisme.relay.SoftIdentityAnchorRelay softIdentityAnchorRelay;
        private com.spectrayan.spector.memory.aisme.fegr.MentalStateTracker mentalStateTracker;
        private java.util.function.Supplier<java.util.List<float[]>> coActivationSupplier;
        private java.util.function.Function<com.spectrayan.spector.commons.pathway.SynapticRelay<ReflectSignal>, com.spectrayan.spector.commons.pathway.SynapticRelay<ReflectSignal>> interceptor;

        private boolean softIdentityAnchorEnabled = SpectorPropertyConstants.DEFAULT_MEMORY_AISME_SOFT_IDENTITY_ANCHOR_ENABLED;
        private float identityAnchorEta = SpectorPropertyConstants.DEFAULT_MEMORY_AISME_IDENTITY_ANCHOR_ETA;
        private float identityLyapunovThreshold = SpectorPropertyConstants.DEFAULT_MEMORY_AISME_IDENTITY_LYAPUNOV_THRESHOLD;

        public Builder quantizer(ScalarQuantizer q) { this.quantizer = q; return this; }
        public Builder embeddingProvider(EmbeddingProvider ep) { this.embeddingProvider = ep; return this; }
        public Builder textGenerator(LlmProvider tg) { this.textGenerator = tg; return this; }
        public Builder importanceProvider(ImportanceProvider ip) { this.importanceProvider = ip; return this; }
        public Builder policy(CircadianPolicy p) { this.policy = p; return this; }
        public Builder centroidRouter(CentroidRouter cr) { this.centroidRouter = cr; return this; }
        public Builder templateEngine(TemplateEngine te) { this.templateEngine = te; return this; }

        public Builder hebbianGraph(HebbianGraphBase hg) { this.hebbianGraph = hg; return this; }
        public Builder temporalChain(TemporalChainMemory tc) { this.temporalChain = tc; return this; }
        public Builder entityDirectory(EntityDirectory ed) { this.entityDirectory = ed; return this; }
        public Builder hyperEntityGraph(HyperEntityGraphMemory heg) { this.hyperEntityGraph = heg; return this; }
        public Builder wal(MemoryWal w) { this.wal = w; return this; }
        public Builder typeNormalizer(TypeNormalizer tn) { this.typeNormalizer = tn; return this; }

        public Builder minClusterSize(int sz) { this.minClusterSize = sz; return this; }
        public Builder pinSourceEpisodes(boolean pin) { this.pinSourceEpisodes = pin; return this; }
        public Builder pinnedQuota(int quota) { this.pinnedQuota = quota; return this; }
        public Builder soulDriftRefusionEnabled(boolean enabled) { this.soulDriftRefusionEnabled = enabled; return this; }
        public Builder soulDriftRefusionBatchSize(int batch) { this.soulDriftRefusionBatchSize = batch; return this; }
        public Builder temporalRetentionDays(int days) { this.temporalRetentionDays = days; return this; }
        public Builder entityResolutionEnabled(boolean enabled) { this.entityResolutionEnabled = enabled; return this; }
        public Builder entityShadowMode(boolean shadow) { this.entityShadowMode = shadow; return this; }
        public Builder entityCosineThreshold(float threshold) { this.entityCosineThreshold = threshold; return this; }

        public Builder cognitiveManifold(CognitiveManifold cm) {
            this.cognitiveManifold = cm;
            return this;
        }

        public Builder manifoldConsolidationRelay(com.spectrayan.spector.memory.aisme.relay.ManifoldConsolidationRelay mcr) {
            this.manifoldConsolidationRelay = mcr;
            return this;
        }

        public Builder softIdentityAnchorRelay(SoftIdentityAnchorRelay siar) {
            this.softIdentityAnchorRelay = siar;
            return this;
        }

        public Builder mentalStateTracker(com.spectrayan.spector.memory.aisme.fegr.MentalStateTracker mst) {
            this.mentalStateTracker = mst;
            return this;
        }

        public Builder coActivationSupplier(java.util.function.Supplier<java.util.List<float[]>> supplier) {
            this.coActivationSupplier = supplier;
            return this;
        }

        public Builder interceptor(
                final java.util.function.Function<com.spectrayan.spector.commons.pathway.SynapticRelay<ReflectSignal>, com.spectrayan.spector.commons.pathway.SynapticRelay<ReflectSignal>> interceptor) {
            this.interceptor = interceptor;
            return this;
        }

        public Builder softIdentityAnchorEnabled(boolean enabled) {
            this.softIdentityAnchorEnabled = enabled;
            return this;
        }

        public Builder identityAnchorEta(float eta) {
            this.identityAnchorEta = eta;
            return this;
        }

        public Builder identityLyapunovThreshold(float threshold) {
            this.identityLyapunovThreshold = threshold;
            return this;
        }

        public ReflectPathway build() {
            return new ReflectPathway(this);
        }
    }
}
