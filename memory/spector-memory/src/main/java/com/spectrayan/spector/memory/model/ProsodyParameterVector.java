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

import java.util.Map;

public record ProsodyParameterVector(
        float targetF0Hz,
        float pitchDeltaHz,
        int targetWordsPerMinute,
        float tempoMultiplier,
        float pitchVariance,
        float breathiness,
        float assertiveness,
        String emotionalTone,
        String ssmlTags,
        Map<String, Object> vendorParameters
) {
    public ProsodyParameterVector {
        vendorParameters = vendorParameters != null ? Map.copyOf(vendorParameters) : Map.of();
    }
}
