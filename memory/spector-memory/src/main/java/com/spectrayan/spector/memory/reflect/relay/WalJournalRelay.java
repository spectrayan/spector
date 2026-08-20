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
package com.spectrayan.spector.memory.reflect.relay;

import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.memory.sync.WalEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Write-Ahead Logging (WAL) & Consolidation Journal Relay.
 *
 * <p>Persists the completion of the biological reflection cycle to the durability WAL
 * and logs telemetry metrics.</p>
 */
public final class WalJournalRelay implements SynapticRelay<ReflectSignal> {

    private static final Logger log = LoggerFactory.getLogger(WalJournalRelay.class);

    @Override
    public boolean transmit(final ReflectSignal signal) {
        if (signal.wal() != null) {
            try {
                signal.wal().append(WalEvent.EventType.REFLECT, "system", null);
            } catch (Exception e) {
                log.warn("Failed to write REFLECT event to WAL: {}", e.getMessage(), e);
            }
        }

        if (log.isInfoEnabled()) {
            var metrics = signal.graphMetrics();
            if (metrics != null && (metrics.totalEdgesDecayed() > 0 || metrics.totalEdgesSurviving() > 0)) {
                log.info("Reflect: graph health snapshot — {}", metrics);
            }
        }

        return true;
    }
}
