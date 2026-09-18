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
package com.spectrayan.spector.memory.aisme.policy;

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
