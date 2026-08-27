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
package com.spectrayan.spector.memory;

import com.spectrayan.spector.commons.pathway.CognitivePathway;
import com.spectrayan.spector.commons.pathway.ErrorPolicy;
import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.memory.aisme.config.AismeConfig;
import com.spectrayan.spector.memory.dream.relay.DreamConfig;
import com.spectrayan.spector.memory.dream.relay.DreamGateRelay;
import com.spectrayan.spector.memory.dream.relay.DreamGates;
import com.spectrayan.spector.memory.dream.relay.DreamIngestionRelay;
import com.spectrayan.spector.memory.dream.relay.DreamJournalRelay;
import com.spectrayan.spector.memory.dream.relay.DreamMode;
import com.spectrayan.spector.memory.dream.relay.DreamReport;
import com.spectrayan.spector.memory.dream.relay.DreamSignal;
import com.spectrayan.spector.memory.dream.relay.EfeTriageRelay;
import com.spectrayan.spector.memory.dream.relay.RemReplayRelay;
import com.spectrayan.spector.memory.dream.relay.SalientSeedRelay;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.function.Function;

/**
 * 7th Canonical Cognitive Pathway in Spector Memory orchestrating Dreaming
 * and memory consolidation processes.
 *
 * <h3>Biological Analog: Offline Consolidation and Dream Generation</h3>
 * <p>Executes continuous dream cycles during offline/sleep states.</p>
 *
 * @since 1.4.0
 */
public final class DreamPathway implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(DreamPathway.class);

    private final CognitivePathway<DreamSignal> pathway;
    private final DreamConfig dreamConfig;
    private final PartitionManager partitionManager;
    private final AismeConfig aismeConfig;

    private DreamPathway(final Builder builder) {
        this.dreamConfig = builder.dreamConfig;
        this.partitionManager = builder.partitionManager;
        this.aismeConfig = builder.aismeConfig;

        var pathwayBuilder = CognitivePathway.<DreamSignal>pathway("dream_pathway");
        if (builder.interceptor != null) {
            pathwayBuilder.withInterceptor(builder.interceptor);
        }

        // 1. Dream Gate
        pathwayBuilder.gated("dream_gate", DreamGates.DREAMING_ENABLED, new DreamGateRelay(), ErrorPolicy.DEGRADE_GRACEFULLY);

        // 2. Salient Seed
        pathwayBuilder.gated("salient_seed", DreamGates.DREAMING_ENABLED, new SalientSeedRelay(), ErrorPolicy.DEGRADE_GRACEFULLY);

        // 3. REM Replay
        pathwayBuilder.gated("rem_replay", DreamGates.HAS_SEEDS, new RemReplayRelay(), ErrorPolicy.DEGRADE_GRACEFULLY);

        // 4. EFE Triage
        pathwayBuilder.gated("efe_triage", DreamGates.HAS_SEEDS, new EfeTriageRelay(), ErrorPolicy.DEGRADE_GRACEFULLY);

        // 5. Dream Journal
        pathwayBuilder.gated("dream_journal", DreamGates.JOURNAL_ENABLED, new DreamJournalRelay(), ErrorPolicy.DEGRADE_GRACEFULLY);

        // 6. Dream Ingestion
        pathwayBuilder.gated("dream_ingestion", DreamGates.DREAMING_ENABLED, new DreamIngestionRelay(), ErrorPolicy.DEGRADE_GRACEFULLY);

        this.pathway = pathwayBuilder.build();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Conducts a full dreaming cycle over the supplied signal.
     *
     * @param signal the dream execution signal
     * @return resulting {@link DreamReport}
     */
    public DreamReport conduct(final DreamSignal signal) {
        Objects.requireNonNull(signal, "DreamSignal cannot be null");
        if (log.isTraceEnabled()) {
            log.trace("DreamPathway: initiating dream cycle in {} mode...", signal.mode());
        }
        try {
            pathway.conduct(signal);
            DreamReport report = signal.buildReport();
            if (log.isDebugEnabled()) {
                log.debug("DreamPathway: cycle complete in {}ms — seeds={}, scenes={}, ingested={}, failed={}",
                        report.elapsed().toMillis(), report.seedsSampled(), report.scenesConstructed(),
                        report.insightsIngested(), report.failedPairsInhibited());
            }
            return report;
        } catch (Exception e) {
            log.error("DreamPathway: dream cycle aborted due to error: {}", e.getMessage(), e);
            throw new IllegalStateException("DreamPathway execution failed: " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to execute a dream cycle.
     */
    public DreamReport dream(DreamMode mode, PartitionManager pm, AismeConfig aismeConfig) {
        DreamSignal signal = DreamSignal.builder()
                .mode(mode)
                .config(dreamConfig)
                .partitionManager(pm)
                .aismeConfig(aismeConfig)
                .build();

        return conduct(signal);
    }

    @Override
    public void close() {
        // Optional cleanup
    }

    /**
     * Builder for {@link DreamPathway}.
     */
    public static final class Builder {
        private DreamConfig dreamConfig;
        private PartitionManager partitionManager;
        private AismeConfig aismeConfig = AismeConfig.defaultConfig();
        private Function<SynapticRelay<DreamSignal>, SynapticRelay<DreamSignal>> interceptor;

        public Builder dreamConfig(DreamConfig dc) { this.dreamConfig = dc; return this; }
        public Builder partitionManager(PartitionManager pm) { this.partitionManager = pm; return this; }
        public Builder aismeConfig(AismeConfig ac) { this.aismeConfig = ac; return this; }
        public Builder interceptor(Function<SynapticRelay<DreamSignal>, SynapticRelay<DreamSignal>> inc) { this.interceptor = inc; return this; }

        public DreamPathway build() {
            return new DreamPathway(this);
        }
    }
}
