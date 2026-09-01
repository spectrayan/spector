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
import com.spectrayan.spector.memory.aisme.fegr.EventDensityFilter;
import com.spectrayan.spector.memory.aisme.fegr.EventDensityMetrics;
import com.spectrayan.spector.memory.aisme.fegr.MentalStateTracker;
import com.spectrayan.spector.memory.pathway.RelayNames;
import com.spectrayan.spector.memory.pathway.remember.relay.RememberSignal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Synaptic relay executing information-theoretic event density evaluation and epistemic gating.
 *
 * <h3>Biological Analog: Thalamic Sensory Filtering & Epistemic Salience Gating</h3>
 * <p>Computes the instantaneous event density \(\nu(o_t)\) for incoming memory signals.
 * Suppresses redundant, low-information sensory background frames while passing salient epistemic spikes.</p>
 */
public final class EventDensityGatingRelay implements SynapticRelay<RememberSignal> {

    private static final Logger log = LoggerFactory.getLogger(EventDensityGatingRelay.class);

    private final EventDensityFilter filter;
    private final MentalStateTracker tracker;
    private final boolean abortOnGated;

    public EventDensityGatingRelay(EventDensityFilter filter, MentalStateTracker tracker) {
        this(filter, tracker, true);
    }

    public EventDensityGatingRelay(EventDensityFilter filter, MentalStateTracker tracker, boolean abortOnGated) {
        this.filter = filter;
        this.tracker = tracker;
        this.abortOnGated = abortOnGated;
    }

    @Override
    public String relayName() {
        return RelayNames.EVENT_DENSITY_GATING;
    }

    @Override
    public boolean transmit(final RememberSignal signal) {
        if (signal == null || filter == null || tracker == null || signal.vector() == null) {
            return true;
        }

        try {
            EventDensityMetrics metrics = filter.evaluate(
                    tracker.posterior(),
                    tracker.selfModel(),
                    signal.vector()
            );

            signal.eventDensityMetrics(metrics);

            if (!metrics.isSalientSpike()) {
                signal.gated(true);
                log.debug("EventDensityGating: frame gated due to low epistemic density (nu={}, threshold={})",
                        String.format("%.4f", metrics.eventDensity()), String.format("%.4f", filter.threshold()));

                if (abortOnGated) {
                    return false; // Abort downstream cortical write for redundant sensory frames
                }
            } else {
                signal.gated(false);
                log.trace("EventDensityGating: salient event spike detected (nu={}, threshold={}, samplingRate={}Hz)",
                        String.format("%.4f", metrics.eventDensity()), String.format("%.4f", filter.threshold()),
                        String.format("%.2f", metrics.dynamicSamplingRateHz()));
            }
        } catch (final RuntimeException e) {
            log.warn("EventDensityGatingRelay: evaluation failed; passing signal uninhibited: {}", e.getMessage(), e);
        }

        return true;
    }

    public EventDensityFilter filter() {
        return filter;
    }

    public MentalStateTracker tracker() {
        return tracker;
    }

    public boolean isAbortOnGated() {
        return abortOnGated;
    }
}
