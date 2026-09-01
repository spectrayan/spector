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
package com.spectrayan.spector.memory.wander.relay;

import com.spectrayan.spector.core.quantization.ScalarQuantizer;
import com.spectrayan.spector.memory.persist.PartitionManager;
import com.spectrayan.spector.memory.aisme.config.AismeConfig;
import com.spectrayan.spector.memory.aisme.fegr.MentalStateTracker;
import com.spectrayan.spector.memory.aisme.homeostasis.HomeostaticCore;
import com.spectrayan.spector.memory.aisme.hopfield.ContinuousHopfieldNetwork;
import com.spectrayan.spector.memory.aisme.manifold.CognitiveManifold;
import com.spectrayan.spector.memory.cortex.ContinuityRecordMemory;
import com.spectrayan.spector.memory.hebbian.HebbianGraphBase;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Mutable synaptic execution signal passed along the {@link com.spectrayan.spector.memory.pathway.WanderPathway}.
 *
 * <h3>Biological Analog: Default Mode Network Spontaneous Activation State</h3>
 * <p>Carries the state of spontaneous associative search during wakeful rest, collecting
 * sampled memory representations, tracking continuous Hopfield energy convergence trajectories,
 * discovering cross-memory narrative synergies, and appending to the longitudinal \(\Phi_{CC}\) ledger.</p>
 *
 * @since 1.2.0
 */
public final class WanderSignal {

    public record DiscoveredAssociation(String sourceId, String targetId, float synergy, float weightDelta) {}

    private final PartitionManager partitionManager;
    private final ScalarQuantizer quantizer;
    private final EmbeddingProvider embeddingProvider;
    private final MentalStateTracker mentalStateTracker;
    private final CognitiveManifold cognitiveManifold;
    private final ContinuousHopfieldNetwork hopfieldNetwork;
    private final HebbianGraphBase hebbianGraph;
    private final HomeostaticCore homeostaticCore;
    private final ContinuityRecordMemory continuityMemory;
    private final AismeConfig aismeConfig;

    private final long lastActivityTimestampMs;
    private final int idleThresholdSeconds;
    private final int minSampleCount;
    private final float synergyThreshold;
    private final float hopfieldTemperature;
    private final float[] soulPriorPreference;

    // Spacetime Simulation Fields (ADR-0031)
    private final long simulationTimeMs;
    private final float[] queryTau;
    private final com.spectrayan.spector.core.spacetime.SpacetimeSimulationMode spacetimeMode;
    private final float recencyLambda;
    private final boolean allowFuture;
    private List<com.spectrayan.spector.memory.model.CognitiveResult> candidateSeeds = new ArrayList<>();

    private final Instant startTime;
    private final List<float[]> sampledVectors = new ArrayList<>();
    private final List<String> sampledMemoryIds = new ArrayList<>();
    private final List<DiscoveredAssociation> discoveredAssociations = new ArrayList<>();

    private final AtomicInteger associationsFormed = new AtomicInteger(0);
    private final AtomicBoolean snapshotRecorded = new AtomicBoolean(false);
    private final ReentrantLock statsLock = new ReentrantLock();
    private double totalSynapticWeightDelta = 0.0;

    private WanderSignal(final Builder builder) {
        this.partitionManager = builder.partitionManager;
        this.quantizer = builder.quantizer;
        this.embeddingProvider = builder.embeddingProvider;
        this.mentalStateTracker = builder.mentalStateTracker;
        this.cognitiveManifold = builder.cognitiveManifold;
        this.hopfieldNetwork = builder.hopfieldNetwork;
        this.hebbianGraph = builder.hebbianGraph;
        this.homeostaticCore = builder.homeostaticCore;
        this.continuityMemory = builder.continuityMemory;
        this.aismeConfig = builder.aismeConfig;

        this.lastActivityTimestampMs = builder.lastActivityTimestampMs;
        this.idleThresholdSeconds = builder.idleThresholdSeconds > 0 ? builder.idleThresholdSeconds : 60;
        this.minSampleCount = builder.minSampleCount > 0 ? builder.minSampleCount : 5;
        this.synergyThreshold = builder.synergyThreshold > 0.0f ? builder.synergyThreshold : 0.10f;
        this.hopfieldTemperature = builder.hopfieldTemperature > 0.0f ? builder.hopfieldTemperature : 1.0f;
        this.soulPriorPreference = builder.soulPriorPreference;

        this.simulationTimeMs = builder.simulationTimeMs > 0L ? builder.simulationTimeMs : System.currentTimeMillis();
        this.spacetimeMode = builder.spacetimeMode != null ? builder.spacetimeMode : com.spectrayan.spector.core.spacetime.SpacetimeSimulationMode.WANDER;
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

    // ── Getters ──

    public long simulationTimeMs() { return simulationTimeMs; }
    public float[] queryTau() { return queryTau; }
    public com.spectrayan.spector.core.spacetime.SpacetimeSimulationMode spacetimeMode() { return spacetimeMode; }
    public float recencyLambda() { return recencyLambda; }
    public boolean allowFuture() { return allowFuture; }
    public List<com.spectrayan.spector.memory.model.CognitiveResult> candidateSeeds() { return candidateSeeds; }
    public void setCandidateSeeds(List<com.spectrayan.spector.memory.model.CognitiveResult> seeds) {
        this.candidateSeeds = seeds != null ? new ArrayList<>(seeds) : new ArrayList<>();
    }

    public PartitionManager partitionManager() { return partitionManager; }
    public ScalarQuantizer quantizer() { return quantizer; }
    public EmbeddingProvider embeddingProvider() { return embeddingProvider; }
    public MentalStateTracker mentalStateTracker() { return mentalStateTracker; }
    public CognitiveManifold cognitiveManifold() { return cognitiveManifold; }
    public ContinuousHopfieldNetwork hopfieldNetwork() { return hopfieldNetwork; }
    public HebbianGraphBase hebbianGraph() { return hebbianGraph; }
    public HomeostaticCore homeostaticCore() { return homeostaticCore; }
    public ContinuityRecordMemory continuityMemory() { return continuityMemory; }
    public AismeConfig aismeConfig() { return aismeConfig; }

    public long lastActivityTimestampMs() { return lastActivityTimestampMs; }
    public int idleThresholdSeconds() { return idleThresholdSeconds; }
    public int minSampleCount() { return minSampleCount; }
    public float synergyThreshold() { return synergyThreshold; }
    public float hopfieldTemperature() { return hopfieldTemperature; }
    public float[] soulPriorPreference() { return soulPriorPreference; }
    public Instant startTime() { return startTime; }

    public List<float[]> sampledVectors() { return sampledVectors; }
    public List<String> sampledMemoryIds() { return sampledMemoryIds; }
    public List<DiscoveredAssociation> discoveredAssociations() { return discoveredAssociations; }

    public int associationsFormed() { return associationsFormed.get(); }
    public void addAssociationsFormed(int count) { associationsFormed.addAndGet(count); }

    public void recordWeightDelta(double delta) {
        statsLock.lock();
        try {
            totalSynapticWeightDelta += delta;
        } finally {
            statsLock.unlock();
        }
    }

    public double totalSynapticWeightDelta() {
        statsLock.lock();
        try {
            return totalSynapticWeightDelta;
        } finally {
            statsLock.unlock();
        }
    }

    public boolean isSnapshotRecorded() { return snapshotRecorded.get(); }
    public void setSnapshotRecorded(boolean recorded) { snapshotRecorded.set(recorded); }

    /**
     * Builds an immutable {@link WanderReport} from the execution metrics.
     */
    public WanderReport buildReport() {
        Duration elapsed = Duration.between(startTime, Instant.now());
        float weightDelta;
        statsLock.lock();
        try {
            weightDelta = (float) totalSynapticWeightDelta;
        } finally {
            statsLock.unlock();
        }
        return new WanderReport(
                sampledVectors.size(),
                associationsFormed.get(),
                weightDelta,
                snapshotRecorded.get(),
                elapsed,
                Collections.unmodifiableList(new ArrayList<>(discoveredAssociations))
        );
    }

    /**
     * Builder for {@link WanderSignal}.
     */
    public static final class Builder {
        private PartitionManager partitionManager;
        private ScalarQuantizer quantizer;
        private EmbeddingProvider embeddingProvider;
        private MentalStateTracker mentalStateTracker;
        private CognitiveManifold cognitiveManifold;
        private ContinuousHopfieldNetwork hopfieldNetwork;
        private HebbianGraphBase hebbianGraph;
        private HomeostaticCore homeostaticCore;
        private ContinuityRecordMemory continuityMemory;
        private AismeConfig aismeConfig;

        private long lastActivityTimestampMs = System.currentTimeMillis();
        private int idleThresholdSeconds = 60;
        private int minSampleCount = 5;
        private float synergyThreshold = 0.10f;
        private float hopfieldTemperature = 1.0f;
        private float[] soulPriorPreference = null;

        private long simulationTimeMs = 0L;
        private float[] queryTau = null;
        private com.spectrayan.spector.core.spacetime.SpacetimeSimulationMode spacetimeMode = null;
        private float recencyLambda = -1.0f;
        private Boolean allowFuture = null;
        private List<com.spectrayan.spector.memory.model.CognitiveResult> candidateSeeds = null;

        public Builder partitionManager(PartitionManager pm) { this.partitionManager = pm; return this; }
        public Builder quantizer(ScalarQuantizer q) { this.quantizer = q; return this; }
        public Builder embeddingProvider(EmbeddingProvider ep) { this.embeddingProvider = ep; return this; }
        public Builder mentalStateTracker(MentalStateTracker mst) { this.mentalStateTracker = mst; return this; }
        public Builder cognitiveManifold(CognitiveManifold cm) { this.cognitiveManifold = cm; return this; }
        public Builder hopfieldNetwork(ContinuousHopfieldNetwork chn) { this.hopfieldNetwork = chn; return this; }
        public Builder hebbianGraph(HebbianGraphBase hg) { this.hebbianGraph = hg; return this; }
        public Builder homeostaticCore(HomeostaticCore hc) { this.homeostaticCore = hc; return this; }
        public Builder continuityMemory(ContinuityRecordMemory crm) { this.continuityMemory = crm; return this; }
        public Builder aismeConfig(AismeConfig cfg) { this.aismeConfig = cfg; return this; }

        public Builder lastActivityTimestampMs(long ts) { this.lastActivityTimestampMs = ts; return this; }
        public Builder idleThresholdSeconds(int sec) { this.idleThresholdSeconds = sec; return this; }
        public Builder minSampleCount(int count) { this.minSampleCount = count; return this; }
        public Builder synergyThreshold(float th) { this.synergyThreshold = th; return this; }
        public Builder hopfieldTemperature(float temp) { this.hopfieldTemperature = temp; return this; }
        public Builder soulPriorPreference(float[] prior) { this.soulPriorPreference = prior; return this; }

        public Builder simulationTimeMs(long simTime) { this.simulationTimeMs = simTime; return this; }
        public Builder queryTau(float[] tau) { this.queryTau = tau; return this; }
        public Builder spacetimeMode(com.spectrayan.spector.core.spacetime.SpacetimeSimulationMode mode) { this.spacetimeMode = mode; return this; }
        public Builder recencyLambda(float lambda) { this.recencyLambda = lambda; return this; }
        public Builder allowFuture(boolean allow) { this.allowFuture = allow; return this; }
        public Builder candidateSeeds(List<com.spectrayan.spector.memory.model.CognitiveResult> seeds) { this.candidateSeeds = seeds; return this; }

        public WanderSignal build() {
            return new WanderSignal(this);
        }
    }
}
