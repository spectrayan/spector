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

import java.time.Instant;
import java.util.List;

public record PolicyDecisionReport(
    CognitivePolicy selectedPolicy,
    List<ScoredPolicy> rankedPolicies,
    float precision,
    long evaluationDurationNanos,
    Instant timestamp
) {
    public record ScoredPolicy(CognitivePolicy policy, float pragmaticRisk, float epistemicGain, float totalG, float probability) {}

    public static PolicyDecisionReport empty() {
        return new PolicyDecisionReport(null, List.of(), 0.0f, 0L, Instant.now());
    }
}
