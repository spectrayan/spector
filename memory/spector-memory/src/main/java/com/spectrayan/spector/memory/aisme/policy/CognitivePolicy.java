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
package com.spectrayan.spector.memory.aisme.policy;

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record CognitivePolicy(
    String id,
    String name,
    PolicyType policyType,
    float[] predictedObservationMean,
    float[] predictedObservationPrecision,
    Map<String, Object> metadata
) {
    public CognitivePolicy {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(policyType, "policyType cannot be null");
        Objects.requireNonNull(predictedObservationMean, "predictedObservationMean cannot be null");
        Objects.requireNonNull(predictedObservationPrecision, "predictedObservationPrecision cannot be null");
        if (predictedObservationMean.length != predictedObservationPrecision.length) {
            throw new IllegalArgumentException("Mean and precision arrays must have same length");
        }
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static CognitivePolicy of(PolicyType type, float[] mean, float[] precision) {
        return new CognitivePolicy(
            UUID.randomUUID().toString(),
            type.name(),
            type,
            mean,
            precision,
            Map.of()
        );
    }
}
