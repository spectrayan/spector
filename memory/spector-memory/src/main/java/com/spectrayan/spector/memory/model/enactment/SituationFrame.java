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
package com.spectrayan.spector.memory.model.enactment;

import java.util.List;
import java.util.Map;

/**
 * Normalized situation context presented to the persona (ADR-0032).
 */
public record SituationFrame(
        String problem,
        List<String> entities,
        String stakes,
        boolean timePressure,
        Map<String, Object> context
) {
    public SituationFrame {
        problem = (problem != null) ? problem : "";
        entities = (entities != null) ? List.copyOf(entities) : List.of();
        stakes = (stakes != null) ? stakes : "MEDIUM";
        context = (context != null) ? Map.copyOf(context) : Map.of();
    }

    public static SituationFrame of(String problem) {
        return new SituationFrame(problem, List.of(), "MEDIUM", false, Map.of());
    }

    /**
     * Resolves the target point-in-time timestamp for REPLAY mode if specified in context.
     */
    public java.time.Instant asOf() {
        if (context == null) return null;
        Object val = context.get("as_of");
        if (val == null) val = context.get("asOf");
        if (val instanceof java.time.Instant instant) return instant;
        if (val instanceof Long epochMs) return java.time.Instant.ofEpochMilli(epochMs);
        if (val instanceof String str && !str.isBlank()) {
            try {
                return java.time.Instant.parse(str);
            } catch (Exception ignored) {
            }
        }
        return null;
    }
}
