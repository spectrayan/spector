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

import com.spectrayan.spector.commons.pathway.IdempotentRelay;
import com.spectrayan.spector.commons.pathway.InterruptibleRelay;
import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.memory.pathway.RelayNames;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.core.spacetime.Time2VecProjector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Transduces a raw text query into a vector representation and computes reference spacetime coordinates.
 */
public final class QueryTransductionRelay
        implements SynapticRelay<RecallSignal>, IdempotentRelay, InterruptibleRelay {

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
