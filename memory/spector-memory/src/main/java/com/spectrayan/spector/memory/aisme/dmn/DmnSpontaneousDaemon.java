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
package com.spectrayan.spector.memory.aisme.dmn;

import com.spectrayan.spector.memory.PartitionManager;
import com.spectrayan.spector.memory.WanderPathway;
import com.spectrayan.spector.memory.wander.relay.WanderReport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Background daemon executing periodic Default Mode Network (DMN) spontaneous mind-wandering
 * and longitudinal continuity snapshots via {@link WanderPathway}.
 *
 * <h3>Biological Analog: Spontaneous Non-Task-Evoked Cortical Fluctuations</h3>
 * <p>Runs during cognitive quiescence to consolidate associative pathways across memory clusters
 * without requiring external user-initiated recall queries.</p>
 *
 * @since 1.2.0
 */
public final class DmnSpontaneousDaemon implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(DmnSpontaneousDaemon.class);

    private final WanderPathway wanderPathway;
    private final PartitionManager partitionManager;
    private final LongSupplier lastActivitySupplier;

    public DmnSpontaneousDaemon(
            WanderPathway wanderPathway,
            PartitionManager partitionManager,
            LongSupplier lastActivitySupplier) {
        this.wanderPathway = Objects.requireNonNull(wanderPathway, "WanderPathway must not be null");
        this.partitionManager = partitionManager;
        this.lastActivitySupplier = lastActivitySupplier != null ? lastActivitySupplier : System::currentTimeMillis;
    }

    @Override
    public void run() {
        try {
            long lastActivity = lastActivitySupplier.getAsLong();
            WanderReport report = wanderPathway.wander(partitionManager, lastActivity);
            if (report != null && report.associationsFormed() > 0) {
                log.info("DMN Mind-Wandering: synthesized {} associative edges (synapticDelta={}) in {}ms",
                        report.associationsFormed(), String.format("%.3f", report.synapticWeightDelta()), report.elapsed().toMillis());
            }
        } catch (Exception e) {
            log.warn("DMN Mind-Wandering cycle encountered non-fatal error: {}", e.getMessage());
        }
    }
}
