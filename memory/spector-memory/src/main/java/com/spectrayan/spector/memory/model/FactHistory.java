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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents the full supersession chain for a (subject, predicate) pair.
 */
public record FactHistory(
    String subject,
    String predicate,
    FactSnapshot activeFact,
    List<FactSnapshot> supersededFacts,
    int totalVersions
) {
    public FactHistory {
        supersededFacts = supersededFacts != null ? List.copyOf(supersededFacts) : Collections.emptyList();
    }

    public record FactSnapshot(
        int factId,
        String object,
        long validFrom,
        long validTo,
        long txTime,
        float confidence,
        int supersededByFactId
    ) {}

    /**
     * True when multiple active (non-retracted) facts exist for the same predicate.
     * This indicates a genuine conflict requiring resolution.
     */
    public boolean hasConflict() {
        return activeFact != null && !supersededFacts.isEmpty()
                && supersededFacts.stream().anyMatch(f -> f.supersededByFactId() < 0);
    }

    public boolean hasHistory() {
        return !supersededFacts.isEmpty();
    }

    public List<FactSnapshot> allVersions() {
        List<FactSnapshot> versions = new ArrayList<>();
        if (activeFact != null) {
            versions.add(activeFact);
        }
        versions.addAll(supersededFacts);
        return Collections.unmodifiableList(versions);
    }

    public static FactHistory empty(String subject, String predicate) {
        return new FactHistory(subject, predicate, null, Collections.emptyList(), 0);
    }
}
