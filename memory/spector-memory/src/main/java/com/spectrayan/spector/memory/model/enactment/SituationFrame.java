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
}
