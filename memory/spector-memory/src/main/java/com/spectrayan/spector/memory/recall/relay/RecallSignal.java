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
package com.spectrayan.spector.memory.recall.relay;

import com.spectrayan.spector.commons.pathway.DivergentCapable;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.RecallOptions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Signal carrying the state of a recall operation through the memory pipeline.
 *
 * <p>This signal implements {@link DivergentCapable} to support parallel forks for 
 * operations like hybrid text/vector search, merging the candidates back together.</p>
 */
public final class RecallSignal implements DivergentCapable<RecallSignal> {

    // Immutable inputs
    private final String rawQuery;
    private final RecallOptions options;
    private final long timestampMs;

    // Mutable working state
    private float[] queryVector;
    private final List<CognitiveResult> candidates = new ArrayList<>();
    private boolean textSearchExecuted = false;
    private boolean rrfFused = false;
    private float effectiveTemperature = 1.0f;

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
        return new RecallSignal(rawQuery, null, options, System.currentTimeMillis());
    }

    /**
     * Creates a new signal for a pre-embedded vector query.
     *
     * @param queryVector the vector query
     * @param options     the recall options
     * @return a new recall signal
     */
    public static RecallSignal forVectorQuery(final float[] queryVector, final RecallOptions options) {
        return new RecallSignal(null, queryVector, options, System.currentTimeMillis());
    }

    @Override
    public RecallSignal fork() {
        return new RecallSignal(rawQuery, queryVector, options, timestampMs);
    }

    @Override
    public void merge(final List<RecallSignal> forks) {
        for (final RecallSignal fork : forks) {
            this.candidates.addAll(fork.candidates);
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
