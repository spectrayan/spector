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
package com.spectrayan.spector.memory.aisme.relay;

import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.core.similarity.VectorOps;
import com.spectrayan.spector.kernel.api.MemorySource;
import com.spectrayan.spector.kernel.id.TsidGenerator;
import com.spectrayan.spector.kernel.engram.EncodingHeader;
import com.spectrayan.spector.kernel.engram.field.EncodingHeaderFields;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.memory.pathway.SoulVersionSource;
import com.spectrayan.spector.memory.pathway.remember.RememberPathway;
import com.spectrayan.spector.memory.pathway.recall.relay.RecallSignal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;

/**
 * RecallPathway relay that persists high-alignment constructive simulations as durable
 * episodic memories with FLAG_SIMULATED provenance in the binary header flags.
 *
 * <h3>Biological Analog: Hippocampal Replay of Imagined Future Scenarios</h3>
 * <p>When the brain imagines future events or counterfactual alternatives with sufficient
 * vividness and personal relevance, those imaginings become consolidated episodic traces
 * that shape future recall and decision-making — the person's imagination becomes
 * part of their identity narrative.</p>
 */
public final class ConstructiveMemoryPersistenceRelay implements SynapticRelay<RecallSignal> {

    private static final Logger log = LoggerFactory.getLogger(ConstructiveMemoryPersistenceRelay.class);
    private static final TsidGenerator TSID = new TsidGenerator();

    private final RememberPathway rememberPathway;
    private final Function<String, float[]> embeddingLookup;
    private final float persistenceThreshold;

    /**
     * Constructs a ConstructiveMemoryPersistenceRelay.
     *
     * @param rememberPathway the ingestion target for persisting memories
     * @param embeddingLookup function to resolve embeddings for simulation text
     * @param persistenceThreshold minimum score for a simulation to be persisted
     */
    public ConstructiveMemoryPersistenceRelay(
            RememberPathway rememberPathway,
            Function<String, float[]> embeddingLookup,
            float persistenceThreshold) {
        this.rememberPathway = rememberPathway;
        this.embeddingLookup = embeddingLookup;
        this.persistenceThreshold = persistenceThreshold;
    }

    @Override
    public boolean transmit(RecallSignal signal) {
        if (rememberPathway == null || signal == null) {
            return true;
        }

        var candidates = signal.candidates();
        if (candidates == null || candidates.isEmpty()) {
            return true;
        }

        int persisted = 0;
        for (CognitiveResult result : candidates) {
            // Only persist constructive simulations flagged with FLAG_SIMULATED
            if (!EncodingHeaderFields.isSimulated(result.consolidationFlags())) {
                continue;
            }

            // Only persist if alignment exceeds threshold (MR-07)
            float alignSim;
            if (result.metadata() != null && result.metadata().containsKey("alignSim")) {
                try {
                    alignSim = Float.parseFloat(result.metadata().get("alignSim"));
                } catch (NumberFormatException e) {
                    alignSim = result.score();
                }
            } else {
                alignSim = result.score();
            }

            if (alignSim < persistenceThreshold) {
                continue;
            }

            try {
                String text = result.text();
                float[] vector = (float[]) signal.attributes().get("simVec:" + result.id());
                if (vector == null && embeddingLookup != null) {
                    vector = embeddingLookup.apply(result.id());
                }
                if (text == null || vector == null) {
                    continue;
                }

                String durableId = TSID.generate();
                byte procFlags = EncodingHeaderFields.withMemoryType((byte) 0, MemoryType.EPISODIC.ordinal());
                float norm = VectorOps.magnitude(vector);
                short soulVer = signal.context() != null && signal.context().find(SoulVersionSource.class).isPresent()
                        ? signal.context().get(SoulVersionSource.class).currentSoulVersion()
                        : (rememberPathway != null ? rememberPathway.currentSoulVersion() : (short) 0);
                EncodingHeader header = EncodingHeader.createSynthetic(
                        System.currentTimeMillis(), 0L, norm, result.importance(),
                        result.valence(), (byte) 128, procFlags,
                        EncodingHeaderFields.FLAG_SIMULATED,
                        soulVer,
                        0.0f
                );

                rememberPathway.ingestCognitiveWithHeader(
                        durableId, text, vector, MemoryType.EPISODIC,
                        result.synapticTags() != null ? result.synapticTags() : new String[]{"simulated", "constructive"},
                        MemorySource.INFERRED, header
                );
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
