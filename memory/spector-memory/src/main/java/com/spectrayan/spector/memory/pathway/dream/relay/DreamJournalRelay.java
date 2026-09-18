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

import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.memory.pathway.dream.DreamJournalMemory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Stage 5 relay in {@link com.spectrayan.spector.memory.pathway.dream.DreamPathway}.
 *
 * <h3>Biological Analog: Dream Journal / Audit Trail</h3>
 * <p>Logs constructed scenes.</p>
 *
 * @since 1.4.0
 */
public final class DreamJournalRelay implements SynapticRelay<DreamSignal> {

    private static final Logger log = LoggerFactory.getLogger(DreamJournalRelay.class);
    public static final int MAX_LOG_PREVIEW_LENGTH = 200;

    @Override
    public boolean transmit(final DreamSignal signal) {
        if (signal == null || signal.constructedScenes().isEmpty()) return true;

        int written = 0;
        boolean journalEnabled = signal.config() != null && signal.config().journalEnabled();
        
        for (DreamSignal.DreamScene scene : signal.constructedScenes()) {
            String narrative = scene.narrative() != null ? scene.narrative() : "";
            if (narrative.length() > MAX_LOG_PREVIEW_LENGTH) {
                narrative = narrative.substring(0, MAX_LOG_PREVIEW_LENGTH) + "...";
            }

            log.info("Dream Journal: Mode={}, Outcome={}, Quality={}, SourceIDs={}, Narrative='{}'",
                signal.mode(), scene.triageOutcome(), scene.qualityScore(), scene.sourceIds(), narrative);
                
            if (journalEnabled && signal.dreamJournalMemory() != null) {
                signal.dreamJournalMemory().append(new DreamJournalMemory.DreamJournalEntry(
                        scene.id(),
                        java.time.Instant.ofEpochMilli(signal.simulationTimeMs()),
                        signal.mode(),
                        scene.triageOutcome(),
                        scene.qualityScore(),
                        scene.narrative(),
                        scene.insightText(),
                        scene.sourceIds()
                ));
            }
            written++;
        }

        if (signal.dreamsGenerated() != null) {
            signal.dreamsGenerated().addAndGet(written);
        }

        return true;
    }

    @Override
    public String relayName() {
        return "dream_journal";
    }
}
