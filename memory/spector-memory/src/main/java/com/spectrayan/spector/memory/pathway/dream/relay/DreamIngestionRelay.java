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

import com.spectrayan.spector.kernel.api.TriageOutcome;
import com.spectrayan.spector.kernel.engram.field.EncodingHeaderFields;

import com.spectrayan.spector.commons.pathway.PathwayCatalog;
import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.memory.model.RememberResult;
import com.spectrayan.spector.memory.pathway.SoulVersionSource;
import com.spectrayan.spector.memory.pathway.remember.RememberPathway;
import com.spectrayan.spector.memory.pathway.remember.relay.RememberSignal;
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
 * <p>As of Phase 7, this relay invokes Remember via {@link PathwayCatalog} when available,
 * falling back to the legacy {@code signal.rememberPathway()} for backwards compatibility.</p>
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

        final short soulVersion = DreamPorts.resolveSoulVersion(signal);
        final PathwayCatalog catalog = signal.context() != null ? signal.context().catalog() : null;

        // 1. Ingest qualified surviving dream insights
        for (DreamSignal.DreamScene scene : signal.survivingScenes()) {
            if (scene.qualityScore() >= threshold) {
                if (scene.embedding() != null) {
                    try {
                        String durableId = signal.nextId();

                        if (catalog != null && catalog.find(RememberPathway.class).isPresent()) {
                            // Preferred: nested invocation via catalog
                            RememberSignal rememberSignal = DreamPorts.toRememberSignal(
                                    signal, scene, durableId, soulVersion);
                            RememberResult result = catalog.invoke(
                                    RememberPathway.class, signal.context(), rememberSignal);
                            DreamPorts.absorbRemembered(signal, result);
                        } else {
                            // Fallback: legacy direct call
                            @SuppressWarnings("deprecation")
                            final var rp = signal.rememberPathway();
                            if (rp != null) {
                                legacyIngest(signal, scene, durableId, soulVersion, rp);
                            }
                        }
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

    /**
     * Legacy direct ingestion via RememberPathway — preserved for backwards compatibility
     * when PathwayCatalog is not available.
     */
    @SuppressWarnings("deprecation")
    private static void legacyIngest(final DreamSignal signal,
                                     final DreamSignal.DreamScene scene,
                                     final String durableId,
                                     final short soulVersion,
                                     final RememberPathway rp) {
        com.spectrayan.spector.core.similarity.VectorOps.magnitude(scene.embedding()); // validate
        byte procFlags = EncodingHeaderFields.withMemoryType((byte) 0, com.spectrayan.spector.kernel.api.MemoryType.SEMANTIC.ordinal());
        float norm = com.spectrayan.spector.core.similarity.VectorOps.magnitude(scene.embedding());
        byte dreamFlags = (byte) (EncodingHeaderFields.FLAG_DREAMED | EncodingHeaderFields.FLAG_SIMULATED);

        com.spectrayan.spector.kernel.engram.EncodingHeader header = com.spectrayan.spector.kernel.engram.EncodingHeader.createSynthetic(
                signal.simulationTimeMs(), 0L, norm,
                scene.qualityScore(), (byte) 0, (byte) 128, procFlags,
                dreamFlags, soulVersion, 0.0f
        );

        com.spectrayan.spector.kernel.api.MemorySource src = signal.mode() == com.spectrayan.spector.kernel.api.DreamMode.THOUGHT_EXPERIMENT
                ? com.spectrayan.spector.kernel.api.MemorySource.THOUGHT_EXPERIMENT
                : com.spectrayan.spector.kernel.api.MemorySource.DREAMED;

        String text = scene.insightText() != null && !scene.insightText().isBlank()
                ? scene.narrative() + " | " + scene.insightText()
                : scene.narrative();

        rp.ingestCognitiveWithHeader(
                durableId, text, scene.embedding(),
                com.spectrayan.spector.kernel.api.MemoryType.SEMANTIC,
                new String[]{"dreamed", signal.mode().name().toLowerCase(), scene.triageOutcome().name().toLowerCase()},
                src, header
        );
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
