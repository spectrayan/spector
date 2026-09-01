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
package com.spectrayan.spector.memory.pathway.wander.relay;

import java.time.Duration;
import java.util.List;

/**
 * Immutable execution telemetry report returned upon completing a {@link com.spectrayan.spector.memory.pathway.wander.WanderPathway} cycle.
 *
 * @param memoriesSampled number of memories sampled from active stores
 * @param associationsFormed number of new or reinforced Hebbian synaptic edges
 * @param synapticWeightDelta total synaptic edge weight increment added to the Hebbian network
 * @param snapshotRecorded whether an identity trajectory snapshot was appended to {@link com.spectrayan.spector.memory.cortex.ContinuityRecordMemory}
 * @param elapsed total elapsed duration of the wandering cycle
 * @param discoveredAssociations details of discovered synergistic memory associations
 */
public record WanderReport(
        int memoriesSampled,
        int associationsFormed,
        float synapticWeightDelta,
        boolean snapshotRecorded,
        Duration elapsed,
        List<WanderSignal.DiscoveredAssociation> discoveredAssociations
) {

    public static WanderReport empty() {
        return new WanderReport(0, 0, 0.0f, false, Duration.ZERO, List.of());
    }
}
