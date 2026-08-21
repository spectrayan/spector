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
package com.spectrayan.spector.memory.temporal;

import java.util.Comparator;
import java.util.List;

/**
 * A TANGLE-style conflict-aware contradiction resolver.
 * 
 * This resolver selects the highest-confidence fact as the primary answer.
 * On a confidence tie, it falls back to the highest txTime (most recent).
 * On a full tie, it falls back to the highest factId for deterministic resolution.
 * 
 * Multi-evidence recall is achieved through SpectorMemory.factHistory() which returns ALL versions.
 * Named after the TANGLE benchmark philosophy of preserving conflicting evidence rather than forcing lossy resolution.
 */
public final class ConflictAwareResolver implements ContradictionResolver {

    private static final Comparator<TemporalFact> COMPARATOR = Comparator
            .<TemporalFact>comparingDouble(TemporalFact::confidence)
            .thenComparingLong(TemporalFact::txTime)
            .thenComparingInt(TemporalFact::factId);

    @Override
    public TemporalFact resolve(List<TemporalFact> conflicting) {
        return conflicting.stream()
                .max(COMPARATOR)
                .orElseThrow();
    }
}
