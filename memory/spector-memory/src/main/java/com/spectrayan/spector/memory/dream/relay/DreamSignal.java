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
package com.spectrayan.spector.memory.dream.relay;

import com.spectrayan.spector.memory.PartitionManager;
import com.spectrayan.spector.memory.aisme.config.AismeConfig;
import com.spectrayan.spector.memory.aisme.hopfield.ContinuousHopfieldNetwork;
import com.spectrayan.spector.memory.dream.DreamJournalMemory;
import com.spectrayan.spector.memory.graph.EntityDirectory;
import com.spectrayan.spector.memory.graph.HyperEntityGraphMemory;
import com.spectrayan.spector.memory.hebbian.HebbianGraphBase;
import com.spectrayan.spector.memory.id.MemoryIdGenerator;
import com.spectrayan.spector.memory.id.TsidGenerator;
import com.spectrayan.spector.memory.kernel.shape.DistributedMemoryTensor;
import com.spectrayan.spector.memory.model.SalienceProfile;
import com.spectrayan.spector.memory.model.SoulContext;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Mutable synaptic execution signal passed along the {@link com.spectrayan.spector.memory.pathway.DreamPathway}.
 *
 * <h3>Biological Analog: Activation during Sleep Consolidation</h3>
 * <p>Carries the state of spontaneous offline generative replay, tracking counterfactual
 * simulations, multi-soul identity constraints, salience profiles, triage outcomes, and modifications
 * to Hebbian topologies.</p>
 *
 * @since 1.4.0
 */
public final class DreamSignal {

    public enum TriageOutcome {
        EPISTEMIC, PRAGMATIC, IDENTITY, NOISE
    }

    public record DreamScene(
            String id,
            String narrative,
            String insightText,
            float[] embedding,
            List<String> sourceIds,
            float qualityScore,
            TriageOutcome triageOutcome
    ) {}

    private final DreamMode mode;
    private final DreamConfig config;
    private final float temperature;
    private final PartitionManager partitionManager;
    private final AismeConfig aismeConfig;

    private final SoulContext primarySoul;
    private final List<SoulContext> soulContexts;
    private final SalienceProfile salienceProfile;

    private final List<String> seedMemoryIds;
    private final List<float[]> seedVectors;
    private final List<DreamScene> constructedScenes;
    private final List<DreamScene> survivingScenes;

    private final List<SceneFragment> fragments;
    private final List<ExtractedInsight> extractedInsights;
    private static final TsidGenerator DEFAULT_ID_GEN = new TsidGenerator();

    private final MemoryIdGenerator idGenerator;
    private final HebbianGraphBase hebbianGraph;
    private final DistributedMemoryTensor distributedMemoryTensor;
    private final DreamJournalMemory dreamJournalMemory;
    private final EntityDirectory entityDirectory;
    private final HyperEntityGraphMemory hyperEntityGraph;
    private final EmbeddingProvider embeddingProvider;
    private final ContinuousHopfieldNetwork hopfieldNetwork;

    private final AtomicInteger dreamsGenerated = new AtomicInteger(0);
    private final AtomicInteger dreamsIngested = new AtomicInteger(0);
    private final AtomicInteger failedPairs = new AtomicInteger(0);

    // Spacetime Simulation Fields (ADR-0031)
    private final long simulationTimeMs;
    private final float[] queryTau;
    private final com.spectrayan.spector.core.spacetime.SpacetimeSimulationMode spacetimeMode;
    private final float recencyLambda;
    private final boolean allowFuture;
    private List<com.spectrayan.spector.memory.model.CognitiveResult> candidateSeeds = new ArrayList<>();

    private final Instant startTime;
    private final ReentrantLock sceneLock = new ReentrantLock();

    private DreamSignal(Builder builder) {
        this.mode = builder.mode;
        this.config = builder.config != null ? builder.config : DreamConfig.defaultConfig();
        
        if (builder.temperature > 0.0f) {
            this.temperature = builder.temperature;
        } else if (this.mode != null) {
            this.temperature = switch (this.mode) {
                case REM -> this.config.dreamTemperatureRem();
                case DAYDREAM -> this.config.dreamTemperatureDaydream();
                case THOUGHT_EXPERIMENT -> this.config.dreamTemperatureThought();
            };
        } else {
            this.temperature = 1.0f;
        }

        this.partitionManager = builder.partitionManager;
        this.aismeConfig = builder.aismeConfig;
        this.primarySoul = builder.primarySoul;
        this.soulContexts = builder.soulContexts != null ? Collections.unmodifiableList(builder.soulContexts) : List.of();
        this.salienceProfile = builder.salienceProfile;
        this.idGenerator = builder.idGenerator;
        
        this.seedMemoryIds = builder.seedMemoryIds != null ? new ArrayList<>(builder.seedMemoryIds) : new ArrayList<>();
        this.seedVectors = builder.seedVectors != null ? new ArrayList<>(builder.seedVectors) : new ArrayList<>();
        
        this.constructedScenes = new ArrayList<>();
        this.survivingScenes = new ArrayList<>();
        
        this.fragments = builder.fragments != null ? new ArrayList<>(builder.fragments) : new ArrayList<>();
        this.extractedInsights = builder.extractedInsights != null ? new ArrayList<>(builder.extractedInsights) : new ArrayList<>();
        this.hebbianGraph = builder.hebbianGraph;
        this.distributedMemoryTensor = builder.distributedMemoryTensor;
        this.dreamJournalMemory = builder.dreamJournalMemory;
        this.entityDirectory = builder.entityDirectory;
        this.hyperEntityGraph = builder.hyperEntityGraph;
        this.embeddingProvider = builder.embeddingProvider;
        this.hopfieldNetwork = builder.hopfieldNetwork;

        final long now = System.currentTimeMillis();
        if (builder.simulationTimeMs > 0L) {
            this.simulationTimeMs = builder.simulationTimeMs;
        } else if (this.mode == DreamMode.REM) {
            this.simulationTimeMs = now + 86_400_000L; // default 1 day prospective horizon for REM
        } else {
            this.simulationTimeMs = now;
        }

        if (builder.spacetimeMode != null) {
            this.spacetimeMode = builder.spacetimeMode;
        } else if (this.mode == DreamMode.REM) {
            this.spacetimeMode = com.spectrayan.spector.core.spacetime.SpacetimeSimulationMode.DREAM_REM;
        } else {
            this.spacetimeMode = com.spectrayan.spector.core.spacetime.SpacetimeSimulationMode.DREAM_NREM;
        }

        this.recencyLambda = builder.recencyLambda >= 0.0f ? builder.recencyLambda : this.spacetimeMode.recencyLambda();
        this.allowFuture = builder.allowFuture != null ? builder.allowFuture : this.spacetimeMode.allowsFuture();
        this.queryTau = builder.queryTau != null ? builder.queryTau : com.spectrayan.spector.core.spacetime.Time2VecProjector.project(this.simulationTimeMs);
        if (builder.candidateSeeds != null) {
            this.candidateSeeds = new ArrayList<>(builder.candidateSeeds);
        }

        this.startTime = Instant.now();
    }

    public static Builder builder() {
        return new Builder();
    }

    public MemoryIdGenerator idGenerator() { return idGenerator; }

    /**
     * Generates a globally unique memory identifier using the configured {@link MemoryIdGenerator},
     * falling back to the standard TSID generator if no custom strategy was injected.
     *
     * @return unique memory ID
     */
    public String nextId() {
        return idGenerator != null ? idGenerator.generate() : DEFAULT_ID_GEN.generate();
    }

    public DreamMode mode() { return mode; }
    public DreamConfig config() { return config; }
    public float temperature() { return temperature; }
    public PartitionManager partitionManager() { return partitionManager; }
    public AismeConfig aismeConfig() { return aismeConfig; }

    public SoulContext primarySoul() { return primarySoul; }
    public List<SoulContext> soulContexts() { return soulContexts; }
    public SalienceProfile salienceProfile() { return salienceProfile; }

    public List<String> seedMemoryIds() { return seedMemoryIds; }
    public List<float[]> seedVectors() { return seedVectors; }
    public List<DreamScene> constructedScenes() { return constructedScenes; }
    public List<DreamScene> survivingScenes() { return survivingScenes; }

    public long simulationTimeMs() { return simulationTimeMs; }
    public float[] queryTau() { return queryTau; }
    public com.spectrayan.spector.core.spacetime.SpacetimeSimulationMode spacetimeMode() { return spacetimeMode; }
    public float recencyLambda() { return recencyLambda; }
    public boolean allowFuture() { return allowFuture; }
    public List<com.spectrayan.spector.memory.model.CognitiveResult> candidateSeeds() { return candidateSeeds; }
    public void setCandidateSeeds(List<com.spectrayan.spector.memory.model.CognitiveResult> seeds) {
        this.candidateSeeds = seeds != null ? new ArrayList<>(seeds) : new ArrayList<>();
    }

    public List<SceneFragment> fragments() { return fragments; }
    public List<ExtractedInsight> extractedInsights() { return extractedInsights; }
    public HebbianGraphBase hebbianGraph() { return hebbianGraph; }
    public DistributedMemoryTensor distributedMemoryTensor() { return distributedMemoryTensor; }
    public DreamJournalMemory dreamJournalMemory() { return dreamJournalMemory; }
    public EntityDirectory entityDirectory() { return entityDirectory; }
    public HyperEntityGraphMemory hyperEntityGraph() { return hyperEntityGraph; }
    public EmbeddingProvider embeddingProvider() { return embeddingProvider; }
    public ContinuousHopfieldNetwork hopfieldNetwork() { return hopfieldNetwork; }

    public AtomicInteger dreamsGenerated() { return dreamsGenerated; }
    public AtomicInteger dreamsIngested() { return dreamsIngested; }
    public AtomicInteger failedPairs() { return failedPairs; }
    public Instant startTime() { return startTime; }

    public void addFragment(SceneFragment fragment) {
        sceneLock.lock();
        try {
            fragments.add(fragment);
        } finally {
            sceneLock.unlock();
        }
    }
    
    public void addExtractedInsight(ExtractedInsight insight) {
        sceneLock.lock();
        try {
            extractedInsights.add(insight);
        } finally {
            sceneLock.unlock();
        }
    }

    public void addConstructedScene(DreamScene scene) {
        sceneLock.lock();
        try {
            constructedScenes.add(scene);
            dreamsGenerated.incrementAndGet();
        } finally {
            sceneLock.unlock();
        }
    }
    
    public void addSurvivingScene(DreamScene scene) {
        sceneLock.lock();
        try {
            survivingScenes.add(scene);
            dreamsIngested.incrementAndGet();
        } finally {
            sceneLock.unlock();
        }
    }

    public DreamReport buildReport() {
        Duration elapsed = Duration.between(startTime, Instant.now());
        int constructedCount;
        int survivingCount;
        sceneLock.lock();
        try {
            constructedCount = constructedScenes.size();
            survivingCount = survivingScenes.size();
        } finally {
            sceneLock.unlock();
        }
        return new DreamReport(
                seedMemoryIds.size(),
                constructedCount,
                survivingCount,
                dreamsIngested.get(),
                config.journalEnabled() ? survivingCount : 0,
                failedPairs.get(),
                elapsed,
                mode
        );
    }

    public static final class Builder {
        private DreamMode mode;
        private DreamConfig config;
        private float temperature;
        private PartitionManager partitionManager;
        private AismeConfig aismeConfig;
        private SoulContext primarySoul;
        private List<SoulContext> soulContexts;
        private SalienceProfile salienceProfile;
        private List<String> seedMemoryIds;
        private List<float[]> seedVectors;
        private List<SceneFragment> fragments;
        private List<ExtractedInsight> extractedInsights;
        private HebbianGraphBase hebbianGraph;
        private DistributedMemoryTensor distributedMemoryTensor;
        private DreamJournalMemory dreamJournalMemory;
        private EntityDirectory entityDirectory;
        private HyperEntityGraphMemory hyperEntityGraph;
        private EmbeddingProvider embeddingProvider;
        private ContinuousHopfieldNetwork hopfieldNetwork;
        private MemoryIdGenerator idGenerator;

        private long simulationTimeMs = 0L;
        private float[] queryTau = null;
        private com.spectrayan.spector.core.spacetime.SpacetimeSimulationMode spacetimeMode = null;
        private float recencyLambda = -1.0f;
        private Boolean allowFuture = null;
        private List<com.spectrayan.spector.memory.model.CognitiveResult> candidateSeeds = null;

        public Builder mode(DreamMode mode) { this.mode = mode; return this; }
        public Builder config(DreamConfig config) { this.config = config; return this; }
        public Builder temperature(float temperature) { this.temperature = temperature; return this; }
        public Builder partitionManager(PartitionManager pm) { this.partitionManager = pm; return this; }
        public Builder aismeConfig(AismeConfig config) { this.aismeConfig = config; return this; }
        public Builder primarySoul(SoulContext soul) { this.primarySoul = soul; return this; }
        public Builder soulContexts(List<SoulContext> soulContexts) { this.soulContexts = soulContexts; return this; }
        public Builder salienceProfile(SalienceProfile profile) { this.salienceProfile = profile; return this; }
        public Builder idGenerator(MemoryIdGenerator idGenerator) { this.idGenerator = idGenerator; return this; }
        public Builder seedMemoryIds(List<String> ids) { this.seedMemoryIds = ids; return this; }
        public Builder seedVectors(List<float[]> vectors) { this.seedVectors = vectors; return this; }
        public Builder fragments(List<SceneFragment> fragments) { this.fragments = fragments; return this; }
        public Builder extractedInsights(List<ExtractedInsight> insights) { this.extractedInsights = insights; return this; }
        public Builder hebbianGraph(HebbianGraphBase graph) { this.hebbianGraph = graph; return this; }
        public Builder distributedMemoryTensor(DistributedMemoryTensor tensor) { this.distributedMemoryTensor = tensor; return this; }
        public Builder dreamJournalMemory(DreamJournalMemory memory) { this.dreamJournalMemory = memory; return this; }
        public Builder entityDirectory(EntityDirectory directory) { this.entityDirectory = directory; return this; }
        public Builder hyperEntityGraph(HyperEntityGraphMemory graph) { this.hyperEntityGraph = graph; return this; }
        public Builder embeddingProvider(EmbeddingProvider provider) { this.embeddingProvider = provider; return this; }
        public Builder hopfieldNetwork(ContinuousHopfieldNetwork network) { this.hopfieldNetwork = network; return this; }

        public Builder simulationTimeMs(long simTime) { this.simulationTimeMs = simTime; return this; }
        public Builder queryTau(float[] tau) { this.queryTau = tau; return this; }
        public Builder spacetimeMode(com.spectrayan.spector.core.spacetime.SpacetimeSimulationMode mode) { this.spacetimeMode = mode; return this; }
        public Builder recencyLambda(float lambda) { this.recencyLambda = lambda; return this; }
        public Builder allowFuture(boolean allow) { this.allowFuture = allow; return this; }
        public Builder candidateSeeds(List<com.spectrayan.spector.memory.model.CognitiveResult> seeds) { this.candidateSeeds = seeds; return this; }

        public DreamSignal build() {
            return new DreamSignal(this);
        }
    }
}
