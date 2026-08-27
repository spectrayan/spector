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

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.spectrayan.spector.memory.dream.relay.SceneFragment;
import com.spectrayan.spector.memory.dream.relay.ExtractedInsight;
import com.spectrayan.spector.memory.hebbian.HebbianGraphBase;
import com.spectrayan.spector.memory.kernel.shape.DistributedMemoryTensor;
import com.spectrayan.spector.memory.dream.DreamJournalMemory;
import com.spectrayan.spector.memory.graph.EntityDirectory;
import com.spectrayan.spector.memory.graph.HyperEntityGraphMemory;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.memory.aisme.hopfield.ContinuousHopfieldNetwork;

/**
 * Mutable synaptic execution signal passed along the {@link com.spectrayan.spector.memory.DreamPathway}.
 *
 * <h3>Biological Analog: Activation during Sleep Consolidation</h3>
 * <p>Carries the state of spontaneous offline generative replay, tracking counterfactual
 * simulations, triage outcomes, and modifications to Hebbian topologies.</p>
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

    private final List<String> seedMemoryIds;
    private final List<float[]> seedVectors;
    private final List<DreamScene> constructedScenes;
    private final List<DreamScene> survivingScenes;

    private final List<SceneFragment> fragments;
    private final List<ExtractedInsight> extractedInsights;
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

    private final Instant startTime;

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

        this.startTime = Instant.now();
    }

    public static Builder builder() {
        return new Builder();
    }

    public DreamMode mode() { return mode; }
    public DreamConfig config() { return config; }
    public float temperature() { return temperature; }
    public PartitionManager partitionManager() { return partitionManager; }
    public AismeConfig aismeConfig() { return aismeConfig; }

    public List<String> seedMemoryIds() { return seedMemoryIds; }
    public List<float[]> seedVectors() { return seedVectors; }
    public List<DreamScene> constructedScenes() { return constructedScenes; }
    public List<DreamScene> survivingScenes() { return survivingScenes; }

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

    public synchronized void addFragment(SceneFragment fragment) {
        fragments.add(fragment);
    }
    
    public synchronized void addExtractedInsight(ExtractedInsight insight) {
        extractedInsights.add(insight);
    }

    public synchronized void addConstructedScene(DreamScene scene) {
        constructedScenes.add(scene);
        dreamsGenerated.incrementAndGet();
    }
    
    public synchronized void addSurvivingScene(DreamScene scene) {
        survivingScenes.add(scene);
        dreamsIngested.incrementAndGet();
    }

    public DreamReport buildReport() {
        Duration elapsed = Duration.between(startTime, Instant.now());
        return new DreamReport(
                seedMemoryIds.size(),
                constructedScenes.size(),
                survivingScenes.size(),
                dreamsIngested.get(),
                config.journalEnabled() ? survivingScenes.size() : 0,
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

        public Builder mode(DreamMode mode) { this.mode = mode; return this; }
        public Builder config(DreamConfig config) { this.config = config; return this; }
        public Builder temperature(float temperature) { this.temperature = temperature; return this; }
        public Builder partitionManager(PartitionManager pm) { this.partitionManager = pm; return this; }
        public Builder aismeConfig(AismeConfig config) { this.aismeConfig = config; return this; }
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

        public DreamSignal build() {
            return new DreamSignal(this);
        }
    }
}
