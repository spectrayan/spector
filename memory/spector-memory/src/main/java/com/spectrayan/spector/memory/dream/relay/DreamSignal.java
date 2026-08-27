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

    public AtomicInteger dreamsGenerated() { return dreamsGenerated; }
    public AtomicInteger dreamsIngested() { return dreamsIngested; }
    public AtomicInteger failedPairs() { return failedPairs; }
    public Instant startTime() { return startTime; }

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

        public Builder mode(DreamMode mode) { this.mode = mode; return this; }
        public Builder config(DreamConfig config) { this.config = config; return this; }
        public Builder temperature(float temperature) { this.temperature = temperature; return this; }
        public Builder partitionManager(PartitionManager pm) { this.partitionManager = pm; return this; }
        public Builder aismeConfig(AismeConfig config) { this.aismeConfig = config; return this; }
        public Builder seedMemoryIds(List<String> ids) { this.seedMemoryIds = ids; return this; }
        public Builder seedVectors(List<float[]> vectors) { this.seedVectors = vectors; return this; }

        public DreamSignal build() {
            return new DreamSignal(this);
        }
    }
}
