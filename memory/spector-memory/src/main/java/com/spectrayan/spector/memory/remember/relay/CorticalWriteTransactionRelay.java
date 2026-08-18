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

import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.core.quantization.ScalarQuantizer;
import com.spectrayan.spector.core.similarity.VectorOps;
import com.spectrayan.spector.memory.cortex.CognitiveMemoryRouter;
import com.spectrayan.spector.memory.dopamine.SurpriseDetector;
import com.spectrayan.spector.memory.error.SpectorMemoryTierFullException;
import com.spectrayan.spector.memory.error.SpectorPartitionFrozenException;
import com.spectrayan.spector.memory.kernel.layout.CognitiveRecordLayout.CognitiveHeader;
import com.spectrayan.spector.memory.kernel.layout.SynapticHeaderConstants;
import com.spectrayan.spector.memory.model.CognitiveProfile;
import com.spectrayan.spector.memory.model.IngestionContext;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.SalienceProfile;
import com.spectrayan.spector.memory.model.SourceModality;
import com.spectrayan.spector.memory.neurodivergent.IngestionHints;
import com.spectrayan.spector.memory.pathway.RelayNames;
import com.spectrayan.spector.memory.pipeline.PostIngestSync;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;

/**
 * Coarse-grained transactional write relay encapsulating the atomic ingestion write sequence (Steps 3–6).
 *
 * <h3>Architectural Rationale</h3>
 * <p>Per ADR Decision #2, the sequential write phase comprises tightly coupled, interdependent steps:
 * <ol>
 *   <li><b>L2 Normalization & INT8 Quantization</b>: Projects continuous embeddings into calibrated off-heap byte representations.</li>
 *   <li><b>Neuromodulatory Header Assembly</b>: Builds the 64-byte {@link CognitiveHeader} incorporating emotional valence/arousal,
 *       personality modulation, soul version stamp, and formation-time surprise z-scores.</li>
 *   <li><b>Off-Heap Slab Allocation & Segment Write</b>: Atomically persists the header and quantized payload to the target
 *       memory tier, automatically rolling active partitions when capacity limits are encountered.</li>
 *   <li><b>Multi-Index Synchronization</b>: Registers the memory across HNSW graph, ID reverse index, WAL journal, BM25 text index,
 *       and SPLADE sparse index with compensating tombstone protection.</li>
 * </ol>
 * Unifying these four steps into a single relay preserves the atomic write transaction boundary and prevents partially persisted
 * corrupt state in the event of an unrecoverable storage failure.</p>
 */
public final class CorticalWriteTransactionRelay implements SynapticRelay<RememberSignal> {

    private static final Logger log = LoggerFactory.getLogger(CorticalWriteTransactionRelay.class);

    private final ScalarQuantizer quantizer;
    private final PostIngestSync postIngestSync;
    private final SurpriseDetector surpriseDetector;
    private final boolean normalizeAtIngest;

    private volatile CognitiveMemoryRouter cognitiveRouter;
    private volatile Runnable partitionRollCallback;

    /**
     * Constructs a new {@code CorticalWriteTransactionRelay}.
     *
     * @param quantizer        the scalar quantizer for INT8 projection
     * @param cognitiveRouter  the router directing writes to off-heap slab stores
     * @param postIngestSync   the post-write index synchronizer
     * @param surpriseDetector the novelty/surprise detector
     * @param normalizeAtIngest whether to L2-normalize vectors before writing
     */
    public CorticalWriteTransactionRelay(
            final ScalarQuantizer quantizer,
            final CognitiveMemoryRouter cognitiveRouter,
            final PostIngestSync postIngestSync,
            final SurpriseDetector surpriseDetector,
            final boolean normalizeAtIngest) {
        this.quantizer = Objects.requireNonNull(quantizer, "quantizer cannot be null");
        this.cognitiveRouter = Objects.requireNonNull(cognitiveRouter, "cognitiveRouter cannot be null");
        this.postIngestSync = Objects.requireNonNull(postIngestSync, "postIngestSync cannot be null");
        this.surpriseDetector = Objects.requireNonNull(surpriseDetector, "surpriseDetector cannot be null");
        this.normalizeAtIngest = normalizeAtIngest;
    }

    @Override
    public boolean transmit(final RememberSignal signal) {
        float[] vector = signal.vector();
        if (normalizeAtIngest && vector != null) {
            vector = l2Normalize(vector);
            signal.normalizedVector(vector);
        }

        // 1. INT8 Scalar Quantization
        final byte[] quantized = (vector != null) ? quantizer.encode(vector) : new byte[0];
        signal.quantizedVector(quantized);

        // 2. Cognitive Header Assembly
        final MemoryType type = signal.type();
        final IngestionHints hints = signal.hints();
        final IngestionContext context = signal.context();
        final SalienceProfile salienceProfile = signal.salienceProfile();

        byte flags = SynapticHeaderConstants.withMemoryType((byte) 0, type.ordinal());
        if (signal.isFlashbulb()) {
            flags = (byte) (flags | SynapticHeaderConstants.FLAG_PINNED);
        }
        if (context != null) {
            final SourceModality modality = context.sourceModality();
            if (modality != null && modality != SourceModality.TEXT) {
                flags = SynapticHeaderConstants.withSourceModality(flags, modality.ordinal());
            }
        }

        final float l2Norm = (vector != null) ? VectorOps.magnitude(vector) : 0.0f;
        final byte rawValence = (hints != null) ? hints.valence() : (byte) 0;
        final byte rawArousal = (hints != null) ? hints.effectiveArousal() : (byte) 0;
        final byte valence = salienceProfile.modulateValence(rawValence);
        final byte arousal = salienceProfile.modulateArousal(rawArousal);

        final float surpriseZScore = (float) surpriseDetector.stats().zScore(signal.nearestDist());
        final byte encodingProfile = computeEncodingProfile(salienceProfile);
        final byte encodingAlpha = computeEncodingAlpha(salienceProfile);
        final byte encodingBeta = computeEncodingBeta(salienceProfile);

        final CognitiveHeader header = new CognitiveHeader(
                signal.timestampMs(),
                signal.synapticTags(),
                l2Norm,
                signal.importance(),
                0,
                (short) 0,
                valence,
                flags,
                arousal,
                1.0f,
                encodingProfile,
                encodingAlpha,
                encodingBeta,
                signal.soulVersion(),
                surpriseZScore
        );
        signal.header(header);

        // 3. Off-Heap Slab Write (with partition roll support)
        long offset;
        try {
            try {
                offset = cognitiveRouter.write(type, header, quantized);
            } catch (final SpectorPartitionFrozenException e) {
                offset = cognitiveRouter.write(type, header, quantized);
            }
        } catch (final SpectorMemoryTierFullException e) {
            if (partitionRollCallback != null) {
                log.info("Tier {} full ({} records)  --  rolling to new partition",
                        type, e.getCapacity());
                partitionRollCallback.run();
                offset = cognitiveRouter.write(type, header, quantized);
            } else {
                throw e;
            }
        }
        signal.offset(offset);

        // 4. Index Synchronization (HNSW, ID Index, WAL, BM25, SPLADE)
        final Map<String, String> metadata = (context != null && context.hasMetadata()) ? context.metadata() : null;
        final PostIngestSync.SyncParams syncParams = new PostIngestSync.SyncParams(
                signal.id(),
                signal.text(),
                vector,
                quantized,
                type,
                signal.tags(),
                signal.source(),
                offset,
                metadata
        );

        final int graphSlot = postIngestSync.syncIndexes(syncParams);
        signal.graphSlot(graphSlot);
        signal.successful(true);

        return true;
    }

    @Override
    public String relayName() {
        return RelayNames.CORTICAL_WRITE;
    }

    /**
     * Updates the cognitive memory router upon partition roll.
     */
    public void updateCognitiveRouter(final CognitiveMemoryRouter newRouter) {
        this.cognitiveRouter = Objects.requireNonNull(newRouter, "newRouter cannot be null");
        this.postIngestSync.updateCognitiveRouter(newRouter);
    }

    /**
     * Sets the callback invoked when a partition tier is full.
     */
    public void setPartitionRollCallback(final Runnable callback) {
        this.partitionRollCallback = callback;
    }

    public PostIngestSync postIngestSync() {
        return postIngestSync;
    }

    private static float[] l2Normalize(final float[] vector) {
        final float norm = VectorOps.magnitude(vector);
        if (norm == 0f || Math.abs(norm - 1.0f) < 1e-6f) return vector;
        return VectorOps.normalize(vector);
    }

    private static byte computeEncodingProfile(final SalienceProfile profile) {
        if (profile.alpha() != null || profile.beta() != null) {
            return SynapticHeaderConstants.soulDerivedEncodingProfile();
        }
        final CognitiveProfile cogProfile = profile.defaultProfile() != null
                ? profile.defaultProfile()
                : CognitiveProfile.BALANCED;
        return SynapticHeaderConstants.presetEncodingProfile(cogProfile.ordinal());
    }

    private static byte computeEncodingAlpha(final SalienceProfile profile) {
        final float alpha;
        if (profile.alpha() != null) {
            alpha = profile.alpha();
        } else {
            final CognitiveProfile cogProfile = profile.defaultProfile() != null
                    ? profile.defaultProfile()
                    : CognitiveProfile.BALANCED;
            alpha = cogProfile.alpha();
        }
        return SynapticHeaderConstants.quantizeWeight(alpha);
    }

    private static byte computeEncodingBeta(final SalienceProfile profile) {
        final float beta;
        if (profile.beta() != null) {
            beta = profile.beta();
        } else {
            final CognitiveProfile cogProfile = profile.defaultProfile() != null
                    ? profile.defaultProfile()
                    : CognitiveProfile.BALANCED;
            beta = cogProfile.beta();
        }
        return SynapticHeaderConstants.quantizeWeight(beta);
    }
}
