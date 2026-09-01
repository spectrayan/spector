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
package com.spectrayan.spector.memory.synapse.scan;

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

import com.spectrayan.spector.memory.kernel.layout.CognitiveRecordLayout.CognitiveHeader;
import com.spectrayan.spector.memory.synapse.CognitiveScorer.ScoredRecord;

import java.util.ArrayList;
import java.util.List;

/**
 * A fixed-capacity min-heap backed by parallel primitive arrays for zero-allocation top-K scoring.
 *
 * <p>Header fields are stored in flat primitive arrays during the scan loop. {@link CognitiveHeader}
 * and {@link ScoredRecord} objects are created ONLY when {@link #drain()} is called on the surviving top-K.</p>
 */
public final class FlatMinHeap {

    private final int capacity;
    private int size;

    // Core fields
    private final float[] scores;
    private final long[] offsets;
    private final int[] indices;

    // Header fields (stored flat, assembled into CognitiveHeader on drain)
    private final long[] timestamps;
    private final long[] synapticTags;
    private final float[] exactNorms;
    private final float[] importances;
    private final int[] agentRecallCounts;
    private final short[] centroidIds;
    private final byte[] valences;
    private final byte[] flags;

    /**
     * Creates a new flat min-heap with the given capacity.
     *
     * @param capacity top-K capacity
     */
    public FlatMinHeap(final int capacity) {
        this.capacity = capacity;
        this.size = 0;
        final int len = capacity + 1; // +1 for sift workspace
        this.scores = new float[len];
        this.offsets = new long[len];
        this.indices = new int[len];
        this.timestamps = new long[len];
        this.synapticTags = new long[len];
        this.exactNorms = new float[len];
        this.importances = new float[len];
        this.agentRecallCounts = new int[len];
        this.centroidIds = new short[len];
        this.valences = new byte[len];
        this.flags = new byte[len];
    }

    /**
     * Returns true if the given score should be inserted (heap not full, or beats minimum).
     *
     * @param score score to check
     * @return true if score qualifies for heap insertion
     */
    public boolean shouldInsert(final float score) {
        return size < capacity || score > scores[0];
    }

    /**
     * Inserts or replaces the minimum entry. Caller must check {@link #shouldInsert(float)} first.
     */
    public void insert(
            final float score, final long offset, final int index,
            final long timestamp, final long tags, final float exactNorm, final float importance,
            final int agentRecallCount, final short centroidId, final byte valence, final byte flag) {
        if (size < capacity) {
            final int idx = size;
            set(idx, score, offset, index, timestamp, tags, exactNorm,
                    importance, agentRecallCount, centroidId, valence, flag);
            size++;
            siftUp(idx);
        } else {
            // Replace root (minimum)
            set(0, score, offset, index, timestamp, tags, exactNorm,
                    importance, agentRecallCount, centroidId, valence, flag);
            siftDown(0);
        }
    }

    /**
     * Drains the heap into a list of {@link ScoredRecord} objects.
     *
     * @return drained list of scored records
     */
    public List<ScoredRecord> drain() {
        final List<ScoredRecord> results = new ArrayList<>(size);
        for (int h = 0; h < size; h++) {
            final CognitiveHeader header = new CognitiveHeader(
                    timestamps[h], synapticTags[h], exactNorms[h], importances[h],
                    agentRecallCounts[h], centroidIds[h], valences[h], flags[h]);
            results.add(new ScoredRecord(offsets[h], scores[h], indices[h], header));
        }
        return results;
    }

    private void set(
            final int idx, final float score, final long offset, final int index,
            final long timestamp, final long tags, final float exactNorm, final float importance,
            final int agentRecallCount, final short centroidId, final byte valence, final byte flag) {
        scores[idx] = score;
        offsets[idx] = offset;
        indices[idx] = index;
        timestamps[idx] = timestamp;
        synapticTags[idx] = tags;
        exactNorms[idx] = exactNorm;
        importances[idx] = importance;
        agentRecallCounts[idx] = agentRecallCount;
        centroidIds[idx] = centroidId;
        valences[idx] = valence;
        flags[idx] = flag;
    }

    private void siftUp(int idx) {
        while (idx > 0) {
            final int parent = (idx - 1) >>> 1;
            if (scores[idx] >= scores[parent]) {
                break;
            }
            swap(idx, parent);
            idx = parent;
        }
    }

    private void siftDown(int idx) {
        while (true) {
            final int left = 2 * idx + 1;
            final int right = left + 1;
            int smallest = idx;
            if (left < size && scores[left] < scores[smallest]) {
                smallest = left;
            }
            if (right < size && scores[right] < scores[smallest]) {
                smallest = right;
            }
            if (smallest == idx) {
                break;
            }
            swap(idx, smallest);
            idx = smallest;
        }
    }

    private void swap(final int a, final int b) {
        final float ts = scores[a]; scores[a] = scores[b]; scores[b] = ts;
        final long to = offsets[a]; offsets[a] = offsets[b]; offsets[b] = to;
        final int ti = indices[a]; indices[a] = indices[b]; indices[b] = ti;
        final long tt = timestamps[a]; timestamps[a] = timestamps[b]; timestamps[b] = tt;
        final long tg = synapticTags[a]; synapticTags[a] = synapticTags[b]; synapticTags[b] = tg;
        final float tn = exactNorms[a]; exactNorms[a] = exactNorms[b]; exactNorms[b] = tn;
        final float tp = importances[a]; importances[a] = importances[b]; importances[b] = tp;
        final int tr = agentRecallCounts[a]; agentRecallCounts[a] = agentRecallCounts[b]; agentRecallCounts[b] = tr;
        final short tc = centroidIds[a]; centroidIds[a] = centroidIds[b]; centroidIds[b] = tc;
        final byte tv = valences[a]; valences[a] = valences[b]; valences[b] = tv;
        final byte tf = flags[a]; flags[a] = flags[b]; flags[b] = tf;
    }
}
