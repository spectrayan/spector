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
package com.spectrayan.spector.memory;

import com.spectrayan.spector.memory.index.MemoryIndex;
import com.spectrayan.spector.memory.model.SalienceProfile;
import com.spectrayan.spector.memory.pipeline.CognitiveIngestionTarget;
import com.spectrayan.spector.memory.sync.MemoryWal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Assembles the {@link CognitiveIngestionTarget} — the write-path sink that fans
 * a remembered item out to the tier stores, index, WAL, graphs and retrieval
 * indices — then wires the effective salience profile and seeds the active
 * partition sequence (#443).
 *
 * <p>Extracted verbatim from {@code SpectorMemoryFactory.assemble} as part of the
 * #437 god-class decomposition. Note the target continues to receive
 * {@code builder.icnuWeights} (not the {@code IcnuWeights.DEFAULT}-resolved value
 * used by the importance estimator), exactly as before.</p>
 *
 * @since 1.1.0
 */
final class CognitiveIngestionTargetBuilder {

    private static final Logger log = LoggerFactory.getLogger(CognitiveIngestionTargetBuilder.class);

    private CognitiveIngestionTargetBuilder() {}

    static CognitiveIngestionTarget build(
            SpectorMemoryBuilder builder,
            CognitiveCortexBuilder.CortexFoundation cortex,
            BiologicalSubsystemsBuilder.BiologicalSubsystems bio,
            CognitiveGraphBuilder.CognitiveGraphs graphs,
            RetrievalIndexBuilder.RetrievalIndices retrieval,
            MemoryIndex index,
            MemoryWal wal,
            int activePartitionIndex) {

        //  Ingestion Target 
        CognitiveIngestionTarget cognitiveTarget = new CognitiveIngestionTarget(
                cortex.quantizer(), bio.surpriseDetector(), bio.flashbulbPolicy(),
                cortex.cognitiveRouter(), index, wal, cortex.workingStore(), builder.icnuWeights,
                builder.semanticIndex, builder.tagExtractor, true,
                graphs.hebbianGraph(), graphs.temporalChain(), graphs.entityExtractor(), graphs.entityGraph(),
                graphs.hyperEntityGraph(),
                retrieval.bm25Index(), retrieval.textDataStore(), activePartitionIndex,
                retrieval.memorySpladeIndex(), builder.SparseEmbeddingProvider,
                builder.dataEncryptor);

        //  Wire Salience Profile Provider 
        if (builder.salienceProfileProvider != null) {
            SalienceProfile effective = builder.salienceProfileProvider.effectiveProfile();
            if (effective != null && !effective.isNeutral()) {
                cognitiveTarget.setSalienceProfile(effective);
                log.info("Salience profile applied: interests={}, disinterests={}, icnuOverride={}",
                        effective.interests().size(), effective.disinterests().size(),
                        effective.hasIcnuOverride());
            }
        }

        //  Seed the ingestion target's active partition seq (#443) 
        cognitiveTarget.updateActivePartitionSeq(cortex.initialPartitionSeq());

        return cognitiveTarget;
    }
}
