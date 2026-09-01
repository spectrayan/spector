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
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.core.spacetime.Time2VecProjector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Transduces a raw text query into a vector representation and computes reference spacetime coordinates.
 */
public final class QueryTransductionRelay implements SynapticRelay<RecallSignal> {

    private static final Logger log = LoggerFactory.getLogger(QueryTransductionRelay.class);
    
    private final EmbeddingProvider embeddingProvider;

    public QueryTransductionRelay(final EmbeddingProvider embeddingProvider) {
        this.embeddingProvider = embeddingProvider;
    }

    @Override
    public boolean transmit(final RecallSignal signal) {
        if (signal.queryVector() == null && signal.rawQuery() != null) {
            final var result = embeddingProvider.embed(signal.rawQuery());
            signal.setQueryVector(result.vector());
            log.debug("Embedded query vector for text: {}", signal.rawQuery());
        }

        final long queryTime = (signal.options().replayTimestamp() != null)
                ? signal.options().replayTimestamp().toEpochMilli()
                : signal.timestampMs();
        signal.setQueryTimeMs(queryTime);
        signal.setQueryTau(Time2VecProjector.project(queryTime));

        return true;
    }

    @Override
    public String relayName() {
        return RelayNames.TRANSDUCTION;
    }
}
