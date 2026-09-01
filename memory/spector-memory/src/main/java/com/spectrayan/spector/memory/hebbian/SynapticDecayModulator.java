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
package com.spectrayan.spector.memory.hebbian;

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

import com.spectrayan.spector.memory.persist.PartitionManager;
import com.spectrayan.spector.memory.cortex.CognitiveMemoryRouter;
import com.spectrayan.spector.memory.index.IndexRecordMemory.MemoryLocation;
import com.spectrayan.spector.memory.index.MemoryIndex;
import com.spectrayan.spector.memory.kernel.layout.CognitiveRecordLayout;
import com.spectrayan.spector.memory.kernel.layout.CognitiveRecordLayout.CognitiveHeader;
import com.spectrayan.spector.memory.kernel.layout.SynapticHeaderConstants;

import java.lang.foreign.MemorySegment;

/**
 * Arousal-modulated decay modulator backed by synaptic header data across all partitions.
 *
 * <h3>Neuroscience Basis</h3>
 * <p>The amygdala modulates synaptic consolidation via noradrenergic signaling:
 * emotionally arousing memories have stronger synaptic traces that resist decay.
 * High-importance memories (ACT-R base-level activation) also maintain stronger
 * pathways between nodes.</p>
 *
 * <h3>Implementation</h3>
 * <p>Reads importance, arousal, and valence from synaptic headers across frozen and active
 * partitions. For each graph slot, computes a composite modifier:</p>
 * <pre>
 *   modifier = 1.0 + 0.3 * importance + 0.2 * arousal + 0.1 * abs(valence)
 * </pre>
 * <p>Result is clamped to [0.5, 2.0] by HebbianGraph. A modifier of 1.5 means
 * edges to this memory decay 33% slower than the base rate.</p>
 *
 * <p><b>Usage:</b> Created once before each decay cycle by {@link com.spectrayan.spector.memory.reflect.ReflectionOrchestrator},
 * then discarded. Captures a snapshot of the partition state at creation time.</p>
 *
 * @see DecayModulator
 */
public final class SynapticDecayModulator implements DecayModulator {

    private final float[] modifiers;

    /**
     * Creates a partition-aware modulator by resolving synaptic headers across frozen and active partitions.
     *
     * @param partitionManager partition manager resolving colocated partition routers
     * @param index            memory index mapping graph slots to memory IDs and locations
     * @param capacity         HebbianGraph capacity (number of slots)
     */
    public SynapticDecayModulator(PartitionManager partitionManager, MemoryIndex index, int capacity) {
        this.modifiers = new float[capacity];
        java.util.Arrays.fill(modifiers, 1.0f);

        if (partitionManager == null || index == null) return;

        for (int s = 0; s < capacity; s++) {
            try {
                String id = index.idAt(s);
                if (id == null) continue;

                MemoryLocation loc = index.locate(id);
                if (loc == null) continue;

                CognitiveMemoryRouter router = partitionManager.routerFor(loc.colocatedPartition());
                if (router == null) continue;

                CognitiveMemoryRouter.CognitiveRecordBody body = router.readRecordBody(loc, false);
                if (body == null || body.header() == null) continue;

                CognitiveHeader header = body.header();
                if (SynapticHeaderConstants.isTombstoned(header.flags())) continue;

                float normArousal = (header.arousal() & 0xFF) / 255.0f;  // unsigned [0,1]
                float normValence = Math.abs(header.valence()) / 127.0f; // absolute [0,1]

                float modifier = 1.0f
                        + 0.3f * header.importance()  // ACT-R base-level activation
                        + 0.2f * normArousal          // Amygdala noradrenergic modulation
                        + 0.1f * normValence;         // Emotional valence (polarity-independent)

                modifiers[s] = modifier;
            } catch (RuntimeException e) {
                // Skip corrupted entries — default modifier remains 1.0f
                modifiers[s] = 1.0f;
            }
        }
    }

    /**
     * Creates a modulator by pre-reading synaptic headers from a single cognitive router (legacy/fallback).
     *
     * @param cognitiveRouter the current cognitive memory router
     * @param capacity        HebbianGraph capacity (number of slots)
     */
    public SynapticDecayModulator(CognitiveMemoryRouter cognitiveRouter, int capacity) {
        this.modifiers = new float[capacity];
        java.util.Arrays.fill(modifiers, 1.0f);

        if (cognitiveRouter == null) return;
        var episodic = cognitiveRouter.episodic();
        if (episodic == null) return;

        CognitiveRecordLayout layout = episodic.cognitiveLayout();
        MemorySegment segment = episodic.segment();
        int count = Math.min(episodic.totalRecords(), capacity);

        for (int i = 0; i < count; i++) {
            try {
                long offset = episodic.recordOffset(i);
                byte flags = layout.readFlags(segment, offset);
                if (SynapticHeaderConstants.isTombstoned(flags)) continue;

                float importance = layout.readImportance(segment, offset);
                byte arousal = layout.readArousal(segment, offset);
                byte valence = layout.readValence(segment, offset);

                float normArousal = (arousal & 0xFF) / 255.0f;
                float normValence = Math.abs(valence) / 127.0f;

                float modifier = 1.0f
                        + 0.3f * importance
                        + 0.2f * normArousal
                        + 0.1f * normValence;

                modifiers[i] = modifier;
            } catch (RuntimeException e) {
                modifiers[i] = 1.0f;
            }
        }
    }

    @Override
    public float modulateDecay(int nodeIndex) {
        if (nodeIndex < 0 || nodeIndex >= modifiers.length) return 1.0f;
        return modifiers[nodeIndex];
    }
}
