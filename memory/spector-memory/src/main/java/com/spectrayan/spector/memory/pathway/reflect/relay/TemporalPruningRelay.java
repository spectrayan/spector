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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Temporal Chain Homeostatic Pruning Relay.
 *
 * <p>Prunes causal and temporal chain links older than the retention threshold
 * when all constituent memories exhibit low importance.</p>
 */
public final class TemporalPruningRelay implements SynapticRelay<ReflectSignal> {

    private static final Logger log = LoggerFactory.getLogger(TemporalPruningRelay.class);
    private static final float TEMPORAL_IMPORTANCE_THRESHOLD = 1.0f;

    @Override
    public boolean transmit(final ReflectSignal signal) {
        if (signal.temporalChain() == null) {
            return true;
        }

        try {
            long cutoffMs = System.currentTimeMillis()
                    - (long) signal.temporalRetentionDays() * 24 * 60 * 60 * 1000;

            int agePruned = signal.temporalChain().pruneOlderThan(cutoffMs);
            int importancePruned = 0;

            if (signal.partitionManager() != null && signal.index() != null) {
                importancePruned = signal.temporalChain().pruneByImportance(
                        cutoffMs, TEMPORAL_IMPORTANCE_THRESHOLD,
                        memIdx -> {
                            try {
                                String id = signal.index().idAt(memIdx);
                                if (id == null) return 0f;
                                var loc = signal.index().locate(id);
                                if (loc == null) return 0f;
                                var router = signal.partitionManager().routerFor(loc.colocatedPartition());
                                if (router == null) return 0f;
                                var body = router.readRecordBody(loc, false);
                                return body != null && body.header() != null ? body.header().importance() : 0f;
                            } catch (RuntimeException e) {
                                return 0f;
                            }
                        });
            }

            int totalPruned = agePruned + importancePruned;
            signal.addTemporalPruned(totalPruned);
            if (totalPruned > 0) {
                log.debug("Temporal Pruning: removed {} causal links", totalPruned);
            }
        } catch (Exception e) {
            log.warn("Temporal chain pruning failed: {}", e.getMessage(), e);
        }
        return true;
    }
}
