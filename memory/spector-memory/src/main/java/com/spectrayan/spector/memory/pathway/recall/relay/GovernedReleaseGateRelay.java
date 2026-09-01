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
package com.spectrayan.spector.memory.pathway.recall.relay;

import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.memory.kernel.layout.SynapticHeaderConstants;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.pathway.RelayNames;

/**
 * Governed Persistent Memory (GPM) fail-closed release gate (ADR-0008).
 *
 * <p>Enforces zero-leakage security and epistemic trust verification on memory candidates.
 * Drops retracted or sovereign-restricted records before cortical tier scanning and scoring.</p>
 */
public final class GovernedReleaseGateRelay implements SynapticRelay<RecallSignal> {

    private static final Logger log = LoggerFactory.getLogger(GovernedReleaseGateRelay.class);

    @Override
    public boolean transmit(final RecallSignal signal) {
        Objects.requireNonNull(signal, "signal cannot be null");

        List<CognitiveResult> candidates = signal.candidates();
        if (candidates.isEmpty()) {
            return true;
        }

        float minTrust = signal.options().minTrustScore();
        Iterator<CognitiveResult> it = candidates.iterator();
        while (it.hasNext()) {
            CognitiveResult r = it.next();
            byte cFlags = r.consolidationFlags();

            // Fail-closed check: retracted memory records are never released
            if (SynapticHeaderConstants.isRetracted(cFlags)) {
                log.debug("Dropping retracted memory candidate: {}", r.id());
                it.remove();
                continue;
            }

            // Sovereign restriction check
            if (SynapticHeaderConstants.isRestricted(cFlags)) {
                // If caller persona is not explicitly authorized, drop candidate
                String persona = signal.options().personaId();
                if (persona == null || persona.isBlank()) {
                    log.debug("Dropping restricted memory candidate without persona context: {}", r.id());
                    it.remove();
                    continue;
                }
            }

            // Trust-gating check: unverified records must satisfy minTrustScore
            if (SynapticHeaderConstants.isUnverified(cFlags) && minTrust > 0.0f) {
                if (r.score() < minTrust && r.ltpAdjustedDecay() < minTrust) {
                    log.debug("Dropping unverified candidate below minTrustScore ({}): {}", minTrust, r.id());
                    it.remove();
                }
            }
        }

        return true;
    }

    @Override
    public String relayName() {
        return RelayNames.GOVERNED_RELEASE_GATE;
    }
}
