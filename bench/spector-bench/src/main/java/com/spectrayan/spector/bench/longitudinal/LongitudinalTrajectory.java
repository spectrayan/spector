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
import java.util.Objects;

/**
 * Immutable record representing an end-to-end multi-session evaluation trajectory.
 *
 * @param trajectoryId   Unique scenario trajectory ID
 * @param domain         Benchmark domain (e.g. "coding-agent", "shopping", "travel")
 * @param description    Human-readable trajectory description
 * @param totalDays      Total simulated time horizon in days
 * @param sessions       Ordered sequence of multi-turn sessions
 */
public record LongitudinalTrajectory(
        String trajectoryId,
        String domain,
        String description,
        int totalDays,
        List<LongitudinalSession> sessions
) {
    public LongitudinalTrajectory {
        Objects.requireNonNull(trajectoryId, "trajectoryId cannot be null");
        Objects.requireNonNull(domain, "domain cannot be null");
        sessions = sessions != null ? List.copyOf(sessions) : List.of();
    }
}
