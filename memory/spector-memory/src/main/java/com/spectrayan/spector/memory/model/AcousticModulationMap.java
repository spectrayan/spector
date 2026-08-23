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

public record AcousticModulationMap(
        float pitchArousalSensitivity,
        float pitchValenceSensitivity,
        float tempoArousalSensitivity,
        float tempoValenceSensitivity,
        float varianceArousalSensitivity,
        float breathinessDominanceSensitivity,
        float assertivenessDominanceSensitivity
) {
    public static final AcousticModulationMap DEFAULT = new AcousticModulationMap(
            25.0f, 10.0f, 0.25f, 0.05f, 0.35f, 0.40f, 0.30f
    );
}
