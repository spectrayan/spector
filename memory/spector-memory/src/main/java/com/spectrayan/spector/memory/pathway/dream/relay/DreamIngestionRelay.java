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
package com.spectrayan.spector.memory.pathway.dream.relay;

import com.spectrayan.spector.core.similarity.VectorOps;
import com.spectrayan.spector.kernel.api.DreamMode;
import com.spectrayan.spector.kernel.api.MemorySource;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.kernel.api.TriageOutcome;
import com.spectrayan.spector.kernel.engram.EncodingHeader;
import com.spectrayan.spector.kernel.engram.field.EncodingHeaderFields;

import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.kernel.store.HebbianGraphBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stage 12 relay in {@link com.spectrayan.spector.memory.pathway.dream.DreamPathway}.
 *
 * <h3>Biological Analog: Dream-to-Memory Consolidation Gate &amp; Synaptic Downscaling</h3>
 * <p>Persists verified high-utility dream insights with {@code FLAG_DREAMED} provenance and applies
 * active Hebbian inhibition (\(\Delta w < 0\)) to the synaptic connections of failed dream fragment
 * pairs to prevent the cognitive engine from repeatedly simulating unproductive associations.</p>
 *
 * @since 1.4.0
 */
public final class DreamIngestionRelay implements SynapticRelay<DreamSignal> {

    private static final Logger log = LoggerFactory.getLogger(DreamIngestionRelay.class);

    @Override
    public boolean transmit(final DreamSignal signal) {
        if (signal == null) return true;

        float threshold = signal.config().persistenceThreshold();
        int eligibleCount = 0;

        // 1. Ingest qualified surviving dream insights
        for (DreamSignal.DreamScene scene : signal.survivingScenes()) {
            if (scene.qualityScore() >= threshold) {
                if (signal.rememberPathway() != null && scene.embedding() != null) {
                    try {
                        String durableId = signal.nextId();
                        byte procFlags = EncodingHeaderFields.withMemoryType((byte) 0, MemoryType.SEMANTIC.ordinal());
                        float norm = VectorOps.magnitude(scene.embedding());
                        short soulVer = signal.rememberPathway().currentSoulVersion();
                        byte dreamFlags = (byte) (EncodingHeaderFields.FLAG_DREAMED | EncodingHeaderFields.FLAG_SIMULATED);

                        EncodingHeader header = EncodingHeader.createSynthetic(
                                signal.simulationTimeMs(), 0L, norm,
                                scene.qualityScore(), (byte) 0, (byte) 128, procFlags,
                                dreamFlags, soulVer, 0.0f
                        );

                        MemorySource src = signal.mode() == DreamMode.THOUGHT_EXPERIMENT
                                ? MemorySource.THOUGHT_EXPERIMENT
                                : MemorySource.DREAMED;

                        String text = scene.insightText() != null && !scene.insightText().isBlank()
                                ? scene.narrative() + " | " + scene.insightText()
                                : scene.narrative();

                        signal.rememberPathway().ingestCognitiveWithHeader(
                                durableId,
                                text,
                                scene.embedding(),
                                MemoryType.SEMANTIC,
                                new String[]{"dreamed", signal.mode().name().toLowerCase(), scene.triageOutcome().name().toLowerCase()},
                                src,
                                header
                        );
                    } catch (Exception e) {
                        log.warn("DreamIngestionRelay: failed to persist dream insight {}: {}", scene.id(), e.getMessage());
                    }
                }
                log.info("DreamIngestionRelay: Ingested dream insight [{}] (Q={}, Mode={}): {}",
                        scene.id(), scene.qualityScore(), signal.mode(), scene.insightText());
                eligibleCount++;
            }
        }

        // Also account for Langevin discovery insights
        if (signal.extractedInsights() != null) {
            eligibleCount += signal.extractedInsights().size();
        }

        signal.dreamsIngested().set(eligibleCount);

        // 2. Active Hebbian inhibition on failed/noise dream pairings
        int failures = signal.failedPairs().get();
        if (failures > 0 && signal.hebbianGraph() != null) {
            HebbianGraphBase graph = signal.hebbianGraph();
            float inhibitionDelta = signal.config().hebbianInhibitionDelta();

            // Weaken synaptic association edges for failed seed combinations
            for (DreamSignal.DreamScene scene : signal.constructedScenes()) {
                if (scene.triageOutcome() == TriageOutcome.NOISE && scene.sourceIds().size() >= 2) {
                    int nodeA = parseNodeIndex(scene.sourceIds().get(0));
                    int nodeB = parseNodeIndex(scene.sourceIds().get(1));
                    if (nodeA >= 0 && nodeB >= 0 && nodeA < graph.capacity() && nodeB < graph.capacity() && nodeA != nodeB) {
                        graph.strengthen(nodeA, nodeB, inhibitionDelta);
                    }
                }
            }

            if (log.isDebugEnabled()) {
                log.debug("DreamIngestionRelay: applied Hebbian synaptic inhibition (delta={}) to {} failed dream pairs",
                        inhibitionDelta, failures);
            }
        }

        if (log.isDebugEnabled()) {
            log.debug("DreamIngestionRelay: completed dream ingestion cycle — {} insights persisted, {} failed pairs inhibited",
                    eligibleCount, failures);
        }

        return true;
    }

    private static int parseNodeIndex(String sourceId) {
        if (sourceId == null) return -1;
        int dash = sourceId.lastIndexOf('-');
        if (dash >= 0 && dash < sourceId.length() - 1) {
            try {
                return Integer.parseInt(sourceId.substring(dash + 1));
            } catch (NumberFormatException ignored) {
                return -1;
            }
        }
        return -1;
    }

    @Override
    public String relayName() {
        return "dream_ingestion";
    }
}
