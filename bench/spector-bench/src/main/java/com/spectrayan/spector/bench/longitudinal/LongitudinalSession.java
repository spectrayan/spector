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
package com.spectrayan.spector.bench.longitudinal;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable record representing a single interaction session within a multi-day longitudinal evaluation scenario.
 *
 * @param sessionId          Unique session identifier (e.g. "session-01")
 * @param dayOffset          Simulated day index (0-based)
 * @param prompt             User input / task prompt for this session
 * @param expectedIntent     Ground-truth target intent
 * @param groundTruthContext Key-value ground truth facts expected to be recalled or honored
 * @param negativeConstraints Constraints or bugs resolved in earlier sessions that must NOT be violated
 */
public record LongitudinalSession(
        String sessionId,
        int dayOffset,
        String prompt,
        String expectedIntent,
        Map<String, String> groundTruthContext,
        List<String> negativeConstraints
) {
    public LongitudinalSession {
        Objects.requireNonNull(sessionId, "sessionId cannot be null");
        Objects.requireNonNull(prompt, "prompt cannot be null");
        groundTruthContext = groundTruthContext != null ? Map.copyOf(groundTruthContext) : Map.of();
        negativeConstraints = negativeConstraints != null ? List.copyOf(negativeConstraints) : List.of();
    }
}
