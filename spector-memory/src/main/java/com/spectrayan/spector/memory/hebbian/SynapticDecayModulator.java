/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.spectrayan.spector.memory.hebbian;

import com.spectrayan.spector.memory.cortex.TierRouter;
import com.spectrayan.spector.memory.synapse.CognitiveRecordLayout;
import com.spectrayan.spector.memory.synapse.SynapticHeaderConstants;

import java.lang.foreign.MemorySegment;

/**
 * Arousal-modulated decay modulator backed by synaptic header data.
 *
 * <h3>Neuroscience Basis</h3>
 * <p>The amygdala modulates synaptic consolidation via noradrenergic signaling:
 * emotionally arousing memories have stronger synaptic traces that resist decay.
 * High-importance memories (ACT-R base-level activation) also maintain stronger
 * pathways between nodes.</p>
 *
 * <h3>Implementation</h3>
 * <p>Reads importance, arousal, and valence from the episodic partition's
 * synaptic headers. For each memory slot, computes a composite modifier:</p>
 * <pre>
 *   modifier = 1.0 + 0.3 * importance + 0.2 * arousal + 0.1 * abs(valence)
 * </pre>
 * <p>Result is clamped to [0.5, 2.0] by HebbianGraph. A modifier of 1.5 means
 * edges to this memory decay 33% slower than the base rate.</p>
 *
 * <p><b>Usage:</b> Created once before each decay cycle by {@link com.spectrayan.spector.memory.ReflectionOrchestrator},
 * then discarded. Captures a snapshot of the partition state at creation time.</p>
 *
 * @see HebbianGraph.DecayModulator
 */
public final class SynapticDecayModulator implements HebbianGraph.DecayModulator {

    private final float[] modifiers;

    /**
     * Creates a modulator by pre-reading synaptic headers from the episodic partition.
     *
     * <p>Pre-reads all values into a float array for O(1) lookup during the decay loop.
     * Cost: O(partitionCount) — typically < 100K entries, < 1ms.</p>
     *
     * @param tierRouter the current tier router (provides access to episodic partition)
     * @param capacity   HebbianGraph capacity (number of slots)
     */
    public SynapticDecayModulator(TierRouter tierRouter, int capacity) {
        this.modifiers = new float[capacity];
        java.util.Arrays.fill(modifiers, 1.0f);

        var episodic = tierRouter.episodic();
        if (episodic == null) return;

        CognitiveRecordLayout layout = episodic.layout();
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

                // Normalize: importance is [0,1], arousal/valence are signed bytes [-128,127]
                float normArousal = (arousal & 0xFF) / 255.0f;  // unsigned [0,1]
                float normValence = Math.abs(valence) / 127.0f; // absolute [0,1]

                // Composite: baseline 1.0 + weighted signals
                float modifier = 1.0f
                        + 0.3f * importance     // ACT-R base-level activation
                        + 0.2f * normArousal    // Amygdala noradrenergic modulation
                        + 0.1f * normValence;   // Emotional valence (polarity-independent)

                modifiers[i] = modifier;
            } catch (RuntimeException e) {
                // Skip corrupted entries — use default modifier
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
