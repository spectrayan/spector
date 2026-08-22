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

import com.spectrayan.spector.commons.template.TemplateEngine;
import com.spectrayan.spector.memory.ImportanceProvider;
import com.spectrayan.spector.memory.PartitionManager;
import com.spectrayan.spector.memory.RememberPathway;
import com.spectrayan.spector.memory.cortex.CentroidRouter;
import com.spectrayan.spector.memory.graph.EntityDirectory;
import com.spectrayan.spector.memory.graph.GraphHealthMetrics;
import com.spectrayan.spector.memory.graph.HyperEntityGraphMemory;
import com.spectrayan.spector.memory.graph.TypeNormalizer;
import com.spectrayan.spector.memory.hebbian.HebbianGraphBase;
import com.spectrayan.spector.core.quantization.ScalarQuantizer;
import com.spectrayan.spector.memory.hippocampus.CircadianPolicy;
import com.spectrayan.spector.memory.index.MemoryIndex;
import com.spectrayan.spector.memory.model.ReflectReport;
import com.spectrayan.spector.memory.model.SalienceProfile;
import com.spectrayan.spector.memory.pipeline.CognitiveIngestionTarget;
import com.spectrayan.spector.memory.sync.MemoryWal;
import com.spectrayan.spector.memory.temporal.TemporalChainMemory;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.generation.LlmProvider;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Mutable context payload propagating through the biological sleep consolidation (Reflect) pathway.
 */
public final class ReflectSignal {

    // ── Subsystems & Context ───────────────────────────────────────
    private final PartitionManager partitionManager;
    private final MemoryIndex index;
    private final ScalarQuantizer quantizer;
    private final RememberPathway rememberPathway;
    private final CognitiveIngestionTarget ingestionTarget;
    private final EmbeddingProvider embeddingProvider;
    private final LlmProvider textGenerator;
    private final ImportanceProvider importanceProvider;
    private final SalienceProfile salienceProfile;
    private final CircadianPolicy policy;
    private final CentroidRouter centroidRouter;
    private final TemplateEngine templateEngine;

    // ── Graph Subsystems ───────────────────────────────────────────
    private final HebbianGraphBase hebbianGraph;
    private final TemporalChainMemory temporalChain;
    private final EntityDirectory entityDirectory;
    private final HyperEntityGraphMemory hyperEntityGraph;
    private final MemoryWal wal;
    private final TypeNormalizer typeNormalizer;

    // ── Configuration Flags & Budgets ──────────────────────────────
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

    // ── Runtime Execution Metrics ──────────────────────────────────
    private final Instant startTime;
    private final GraphHealthMetrics graphMetrics;
    private final AtomicInteger totalTombstoned = new AtomicInteger(0);
    private final AtomicInteger totalCompacted = new AtomicInteger(0);
    private final AtomicInteger totalConsolidated = new AtomicInteger(0);
    private final AtomicInteger temporalPruned = new AtomicInteger(0);
    private final AtomicInteger soulDriftedCount = new AtomicInteger(0);
    private final AtomicInteger soulRefusedCount = new AtomicInteger(0);
    private final AtomicInteger logTurnsConsolidated = new AtomicInteger(0);
    private final AtomicInteger proceduralCrystallizedCount = new AtomicInteger(0);
    private double sumImportanceDelta = 0.0;
    private int pinnedCount = 0;

    private ReflectSignal(final Builder builder) {
        this.partitionManager = builder.partitionManager;
        this.index = builder.index;
        this.quantizer = builder.quantizer;
        this.rememberPathway = builder.rememberPathway;
        this.ingestionTarget = builder.ingestionTarget;
        this.embeddingProvider = builder.embeddingProvider;
        this.textGenerator = builder.textGenerator;
        this.importanceProvider = builder.importanceProvider != null ? builder.importanceProvider : ImportanceProvider.baseline();
        this.salienceProfile = builder.salienceProfile != null ? builder.salienceProfile : SalienceProfile.NEUTRAL;
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

        this.startTime = Instant.now();
        this.graphMetrics = builder.graphMetrics != null ? builder.graphMetrics : new GraphHealthMetrics();
    }

    public static Builder builder() {
        return new Builder();
    }

    // ── Getters & Accessors ────────────────────────────────────────

    public com.spectrayan.spector.memory.aisme.fegr.MentalStateTracker mentalStateTracker() { return mentalStateTracker; }
    public PartitionManager partitionManager() { return partitionManager; }
    public MemoryIndex index() { return index; }
    public ScalarQuantizer quantizer() { return quantizer; }
    public RememberPathway rememberPathway() { return rememberPathway; }
    public CognitiveIngestionTarget ingestionTarget() { return ingestionTarget; }
    public EmbeddingProvider embeddingProvider() { return embeddingProvider; }
    public LlmProvider textGenerator() { return textGenerator; }
    public ImportanceProvider importanceProvider() { return importanceProvider; }
    public SalienceProfile salienceProfile() { return salienceProfile; }
    public CircadianPolicy policy() { return policy; }
    public CentroidRouter centroidRouter() { return centroidRouter; }
    public TemplateEngine templateEngine() { return templateEngine; }

    public HebbianGraphBase hebbianGraph() { return hebbianGraph; }
    public TemporalChainMemory temporalChain() { return temporalChain; }
    public EntityDirectory entityDirectory() { return entityDirectory; }
    public HyperEntityGraphMemory hyperEntityGraph() { return hyperEntityGraph; }
    public MemoryWal wal() { return wal; }
    public TypeNormalizer typeNormalizer() { return typeNormalizer; }

    public int minClusterSize() { return minClusterSize; }
    public boolean pinSourceEpisodes() { return pinSourceEpisodes; }
    public int pinnedQuota() { return pinnedQuota; }
    public boolean soulDriftRefusionEnabled() { return soulDriftRefusionEnabled; }
    public int soulDriftRefusionBatchSize() { return soulDriftRefusionBatchSize; }
    public int temporalRetentionDays() { return temporalRetentionDays; }
    public boolean entityResolutionEnabled() { return entityResolutionEnabled; }
    public boolean entityShadowMode() { return entityShadowMode; }
    public float entityCosineThreshold() { return entityCosineThreshold; }

    public Instant startTime() { return startTime; }
    public GraphHealthMetrics graphMetrics() { return graphMetrics; }

    public int totalTombstoned() { return totalTombstoned.get(); }
    public void addTombstoned(int count) { totalTombstoned.addAndGet(count); }

    public int totalCompacted() { return totalCompacted.get(); }
    public void addCompacted(int count) { totalCompacted.addAndGet(count); }

    public int totalConsolidated() { return totalConsolidated.get(); }
    public void addConsolidated(int count) { totalConsolidated.addAndGet(count); }

    public int temporalPruned() { return temporalPruned.get(); }
    public void addTemporalPruned(int count) { temporalPruned.addAndGet(count); }

    public int soulDriftedCount() { return soulDriftedCount.get(); }
    public void addSoulDrifted(int count) { soulDriftedCount.addAndGet(count); }

    public int soulRefusedCount() { return soulRefusedCount.get(); }
    public void addSoulRefused(int count) { soulRefusedCount.addAndGet(count); }

    public int logTurnsConsolidated() { return logTurnsConsolidated.get(); }
    public void addLogTurnsConsolidated(int count) { logTurnsConsolidated.addAndGet(count); }

    public int proceduralCrystallizedCount() { return proceduralCrystallizedCount.get(); }
    public void addProceduralCrystallized(int count) { proceduralCrystallizedCount.addAndGet(count); }

    public synchronized void recordImportanceDelta(double delta) {
        this.sumImportanceDelta += Math.abs(delta);
    }

    public synchronized float averageImportanceDelta() {
        int count = soulRefusedCount.get();
        return count > 0 ? (float) (sumImportanceDelta / count) : 0.0f;
    }

    public synchronized boolean canPin() {
        return pinSourceEpisodes && pinnedCount < pinnedQuota;
    }

    public synchronized void incrementPinned() {
        pinnedCount++;
    }

    /**
     * Builds the final {@link ReflectReport} snapshot from the accumulated signal metrics.
     */
    public ReflectReport buildReport() {
        Duration elapsed = Duration.between(startTime, Instant.now());
        return new ReflectReport(
                totalConsolidated.get(),
                totalTombstoned.get(),
                totalCompacted.get(),
                temporalPruned.get(),
                elapsed,
                graphMetrics,
                soulDriftedCount.get(),
                soulRefusedCount.get(),
                averageImportanceDelta(),
                logTurnsConsolidated.get()
        );
    }

    /**
     * Builder for {@link ReflectSignal}.
     */
    public static final class Builder {
        private PartitionManager partitionManager;
        private MemoryIndex index;
        private ScalarQuantizer quantizer;
        private RememberPathway rememberPathway;
        private CognitiveIngestionTarget ingestionTarget;
        private EmbeddingProvider embeddingProvider;
        private LlmProvider textGenerator;
        private ImportanceProvider importanceProvider;
        private SalienceProfile salienceProfile;
        private CircadianPolicy policy = CircadianPolicy.DEFAULT;
        private CentroidRouter centroidRouter;
        private TemplateEngine templateEngine;

        private HebbianGraphBase hebbianGraph;
        private TemporalChainMemory temporalChain;
        private EntityDirectory entityDirectory;
        private HyperEntityGraphMemory hyperEntityGraph;
        private MemoryWal wal;
        private TypeNormalizer typeNormalizer;

        private int minClusterSize = 5;
        private boolean pinSourceEpisodes = false;
        private int pinnedQuota = 10_000;
        private boolean soulDriftRefusionEnabled = true;
        private int soulDriftRefusionBatchSize = 100;
        private int temporalRetentionDays = 30;
        private boolean entityResolutionEnabled = false;
        private boolean entityShadowMode = true;
        private float entityCosineThreshold = 0.85f;
        private GraphHealthMetrics graphMetrics;
        private com.spectrayan.spector.memory.aisme.fegr.MentalStateTracker mentalStateTracker;

        public Builder partitionManager(PartitionManager pm) { this.partitionManager = pm; return this; }
        public Builder index(MemoryIndex idx) { this.index = idx; return this; }
        public Builder quantizer(ScalarQuantizer q) { this.quantizer = q; return this; }
        public Builder rememberPathway(RememberPathway rp) { this.rememberPathway = rp; return this; }
        public Builder ingestionTarget(CognitiveIngestionTarget cit) { this.ingestionTarget = cit; return this; }
        public Builder embeddingProvider(EmbeddingProvider ep) { this.embeddingProvider = ep; return this; }
        public Builder textGenerator(LlmProvider tg) { this.textGenerator = tg; return this; }
        public Builder importanceProvider(ImportanceProvider ip) { this.importanceProvider = ip; return this; }
        public Builder salienceProfile(SalienceProfile sp) { this.salienceProfile = sp; return this; }
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
        public Builder graphMetrics(GraphHealthMetrics metrics) { this.graphMetrics = metrics; return this; }
        public Builder mentalStateTracker(com.spectrayan.spector.memory.aisme.fegr.MentalStateTracker mst) { this.mentalStateTracker = mst; return this; }

        public ReflectSignal build() {
            return new ReflectSignal(this);
        }
    }
}
