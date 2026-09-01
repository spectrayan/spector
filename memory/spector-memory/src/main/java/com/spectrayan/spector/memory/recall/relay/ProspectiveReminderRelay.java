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
package com.spectrayan.spector.memory.recall.relay;

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.memory.pathway.RelayNames;
import com.spectrayan.spector.memory.pipeline.scorer.SalienceAndHabituationScorer;
import com.spectrayan.spector.memory.prospective.ProspectiveScheduler;
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
        salienceScorer.seedProspectiveReminders(signal.candidates(), prospectiveScheduler);
        return true;
    }

    @Override
    public String relayName() {
        return RelayNames.PROSPECTIVE;
    }
}
