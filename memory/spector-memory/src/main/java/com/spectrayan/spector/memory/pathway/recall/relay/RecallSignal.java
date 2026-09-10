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
package com.spectrayan.spector.memory.pathway.recall.relay;

import com.spectrayan.spector.commons.pathway.DivergentCapable;
import com.spectrayan.spector.commons.pathway.RelayTrace;
import com.spectrayan.spector.commons.pathway.TraceableSignal;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.RecallOptions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Signal carrying the state of a recall operation through the memory pipeline.
 *
 * <p>This signal implements {@link DivergentCapable} to support parallel forks for 
 * operations like hybrid text/vector search, and {@link TraceableSignal} to capture
 * fine-grained relay execution diagnostics.</p>
 */
public final class RecallSignal implements DivergentCapable<RecallSignal>, TraceableSignal {

    // Immutable inputs
    private final String rawQuery;
    private final RecallOptions options;
    private final long timestampMs;

    // Mutable working state
    private float[] queryVector;
    private float[] queryTau;
    private long queryTimeMs;
    private final List<CognitiveResult> candidates = new ArrayList<>();
    private boolean textSearchExecuted = false;
    private boolean rrfFused = false;
    private float effectiveTemperature = 1.0f;
    private final List<RelayTrace> traces = new ArrayList<>();
    private final ReentrantLock tracesLock = new ReentrantLock();

    private final java.util.Map<String, Object> attributes = new java.util.concurrent.ConcurrentHashMap<>();
    private com.spectrayan.spector.kernel.api.NamespaceKernel kernel;
    private com.spectrayan.spector.memory.cortex.PartitionRegistry partitionRegistry;
    private com.spectrayan.spector.memory.cortex.index.MemoryIndex index;
    private com.spectrayan.spector.memory.cortex.MemoryBM25Index bm25Index;
    private com.spectrayan.spector.kernel.store.HebbianGraphBase hebbianGraph;
    private com.spectrayan.spector.kernel.store.TemporalChainMemory temporalChain;
    private com.spectrayan.spector.memory.graph.temporal.TemporalKnowledgeGraph temporalKnowledgeGraph;
    private com.spectrayan.spector.memory.graph.EntityDirectory entityDirectory;
    private com.spectrayan.spector.kernel.store.HyperEntityGraphMemory hyperEntityGraph;
    private com.spectrayan.spector.core.quantization.ScalarQuantizer quantizer;
    private com.spectrayan.spector.kernel.store.CoActivationMemory coActivationTracker;
    private com.spectrayan.spector.memory.neuromod.inhibition.SuppressionSet suppressionSet;
    private com.spectrayan.spector.memory.neuromod.habituation.HabituationPenalty habituationPenalty;
    private com.spectrayan.spector.memory.neuromod.dopamine.SurpriseDetector surpriseDetector;
    private com.spectrayan.spector.memory.cortex.prospective.ProspectiveScheduler prospectiveScheduler;

    // Output
    private List<CognitiveResult> finalizedResults = Collections.emptyList();

    // Private constructor
    private RecallSignal(final String rawQuery, final float[] queryVector, final RecallOptions options, final long timestampMs) {
        this.rawQuery = rawQuery;
        this.queryVector = queryVector;
        this.options = Objects.requireNonNull(options);
        this.timestampMs = timestampMs;
    }

    /**
     * Creates a new signal for a text-based query.
     *
     * @param rawQuery the raw query string
     * @param options  the recall options
     * @return a new recall signal
     */
    public static RecallSignal forTextQuery(final String rawQuery, final RecallOptions options) {
        long ts = options != null && options.replayTimestamp() != null
                ? options.replayTimestamp().toEpochMilli()
                : System.currentTimeMillis();
        RecallSignal signal = new RecallSignal(rawQuery, null, options, ts);
        signal.setQueryTimeMs(ts);
        return signal;
    }

    /**
     * Creates a new signal for a pre-embedded vector query.
     *
     * @param queryVector the vector query
     * @param options     the recall options
     * @return a new recall signal
     */
    public static RecallSignal forVectorQuery(final float[] queryVector, final RecallOptions options) {
        long ts = options != null && options.replayTimestamp() != null
                ? options.replayTimestamp().toEpochMilli()
                : System.currentTimeMillis();
        RecallSignal signal = new RecallSignal(null, queryVector, options, ts);
        signal.setQueryTimeMs(ts);
        return signal;
    }

    @Override
    public RecallSignal fork() {
        final RecallSignal fork = new RecallSignal(rawQuery, queryVector, options, timestampMs);
        fork.queryTau = this.queryTau;
        fork.queryTimeMs = this.queryTimeMs;
        fork.candidates.addAll(this.candidates);
        fork.textSearchExecuted = this.textSearchExecuted;
        fork.rrfFused = this.rrfFused;
        fork.effectiveTemperature = this.effectiveTemperature;
        fork.kernel = this.kernel;
        fork.partitionRegistry = this.partitionRegistry;
        fork.index = this.index;
        fork.bm25Index = this.bm25Index;
        fork.hebbianGraph = this.hebbianGraph;
        fork.temporalChain = this.temporalChain;
        fork.temporalKnowledgeGraph = this.temporalKnowledgeGraph;
        fork.entityDirectory = this.entityDirectory;
        fork.hyperEntityGraph = this.hyperEntityGraph;
        fork.quantizer = this.quantizer;
        fork.coActivationTracker = this.coActivationTracker;
        fork.suppressionSet = this.suppressionSet;
        fork.habituationPenalty = this.habituationPenalty;
        fork.surpriseDetector = this.surpriseDetector;
        fork.prospectiveScheduler = this.prospectiveScheduler;
        fork.attributes.putAll(this.attributes);
        this.tracesLock.lock();
        try {
            fork.tracesLock.lock();
            try {
                fork.traces.addAll(this.traces);
            } finally {
                fork.tracesLock.unlock();
            }
        } finally {
            this.tracesLock.unlock();
        }
        return fork;
    }

    /**
     * Returns the target namespace kernel for this recall operation, if bound (R13.6).
     */
    public com.spectrayan.spector.kernel.api.NamespaceKernel kernel() {
        return kernel;
    }

    /**
     * Binds the target namespace kernel to this recall operation (R13.6).
     */
    public void kernel(final com.spectrayan.spector.kernel.api.NamespaceKernel kernel) {
        this.kernel = kernel;
        if (kernel != null) {
            this.attributes.put("kernel", kernel);
        }
    }

    public com.spectrayan.spector.memory.cortex.PartitionRegistry partitionRegistry() { return partitionRegistry; }
    public RecallSignal partitionRegistry(com.spectrayan.spector.memory.cortex.PartitionRegistry pr) { this.partitionRegistry = pr; return this; }

    public com.spectrayan.spector.memory.cortex.index.MemoryIndex index() { return index; }
    public RecallSignal index(com.spectrayan.spector.memory.cortex.index.MemoryIndex idx) { this.index = idx; return this; }

    public com.spectrayan.spector.memory.cortex.MemoryBM25Index bm25Index() { return bm25Index; }
    public RecallSignal bm25Index(com.spectrayan.spector.memory.cortex.MemoryBM25Index bm25) { this.bm25Index = bm25; return this; }

    public com.spectrayan.spector.kernel.store.HebbianGraphBase hebbianGraph() { return hebbianGraph; }
    public RecallSignal hebbianGraph(com.spectrayan.spector.kernel.store.HebbianGraphBase hg) { this.hebbianGraph = hg; return this; }

    public com.spectrayan.spector.kernel.store.TemporalChainMemory temporalChain() { return temporalChain; }
    public RecallSignal temporalChain(com.spectrayan.spector.kernel.store.TemporalChainMemory tc) { this.temporalChain = tc; return this; }

    public com.spectrayan.spector.memory.graph.temporal.TemporalKnowledgeGraph temporalKnowledgeGraph() { return temporalKnowledgeGraph; }
    public RecallSignal temporalKnowledgeGraph(com.spectrayan.spector.memory.graph.temporal.TemporalKnowledgeGraph tkg) { this.temporalKnowledgeGraph = tkg; return this; }

    public com.spectrayan.spector.memory.graph.EntityDirectory entityDirectory() { return entityDirectory; }
    public RecallSignal entityDirectory(com.spectrayan.spector.memory.graph.EntityDirectory ed) { this.entityDirectory = ed; return this; }

    public com.spectrayan.spector.kernel.store.HyperEntityGraphMemory hyperEntityGraph() { return hyperEntityGraph; }
    public RecallSignal hyperEntityGraph(com.spectrayan.spector.kernel.store.HyperEntityGraphMemory heg) { this.hyperEntityGraph = heg; return this; }

    public com.spectrayan.spector.core.quantization.ScalarQuantizer quantizer() { return quantizer; }
    public RecallSignal quantizer(com.spectrayan.spector.core.quantization.ScalarQuantizer q) { this.quantizer = q; return this; }

    public com.spectrayan.spector.kernel.store.CoActivationMemory coActivationTracker() { return coActivationTracker; }
    public RecallSignal coActivationTracker(com.spectrayan.spector.kernel.store.CoActivationMemory cat) { this.coActivationTracker = cat; return this; }

    public com.spectrayan.spector.memory.neuromod.inhibition.SuppressionSet suppressionSet() { return suppressionSet; }
    public RecallSignal suppressionSet(com.spectrayan.spector.memory.neuromod.inhibition.SuppressionSet ss) { this.suppressionSet = ss; return this; }

    public com.spectrayan.spector.memory.neuromod.habituation.HabituationPenalty habituationPenalty() { return habituationPenalty; }
    public RecallSignal habituationPenalty(com.spectrayan.spector.memory.neuromod.habituation.HabituationPenalty hp) { this.habituationPenalty = hp; return this; }

    public com.spectrayan.spector.memory.neuromod.dopamine.SurpriseDetector surpriseDetector() { return surpriseDetector; }
    public RecallSignal surpriseDetector(com.spectrayan.spector.memory.neuromod.dopamine.SurpriseDetector sd) { this.surpriseDetector = sd; return this; }

    public com.spectrayan.spector.memory.cortex.prospective.ProspectiveScheduler prospectiveScheduler() { return prospectiveScheduler; }
    public RecallSignal prospectiveScheduler(com.spectrayan.spector.memory.cortex.prospective.ProspectiveScheduler ps) { this.prospectiveScheduler = ps; return this; }

    /**
     * Returns the mutable contextual attributes map for inter-relay parameter passing.
     */
    public java.util.Map<String, Object> attributes() {
        return attributes;
    }

    @Override
    public void merge(final List<RecallSignal> forks) {
        this.candidates.clear();
        for (final RecallSignal fork : forks) {
            this.candidates.addAll(fork.candidates);
            if (fork.textSearchExecuted) {
                this.textSearchExecuted = true;
            }
            if (fork.rrfFused) {
                this.rrfFused = true;
            }
            List<RelayTrace> forkTraces = fork.traces();
            this.tracesLock.lock();
            try {
                for (final RelayTrace trace : forkTraces) {
                    if (!this.traces.contains(trace)) {
                        this.traces.add(trace);
                    }
                }
            } finally {
                this.tracesLock.unlock();
            }
        }
    }

    @Override
    public boolean isTraceEnabled() {
        return options.enableTrace();
    }

    @Override
    public void recordTrace(final RelayTrace trace) {
        if (trace != null) {
            tracesLock.lock();
            try {
                traces.add(trace);
            } finally {
                tracesLock.unlock();
            }
        }
    }

    @Override
    public List<RelayTrace> traces() {
        tracesLock.lock();
        try {
            return List.copyOf(traces);
        } finally {
            tracesLock.unlock();
        }
    }

    /**
     * Returns the raw query string.
     *
     * @return the raw query string, or null if this is a vector-only query
     */
    public String rawQuery() {
        return rawQuery;
    }

    /**
     * Returns the recall options.
     *
     * @return the recall options
     */
    public RecallOptions options() {
        return options;
    }

    /**
     * Returns the creation timestamp of this signal.
     *
     * @return the timestamp when this signal was created in milliseconds
     */
    public long timestampMs() {
        return timestampMs;
    }

    /**
     * Returns the query vector.
     *
     * @return the query vector, or null if not yet embedded or provided
     */
    public float[] queryVector() {
        return queryVector;
    }

    /**
     * Sets the query vector.
     *
     * @param queryVector the vector to set
     */
    public void setQueryVector(final float[] queryVector) {
        this.queryVector = queryVector;
    }

    /**
     * Returns the 8-dimensional harmonic basis vector for the query timestamp.
     *
     * @return 8-element harmonic vector, or null if not computed
     */
    public float[] queryTau() {
        return queryTau;
    }

    /**
     * Sets the 8-dimensional harmonic basis vector for the query.
     *
     * @param queryTau harmonic basis vector
     */
    public void setQueryTau(final float[] queryTau) {
        this.queryTau = queryTau;
    }

    /**
     * Returns the effective reference query timestamp in epoch milliseconds.
     *
     * @return query timestamp in epoch ms
     */
    public long queryTimeMs() {
        return queryTimeMs;
    }

    /**
     * Sets the effective reference query timestamp in epoch milliseconds.
     *
     * @param queryTimeMs reference query timestamp in epoch ms
     */
    public void setQueryTimeMs(final long queryTimeMs) {
        this.queryTimeMs = queryTimeMs;
    }

    /**
     * Returns the current candidate results.
     *
     * @return the list of candidate results
     */
    public List<CognitiveResult> candidates() {
        return candidates;
    }

    /**
     * Adds multiple candidates to this signal.
     *
     * @param newCandidates the candidates to add
     */
    public void addCandidates(final List<CognitiveResult> newCandidates) {
        this.candidates.addAll(newCandidates);
    }

    /**
     * Sets the candidates for this signal, replacing any existing ones.
     *
     * @param newCandidates the new list of candidates
     */
    public void setCandidates(final List<CognitiveResult> newCandidates) {
        this.candidates.clear();
        this.candidates.addAll(newCandidates);
    }

    /**
     * Checks if text search has been executed.
     *
     * @return true if text search was executed, false otherwise
     */
    public boolean isTextSearchExecuted() {
        return textSearchExecuted;
    }

    /**
     * Sets the execution status of text search.
     *
     * @param textSearchExecuted true if executed, false otherwise
     */
    public void setTextSearchExecuted(final boolean textSearchExecuted) {
        this.textSearchExecuted = textSearchExecuted;
    }

    /**
     * Checks if RRF fusion has been performed.
     *
     * @return true if RRF fusion was performed, false otherwise
     */
    public boolean isRrfFused() {
        return rrfFused;
    }

    /**
     * Sets the status of RRF fusion.
     *
     * @param rrfFused true if fused, false otherwise
     */
    public void setRrfFused(final boolean rrfFused) {
        this.rrfFused = rrfFused;
    }

    /**
     * Returns the effective temperature applied during softmax modulation.
     *
     * @return the effective temperature
     */
    public float effectiveTemperature() {
        return effectiveTemperature;
    }

    /**
     * Sets the effective temperature applied during softmax modulation.
     *
     * @param effectiveTemperature the effective temperature
     */
    public void setEffectiveTemperature(final float effectiveTemperature) {
        this.effectiveTemperature = effectiveTemperature;
    }

    /**
     * Returns the finalized list of cognitive results.
     *
     * @return the finalized results, or an empty list if not yet finalized
     */
    public List<CognitiveResult> finalizedResults() {
        return finalizedResults;
    }

    /**
     * Finalizes the results for this signal.
     *
     * @param finalizedResults the final list of cognitive results
     */
    public void setFinalizedResults(final List<CognitiveResult> finalizedResults) {
        this.finalizedResults = List.copyOf(finalizedResults);
    }
}
