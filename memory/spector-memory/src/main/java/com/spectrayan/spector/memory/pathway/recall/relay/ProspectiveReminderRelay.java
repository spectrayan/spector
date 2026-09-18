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
package com.spectrayan.spector.memory.pathway.recall.relay;

import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.memory.pathway.RelayNames;
import com.spectrayan.spector.memory.pathway.pipeline.scorer.SalienceAndHabituationScorer;
import com.spectrayan.spector.memory.cortex.prospective.ProspectiveScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Injects prospective reminders as recall candidates.
 */
public final class ProspectiveReminderRelay implements SynapticRelay<RecallSignal> {

    private static final Logger log = LoggerFactory.getLogger(ProspectiveReminderRelay.class);
    
    private final SalienceAndHabituationScorer salienceScorer;
    private final ProspectiveScheduler prospectiveScheduler;

    public ProspectiveReminderRelay(
            final SalienceAndHabituationScorer salienceScorer,
            final ProspectiveScheduler prospectiveScheduler) {
        this.salienceScorer = salienceScorer;
        this.prospectiveScheduler = prospectiveScheduler;
    }

    @Override
    public boolean transmit(final RecallSignal signal) {
        final ProspectiveScheduler ps = (signal != null && signal.context() != null)
                ? signal.context().find(ProspectiveScheduler.class).orElse(prospectiveScheduler) : prospectiveScheduler;
        salienceScorer.seedProspectiveReminders(signal.candidates(), ps);
        return true;
    }

    @Override
    public String relayName() {
        return RelayNames.PROSPECTIVE;
    }
}
