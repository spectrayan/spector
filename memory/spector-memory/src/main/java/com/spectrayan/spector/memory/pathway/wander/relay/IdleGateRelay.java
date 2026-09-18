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
package com.spectrayan.spector.memory.pathway.wander.relay;

import com.spectrayan.spector.commons.pathway.SynapticRelay;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stage 1 relay in {@link com.spectrayan.spector.memory.pathway.wander.WanderPathway} that enforces cognitive quiescence.
 *
 * <h3>Biological Analog: Task-Negative Default Network Disinhibition</h3>
 * <p>Ensures that mind-wandering processes only initiate when the external sensory and query
 * streams have remained quiescent for a designated idle interval \(\Delta t \ge \tau_{\text{idle}}\).</p>
 *
 * @since 1.2.0
 */
public final class IdleGateRelay implements SynapticRelay<WanderSignal> {

    private static final Logger log = LoggerFactory.getLogger(IdleGateRelay.class);

    @Override
    public boolean transmit(final WanderSignal signal) {
        if (signal == null) {
            return false;
        }

        long idleDurationMs = System.currentTimeMillis() - signal.lastActivityTimestampMs();
        long requiredIdleMs = (long) signal.idleThresholdSeconds() * 1000L;

        if (idleDurationMs < requiredIdleMs) {
            if (log.isTraceEnabled()) {
                log.trace("IdleGateRelay: system active within threshold (idle={}ms, required={}ms)",
                        idleDurationMs, requiredIdleMs);
            }
            return false;
        }

        if (log.isDebugEnabled()) {
            log.debug("IdleGateRelay: cognitive rest detected (idle={}ms >= {}ms), initiating mind-wandering",
                    idleDurationMs, requiredIdleMs);
        }

        return true;
    }

    @Override
    public String relayName() {
        return "idle_gate";
    }
}
