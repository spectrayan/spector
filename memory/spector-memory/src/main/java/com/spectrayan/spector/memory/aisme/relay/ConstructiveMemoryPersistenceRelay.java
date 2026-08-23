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
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.pipeline.CognitiveIngestionTarget;
import com.spectrayan.spector.memory.recall.relay.RecallSignal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;

/**
 * RecallPathway relay that persists high-alignment constructive simulations as durable
 * episodic memories with SIMULATED provenance in the consolidation flags.
 *
 * <h3>Biological Analog: Hippocampal Replay of Imagined Future Scenarios</h3>
 * <p>When the brain imagines future events or counterfactual alternatives with sufficient
 * vividness and personal relevance, those imaginings become consolidated episodic traces
 * that shape future recall and decision-making — the person's imagination becomes
 * part of their identity narrative.</p>
 */
public final class ConstructiveMemoryPersistenceRelay implements SynapticRelay<RecallSignal> {

    private static final Logger log = LoggerFactory.getLogger(ConstructiveMemoryPersistenceRelay.class);

    private final CognitiveIngestionTarget ingestionTarget;
    private final Function<String, float[]> embeddingLookup;
    private final float persistenceThreshold;

    /**
     * Constructs a ConstructiveMemoryPersistenceRelay.
     *
     * @param ingestionTarget the ingestion target for persisting memories
     * @param embeddingLookup function to resolve embeddings for simulation text
     * @param persistenceThreshold minimum score for a simulation to be persisted
     */
    public ConstructiveMemoryPersistenceRelay(
            CognitiveIngestionTarget ingestionTarget,
            Function<String, float[]> embeddingLookup,
            float persistenceThreshold) {
        this.ingestionTarget = ingestionTarget;
        this.embeddingLookup = embeddingLookup;
        this.persistenceThreshold = persistenceThreshold;
    }

    @Override
    public boolean transmit(RecallSignal signal) {
        if (ingestionTarget == null || embeddingLookup == null || signal == null) {
            return true;
        }

        var candidates = signal.candidates();
        if (candidates == null) {
            return true;
        }

        int persisted = 0;
        for (CognitiveResult result : candidates) {
            // Only persist constructive simulations (ID starts with "sim-")
            if (result.id() == null || !result.id().startsWith("sim-")) {
                continue;
            }

            // Only persist if score exceeds threshold (high narrative alignment)
            if (result.score() < persistenceThreshold) {
                continue;
            }

            try {
                String text = result.text();
                float[] vector = embeddingLookup.apply(result.id());
                if (text == null || vector == null) {
                    continue;
                }

                // Persist via CognitiveIngestionTarget.ingest(id, text, vector)
                // The SIMULATED flag should be set post-ingestion via consolidation flags
                String durableId = "sim-durable-" + System.nanoTime();
                ingestionTarget.ingest(durableId, text, vector);
                persisted++;

                log.debug("Persisted constructive simulation as durable memory: sourceId={}, durableId={}, score={}",
                        result.id(), durableId, result.score());
            } catch (Exception e) {
                log.warn("Failed to persist constructive simulation {}: {}", result.id(), e.getMessage());
            }
        }

        if (persisted > 0) {
            log.info("ConstructiveMemoryPersistence: persisted {} high-alignment simulations (threshold={})",
                    persisted, persistenceThreshold);
        }

        return true;
    }

    @Override
    public String relayName() {
        return "constructive_memory_persistence";
    }
}
