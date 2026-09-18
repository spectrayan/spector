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
