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
package com.spectrayan.spector.memory.pathway.dream.daemon;

import com.spectrayan.spector.memory.pathway.dream.DreamPathway;
import com.spectrayan.spector.memory.persist.PartitionManager;
import com.spectrayan.spector.memory.aisme.config.AismeConfig;
import com.spectrayan.spector.memory.pathway.dream.relay.DreamMode;
import com.spectrayan.spector.memory.pathway.dream.relay.DreamReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

/**
 * Background daemon executing periodic offline REM and creative dreaming cycles via {@link DreamPathway}.
 *
 * <h3>Biological Analog: Sleep-Dependent REM Memory Reorganization &amp; Dream Consolidation</h3>
 * <p>Runs asynchronously in a dedicated background thread during quiescent sleep consolidation windows,
 * evaluating sleep pressure and reflection cycle frequency before initiating generative recombination.</p>
 *
 * @since 1.4.0
 */
public final class DreamDaemon implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(DreamDaemon.class);

    private final DreamPathway dreamPathway;
    private final PartitionManager partitionManager;
    private final AismeConfig aismeConfig;
    private final LongSupplier lastActivitySupplier;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger cycleCounter = new AtomicInteger(0);

    public DreamDaemon(
            DreamPathway dreamPathway,
            PartitionManager partitionManager,
            AismeConfig aismeConfig,
            LongSupplier lastActivitySupplier) {
        this.dreamPathway = Objects.requireNonNull(dreamPathway, "DreamPathway must not be null");
        this.partitionManager = partitionManager;
        this.aismeConfig = aismeConfig;
        this.lastActivitySupplier = lastActivitySupplier != null ? lastActivitySupplier : System::currentTimeMillis;
    }

    public DreamDaemon(DreamPathway dreamPathway, PartitionManager partitionManager) {
        this(dreamPathway, partitionManager, null, System::currentTimeMillis);
    }

    @Override
    public void run() {
        if (!running.compareAndSet(false, true)) {
            log.debug("DreamDaemon: dream cycle already in progress — skipping");
            return;
        }

        try {
            int currentEpoch = cycleCounter.incrementAndGet();
            if (dreamPathway.config() != null && !dreamPathway.config().enabled()) {
                return;
            }

            int freq = dreamPathway.config() != null ? dreamPathway.config().dreamCycleFrequency() : com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_DREAM_CYCLE_FREQUENCY;
            if (currentEpoch % freq != 0) {
                if (log.isTraceEnabled()) {
                    log.trace("DreamDaemon: cycle {} not matching frequency {} — skipping", currentEpoch, freq);
                }
                return;
            }

            log.info("DreamDaemon: initiating offline sleep dream cycle (epoch={})", currentEpoch);
            DreamReport report = dreamPathway.dream(DreamMode.REM, partitionManager, aismeConfig);

            if (report != null && report.scenesConstructed() > 0) {
                log.info("DreamDaemon: cycle complete — synthesized {} scenes, ingested {} insights, inhibited {} failed pairs in {}ms",
                        report.scenesConstructed(), report.insightsIngested(), report.failedPairsInhibited(), report.elapsed().toMillis());
            }
        } catch (Exception e) {
            log.warn("DreamDaemon encountered non-fatal error during dream cycle: {}", e.getMessage(), e);
        } finally {
            running.set(false);
        }
    }

    public int totalCyclesRun() {
        return cycleCounter.get();
    }
}
