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
package com.spectrayan.spector.memory.remember.relay;

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.memory.index.MemoryIndex;
import com.spectrayan.spector.memory.pathway.RelayNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Deduplication guard relay that short-circuits ingestion if the memory identifier is already indexed.
 */
public final class DedupGuardRelay implements SynapticRelay<RememberSignal> {

    private static final Logger log = LoggerFactory.getLogger(DedupGuardRelay.class);

    private final MemoryIndex index;

    public DedupGuardRelay(final MemoryIndex index) {
        this.index = Objects.requireNonNull(index, "index cannot be null");
    }

    @Override
    public boolean transmit(final RememberSignal signal) {
        if (index.locate(signal.id()) != null) {
            log.debug("Skipping duplicate memory '{}'  --  already indexed", sanitize(signal.id()));
            signal.duplicate(true);
            return false; // Short-circuit pathway execution
        }
        return true;
    }

    @Override
    public String relayName() {
        return RelayNames.DEDUP_GUARD;
    }

    private static String sanitize(final String value) {
        if (value == null) {
            return null;
        }
        return value.replace('\n', '_').replace('\r', '_');
    }
}
