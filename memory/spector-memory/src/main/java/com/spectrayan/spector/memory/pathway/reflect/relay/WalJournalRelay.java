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
package com.spectrayan.spector.memory.pathway.reflect.relay;

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
