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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.memory.kernel.layout.SynapticHeaderConstants;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.ConflictMode;
import com.spectrayan.spector.memory.pathway.RelayNames;

/**
 * TANGLE multi-evidence fusion relay (ADR-0008).
 *
 * <p>Handles contradictory candidate claims and bitemporal hypothesis clustering according
 * to the configured {@link ConflictMode}.</p>
 */
public final class EvidenceFusionRelay implements SynapticRelay<RecallSignal> {

    private static final Logger log = LoggerFactory.getLogger(EvidenceFusionRelay.class);

    @Override
    public boolean transmit(final RecallSignal signal) {
        Objects.requireNonNull(signal, "signal cannot be null");

        List<CognitiveResult> candidates = signal.candidates();
        if (candidates.isEmpty()) {
            return true;
        }

        ConflictMode mode = signal.options().conflictMode();
        if (mode == null) {
            mode = ConflictMode.MULTI_EVIDENCE;
        }

        switch (mode) {
            case FAIL_CLOSED -> {
                // Drop any candidates that are contradicted
                Iterator<CognitiveResult> it = candidates.iterator();
                while (it.hasNext()) {
                    CognitiveResult r = it.next();
                    if (SynapticHeaderConstants.isContradicted(r.consolidationFlags())) {
                        log.debug("Fail-closed: dropping contradicted candidate {}", r.id());
                        it.remove();
                    }
                }
            }
            case HIGHEST_CONFIDENCE -> {
                // If contradictions exist, favor the candidate with higher confidence/score
                List<CognitiveResult> resolved = new ArrayList<>();
                for (CognitiveResult r : candidates) {
                    if (SynapticHeaderConstants.isContradicted(r.consolidationFlags())) {
                        boolean superseded = false;
                        for (CognitiveResult existing : resolved) {
                            if ((existing.text().equalsIgnoreCase(r.text()) || existing.id().equals(r.id()))
                                    && existing.score() >= r.score()) {
                                superseded = true;
                                break;
                            }
                        }
                        if (!superseded) {
                            resolved.add(r);
                        }
                    } else {
                        resolved.add(r);
                    }
                }
                signal.setCandidates(resolved);
            }
            case MULTI_EVIDENCE -> {
                // Preserve all evidence versions for downstream formatting and distribution
                if (log.isTraceEnabled()) {
                    log.trace("Preserved {} multi-evidence candidate traces", candidates.size());
                }
            }
        }

        return true;
    }

    @Override
    public String relayName() {
        return RelayNames.EVIDENCE_FUSION;
    }
}
