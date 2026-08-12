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
package com.spectrayan.spector.memory.pipeline.graph;

import com.spectrayan.spector.memory.temporal.TemporalKnowledgeGraph;
import com.spectrayan.spector.memory.temporal.TemporalFact;
import com.spectrayan.spector.memory.graph.EntityDirectory;
import com.spectrayan.spector.memory.graph.EntityExtractor;
import com.spectrayan.spector.memory.graph.ExtractedEntity;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.RecallOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.time.Instant;

/**
 * Enriches recall candidates with temporally-valid facts from the TKG.
 * Invoked after GraphExpansionStage and before the reranker.
 */
public final class TemporalFactWeavingStage {
    private static final Logger log = LoggerFactory.getLogger(TemporalFactWeavingStage.class);
    
    private final TemporalKnowledgeGraph tkg;
    private final EntityDirectory entityDirectory;
    private final EntityExtractor entityExtractor;
    
    public TemporalFactWeavingStage(TemporalKnowledgeGraph tkg, EntityDirectory entityDirectory, EntityExtractor entityExtractor) {
        this.tkg = tkg;
        this.entityDirectory = entityDirectory;
        this.entityExtractor = entityExtractor;
    }
    
    public void weave(List<CognitiveResult> candidates, float[] queryVector, RecallOptions options) {
        if (tkg == null || tkg.factCount() == 0 || candidates.isEmpty()) return;
        
        Instant asOf = options.replayTimestamp() != null ? options.replayTimestamp() : Instant.now();
        
        for (int i = 0; i < candidates.size(); i++) {
            CognitiveResult candidate = candidates.get(i);
            try {
                if (entityExtractor != null && entityExtractor.isAvailable()) {
                    List<ExtractedEntity> entities = entityExtractor.extract(candidate.id(), candidate.text());
                    if (entities == null || entities.isEmpty()) continue;
                    
                    int validFactsCount = 0;
                    for (ExtractedEntity entity : entities) {
                        if (entityDirectory != null) {
                            int entityId = entityDirectory.findEntity(entity.name());
                            if (entityId < 0) continue;
                            
                            List<TemporalFact> facts = tkg.factsAbout(entityId).validAt(asOf).resolve();
                            if (facts != null && !facts.isEmpty()) {
                                validFactsCount += facts.size();
                            }
                        }
                    }
                    
                    if (validFactsCount > 0) {
                        java.util.Map<String, String> meta = candidate.metadata();
                        if (meta == null) {
                            meta = new java.util.HashMap<>();
                        } else {
                            meta = new java.util.HashMap<>(meta);
                        }
                        meta.put("tkg_valid_facts", String.valueOf(validFactsCount));
                        candidates.set(i, candidate.withModality(candidate.sourceModality(), meta));
                    }
                }
            } catch (Exception e) {
                log.debug("Failed to weave temporal facts for candidate '{}': {}", candidate.id(), e.getMessage());
            }
        }
    }
}
