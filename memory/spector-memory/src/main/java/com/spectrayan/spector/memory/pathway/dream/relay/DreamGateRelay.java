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
package com.spectrayan.spector.memory.pathway.dream.relay;

import com.spectrayan.spector.commons.pathway.CognitivePathwayException;
import com.spectrayan.spector.commons.pathway.FaultKind;
import com.spectrayan.spector.commons.pathway.SynapticRelay;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stage 1 relay in {@link com.spectrayan.spector.memory.pathway.dream.DreamPathway}.
 *
 * <h3>Biological Analog: Circadian Sleep Pressure Gate</h3>
 * <p>Checks if dreaming conditions are met. Allows the pipeline to proceed.</p>
 *
 * @since 1.4.0
 */
public final class DreamGateRelay implements SynapticRelay<DreamSignal> {

    private static final Logger log = LoggerFactory.getLogger(DreamGateRelay.class);

    @Override
    public boolean transmit(final DreamSignal signal) throws Exception {
        if (signal == null || !DreamGates.DREAMING_ENABLED.test(signal)) {
            final String reason = signal != null
                    ? DreamGates.DREAMING_ENABLED.unsatisfiedReason(signal)
                    : "Dream signal is null";
            if (log.isDebugEnabled()) {
                log.debug("DreamGateRelay: closed gate — {}", reason);
            }
            throw new CognitivePathwayException(
                    "dream_pathway",
                    relayName(),
                    FaultKind.CONTROL,
                    false,
                    new IllegalStateException(reason));
        }
        if (log.isDebugEnabled()) {
            log.debug("DreamGateRelay: initiating dream cycle");
        }
        return true;
    }

    @Override
    public String relayName() {
        return "dream_gate";
    }
}
