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
package com.spectrayan.spector.memory.model;

import java.util.Arrays;

public record BlendshapeVector(float[] blendshapes, float[] gazeVector, float[] headPose, String primaryExpression) {
    public static final BlendshapeVector NEUTRAL = new BlendshapeVector(new float[52], new float[3], new float[3], "NEUTRAL");

    public BlendshapeVector(float[] blendshapes, float[] gazeVector, float[] headPose, String primaryExpression) {
        this.blendshapes = blendshapes != null ? Arrays.copyOf(blendshapes, blendshapes.length) : new float[52];
        this.gazeVector = gazeVector != null ? Arrays.copyOf(gazeVector, gazeVector.length) : new float[3];
        this.headPose = headPose != null ? Arrays.copyOf(headPose, headPose.length) : new float[3];
        this.primaryExpression = primaryExpression;
    }

    @Override
    public float[] blendshapes() {
        return Arrays.copyOf(blendshapes, blendshapes.length);
    }

    @Override
    public float[] gazeVector() {
        return Arrays.copyOf(gazeVector, gazeVector.length);
    }

    @Override
    public float[] headPose() {
        return Arrays.copyOf(headPose, headPose.length);
    }
}
