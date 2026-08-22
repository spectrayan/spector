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
package com.spectrayan.spector.memory.aisme.homeostasis;

import com.spectrayan.spector.memory.model.AgentSoul;
import com.spectrayan.spector.memory.model.AgentSoul.EmotionalBaseline;
import com.spectrayan.spector.memory.model.CognitiveProfile;

/**
 * Factory that derives the regulation dynamics matrix from existing Spector components.
 *
 * <h3>Biological Analog: Personality and Neurological Baselines</h3>
 * <p>Just as genetics and neuroplasticity shape how quickly someone calms down
 * after being startled, or how deeply they ruminate on past interactions, this
 * regulator tunes the differential equations (A matrix) that govern emotional
 * decay and stability based on the agent's CognitiveProfile and AgentSoul.</p>
 */
public final class EmotionalRegulator {

    private EmotionalRegulator() {
        // Factory class, do not instantiate
    }

    /**
     * Derives the A_person dynamics matrix representing the internal regulation rate.
     * The diagonal of A encodes decay rates toward equilibrium (negative values = stable).
     *
     * @param baseline The emotional baseline defining standard equilibrium
     * @param profile The cognitive profile which modulates decay rates
     * @param dimensions The number of dimensions of the interoceptive state
     * @return The derived A_person matrix
     */
    public static float[][] deriveRegulationMatrix(EmotionalBaseline baseline, CognitiveProfile profile, int dimensions) {
        float[][] aPerson = new float[dimensions][dimensions];
        
        // Base decay factor (negative for stability/decay back to equilibrium)
        float decayFactor = -0.5f;

        // Modulate based on cognitive profile
        if (profile != null) {
            switch (profile) {
                case HYPERFOCUS:
                    // Slow decay (rumination)
                    decayFactor = -0.1f;
                    break;
                case DIVERGENT:
                    // Fast decay (rapid mood shifts)
                    decayFactor = -0.9f;
                    break;
                case BALANCED:
                default:
                    // Moderate
                    decayFactor = -0.5f;
                    break;
            }
        }

        // Apply decay to diagonal
        for (int i = 0; i < dimensions; i++) {
            aPerson[i][i] = decayFactor;
        }

        return aPerson;
    }

    /**
     * Converts an emotional baseline into a float vector representing the equilibrium point.
     *
     * @param baseline The baseline to convert
     * @param totalDimensions The total dimensions in the vector
     * @return The equilibrium state vector
     */
    public static float[] deriveEquilibrium(EmotionalBaseline baseline, int totalDimensions) {
        float[] eq = new float[totalDimensions];
        if (totalDimensions >= 2 && baseline != null) {
            // Map byte range [-128, 127] to float range [-1.0, 1.0]
            eq[0] = baseline.defaultValence() / 127.0f;  // valence
            eq[1] = baseline.defaultArousal() / 127.0f;  // arousal
            // dominance defaults to 0 (neutral)
        }
        return eq;
    }

    /**
     * Factory method to create a fully configured HomeostaticCore from an agent's soul.
     *
     * @param soul The agent soul defining the core personality
     * @param profile The cognitive profile
     * @param interoceptiveChannels The number of extra interoceptive channels (default: 4)
     * @return A configured HomeostaticCore
     */
    public static HomeostaticCore createFromSoul(AgentSoul soul, CognitiveProfile profile, int interoceptiveChannels) {
        int totalDimensions = 3 + interoceptiveChannels; // VAD + channels
        
        float[][] aPerson = deriveRegulationMatrix(soul != null ? soul.emotionalBaseline() : null, profile, totalDimensions);
        
        // Initialize default B (input), C (recall) matrices and sigma (noise)
        float[][] bInput = new float[totalDimensions][totalDimensions];
        float[][] cRecall = new float[totalDimensions][totalDimensions];
        float[] sigma = new float[totalDimensions];
        
        // Simple identity mapping with some default weights
        for (int i = 0; i < totalDimensions; i++) {
            bInput[i][i] = 0.2f;
            cRecall[i][i] = 0.1f;
            sigma[i] = 0.01f;
        }

        return new HomeostaticCore(aPerson, bInput, cRecall, sigma);
    }
}
