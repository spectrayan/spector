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
 * A contradiction resolver that selects the most recently transacted fact.
 * Analogous to recency bias in biological memory systems.
 */
public final class LatestTxWinsResolver implements ContradictionResolver {
    @Override
    public TemporalFact resolve(List<TemporalFact> conflicting) {
        return conflicting.stream()
                .max(Comparator.comparingLong(TemporalFact::txTime))
                .orElseThrow();
    }
}
