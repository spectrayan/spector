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

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;

import java.util.Arrays;

/**
 * Immutable record representing the multi-dimensional affective state of the agent.
 *
 * <p>Biological analog: The anterior insular cortex interoceptive representation.
 * It encodes valence, arousal, and dominance (VAD), along with specific physiological
 * or internal channels (hunger, fatigue, social need, etc.).</p>
 */
public record InteroceptiveState(
    float valence,        // pleasure-displeasure [-1, 1]
    float arousal,        // activation-deactivation [-1, 1] 
    float dominance,      // control-submission [-1, 1]
    float[] channels,     // K interoceptive channels each [-1, 1]
    long epochMillis,     // when this state was computed
    int version           // monotonically increasing version
) {
    /** Neutral state constant with empty channels. */
    public static final InteroceptiveState NEUTRAL = new InteroceptiveState(0f, 0f, 0f, new float[0], 0L, 0);

    /**
     * Compact constructor: performs defensive copy of channels and validates ranges.
     */
    public InteroceptiveState {
        if (Float.isNaN(valence) || Float.isNaN(arousal) || Float.isNaN(dominance)) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "VAD dimensions cannot be NaN");
        }
        channels = channels == null ? new float[0] : Arrays.copyOf(channels, channels.length);
        for (int i = 0; i < channels.length; i++) {
            if (Float.isNaN(channels[i])) {
                throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Channel at index " + i + " cannot be NaN");
            }
        }
    }

    /**
     * @return a flattened float array of [valence, arousal, dominance, channels...] for SIMD processing
     */
    public float[] toVector() {
        float[] vec = new float[3 + channels.length];
        vec[0] = valence;
        vec[1] = arousal;
        vec[2] = dominance;
        System.arraycopy(channels, 0, vec, 3, channels.length);
        return vec;
    }

    /**
     * Factory method to create an InteroceptiveState from a flat float array.
     *
     * @param vec vector containing [valence, arousal, dominance, channels...]
     * @param epochMillis computation timestamp
     * @param version monotonically increasing version
     * @return new InteroceptiveState
     */
    public static InteroceptiveState fromVector(float[] vec, long epochMillis, int version) {
        if (vec == null || vec.length < 3) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Vector must have at least 3 dimensions");
        }
        float[] c = new float[vec.length - 3];
        System.arraycopy(vec, 3, c, 0, c.length);
        return new InteroceptiveState(vec[0], vec[1], vec[2], c, epochMillis, version);
    }

    /**
     * @return the total number of dimensions (3 + number of channels)
     */
    public int dimensions() {
        return 3 + channels.length;
    }

    /**
     * @return a new InteroceptiveState with all values clamped to the range [-1, 1]
     */
    public InteroceptiveState clamp() {
        float v = Math.max(-1f, Math.min(1f, valence));
        float a = Math.max(-1f, Math.min(1f, arousal));
        float d = Math.max(-1f, Math.min(1f, dominance));
        
        float[] c = new float[channels.length];
        for (int i = 0; i < channels.length; i++) {
            c[i] = Math.max(-1f, Math.min(1f, channels[i]));
        }
        
        return new InteroceptiveState(v, a, d, c, epochMillis, version);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof InteroceptiveState that)) return false;
        return Float.compare(that.valence, valence) == 0 &&
               Float.compare(that.arousal, arousal) == 0 &&
               Float.compare(that.dominance, dominance) == 0 &&
               epochMillis == that.epochMillis &&
               version == that.version &&
               Arrays.equals(channels, that.channels);
    }

    @Override
    public int hashCode() {
        int result = Float.hashCode(valence);
        result = 31 * result + Float.hashCode(arousal);
        result = 31 * result + Float.hashCode(dominance);
        result = 31 * result + Arrays.hashCode(channels);
        result = 31 * result + Long.hashCode(epochMillis);
        result = 31 * result + Integer.hashCode(version);
        return result;
    }
    
    @Override
    public String toString() {
        return "InteroceptiveState{" +
                "valence=" + valence +
                ", arousal=" + arousal +
                ", dominance=" + dominance +
                ", channels=" + Arrays.toString(channels) +
                ", epochMillis=" + epochMillis +
                ", version=" + version +
                '}';
    }
}
