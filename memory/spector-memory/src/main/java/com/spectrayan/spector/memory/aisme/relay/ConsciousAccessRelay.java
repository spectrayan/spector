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
package com.spectrayan.spector.memory.aisme.relay;

import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.memory.aisme.workspace.GlobalWorkspace;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.recall.relay.RecallSignal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * RecallPathway relay that implements the Global Workspace conscious access broadcast gateway.
 *
 * <h3>Biological Analog: Global Workspace Conscious Access Bottleneck</h3>
 * <p>Gates candidate memories through the limited-capacity conscious bottleneck (~7 items),
 * selecting only the most salient and resonant items for final cognitive broadcast.</p>
 */
public final class ConsciousAccessRelay implements SynapticRelay<RecallSignal> {

    private static final Logger log = LoggerFactory.getLogger(ConsciousAccessRelay.class);

    private final GlobalWorkspace workspace;

    /**
     * Constructs a ConsciousAccessRelay with a GlobalWorkspace instance.
     *
     * @param workspace the global workspace engine (nullable)
     */
    public ConsciousAccessRelay(GlobalWorkspace workspace) {
        this.workspace = workspace;
    }

    @Override
    public boolean transmit(final RecallSignal signal) {
        if (workspace == null || signal == null) {
            return true;
        }

        List<CognitiveResult> candidates = signal.candidates();
        if (candidates == null || candidates.isEmpty()) {
            return true;
        }

        List<CognitiveResult> broadcastList = workspace.filterForBroadcast(candidates);
        signal.setCandidates(broadcastList);

        if (log.isTraceEnabled()) {
            log.trace("ConsciousAccessRelay gated candidates from {} to {}", candidates.size(), broadcastList.size());
        }

        return true;
    }

    @Override
    public String relayName() {
        return "conscious-access";
    }
}
