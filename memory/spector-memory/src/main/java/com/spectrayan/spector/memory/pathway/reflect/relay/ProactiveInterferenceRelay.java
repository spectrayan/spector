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
package com.spectrayan.spector.memory.pathway.reflect.relay;

import com.spectrayan.spector.commons.pathway.SynapticRelay;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * REM Sleep Proactive Interference Relay.
 *
 * <p>Legacy fixed-stride episodic partitions have been replaced by {@code EpisodeLayout} append logs.
 * Episodic reflection and consolidation are handled via {@link EpisodicLogConsolidationRelay}.</p>
 */
public final class ProactiveInterferenceRelay implements SynapticRelay<ReflectSignal> {

    private static final Logger log = LoggerFactory.getLogger(ProactiveInterferenceRelay.class);

    @Override
    public boolean transmit(final ReflectSignal signal) {
        return true;
    }
}
