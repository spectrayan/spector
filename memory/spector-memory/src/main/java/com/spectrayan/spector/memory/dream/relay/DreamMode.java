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

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

/**
 * Biological Analog: Different states of sleep/wakefulness and corresponding constraint relaxation.
 *
 * @since 1.4.0
 */
public enum DreamMode {
    REM(2.0f, "weak constraint"),
    DAYDREAM(1.0f, "moderate constraint"),
    THOUGHT_EXPERIMENT(0.5f, "tight constraint");

    private final float defaultTemperature;
    private final String constraintLabel;

    DreamMode(float defaultTemperature, String constraintLabel) {
        this.defaultTemperature = defaultTemperature;
        this.constraintLabel = constraintLabel;
    }

    public float defaultTemperature() {
        return defaultTemperature;
    }

    public String constraintLabel() {
        return constraintLabel;
    }
}
