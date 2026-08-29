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
import com.spectrayan.spector.memory.consolidation.CadpContradictionResolver;
import com.spectrayan.spector.memory.graph.EntityDirectory;
import com.spectrayan.spector.memory.graph.EntityExtractor;
import com.spectrayan.spector.memory.graph.ExtractedEntity;
import com.spectrayan.spector.memory.index.MemoryIndex;
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
    private final MemoryIndex index;
    
    public TemporalFactWeavingStage(TemporalKnowledgeGraph tkg, EntityDirectory entityDirectory, EntityExtractor entityExtractor) {
        this(tkg, entityDirectory, entityExtractor, null);
    }

    public TemporalFactWeavingStage(TemporalKnowledgeGraph tkg, EntityDirectory entityDirectory,
                                    EntityExtractor entityExtractor, MemoryIndex index) {
        this.tkg = tkg;
        this.entityDirectory = entityDirectory;
        this.entityExtractor = entityExtractor;
        this.index = index;
    }
    
    public void weave(List<CognitiveResult> candidates, float[] queryVector, RecallOptions options) {
        if (tkg == null || tkg.factCount() == 0 || candidates.isEmpty()) return;
        
        Instant asOf = options.replayTimestamp() != null ? options.replayTimestamp() : Instant.now();
        
        for (int i = 0; i < candidates.size(); i++) {
            CognitiveResult candidate = candidates.get(i);
            try {
                java.util.Set<Integer> entityIds = new java.util.HashSet<>();

                // Priority 1: Fast O(1) off-heap lookup via EntityDirectory index slot
                if (entityDirectory != null && index != null) {
                    MemoryIndex.MemoryLocation loc = index.locate(candidate.id());
                    if (loc != null) {
                        int slot = loc.graphSlot() >= 0 ? loc.graphSlot() : (int) (loc.offset() / 164);
                        List<Integer> slotEntityIds = CadpContradictionResolver.findEntitiesForSlot(entityDirectory, slot);
                        if (slotEntityIds != null) {
                            entityIds.addAll(slotEntityIds);
                        }
                    }
                }

                // Priority 2: Pre-extracted hints from options
                if (entityIds.isEmpty() && options.entityHints() != null && !options.entityHints().isEmpty() && entityDirectory != null) {
                    for (ExtractedEntity hint : options.entityHints()) {
                        int eid = entityDirectory.findEntity(hint.name());
                        if (eid >= 0) entityIds.add(eid);
                    }
                }

                if (entityIds.isEmpty()) continue;

                List<String> prioritizedFacts = new java.util.ArrayList<>();
                List<String> otherFacts = new java.util.ArrayList<>();
                String candTextLower = candidate.text() != null ? candidate.text().toLowerCase() : "";

                for (int entityId : entityIds) {
                    List<TemporalFact> facts = tkg.factsAbout(entityId).validAt(asOf).resolve();
                    if (facts != null && !facts.isEmpty()) {
                        for (TemporalFact fact : facts) {
                            String subj = entityDirectory != null ? entityDirectory.entityName(fact.subjectEntityId()) : null;
                            String pred = tkg.predicateRegistry() != null ? tkg.predicateRegistry().nameOf(fact.predicateId()) : "related_to";
                            String obj = (fact.objectEntityId() >= 0 && entityDirectory != null) ? entityDirectory.entityName(fact.objectEntityId()) : "";
                            
                            if (subj != null && pred != null && !pred.isBlank() && obj != null && !obj.isBlank()) {
                                String validTimeStr = "";
                                if (fact.validFrom() > 0) {
                                    if (fact.validFrom() < 3000) {
                                        validTimeStr = " (Valid: " + fact.validFrom() + (fact.isOngoing() ? " - Present" : " - " + fact.validTo()) + ")";
                                    } else {
                                        Instant fromInst = Instant.ofEpochMilli(fact.validFrom() > 100000000000L ? fact.validFrom() : fact.validFrom() * 1000L);
                                        String dateStr = java.time.format.DateTimeFormatter.ISO_LOCAL_DATE.withZone(java.time.ZoneOffset.UTC).format(fromInst);
                                        validTimeStr = " (Valid: " + dateStr + (fact.isOngoing() ? " - Present" : "") + ")";
                                    }
                                }
                                String factLine = "[Temporal Fact: " + subj + " " + pred + " " + obj + validTimeStr + "]";
                                String predLower = pred.toLowerCase();
                                boolean isKeyAttribute = predLower.contains("moved_from") || predLower.contains("home_country")
                                        || predLower.contains("born_in") || predLower.contains("occupation")
                                        || predLower.contains("has_pet") || predLower.contains("goal")
                                        || predLower.contains("career");

                                if (candTextLower.contains(obj.toLowerCase()) || candTextLower.contains(predLower)
                                        || (isKeyAttribute && (candTextLower.contains("home") || candTextLower.contains("country") || candTextLower.contains("pet") || candTextLower.contains("work")))) {
                                    prioritizedFacts.add(factLine);
                                } else {
                                    otherFacts.add(factLine);
                                }
                            }
                        }
                    }
                }

                List<String> selectedFacts = new java.util.ArrayList<>(prioritizedFacts);
                if (selectedFacts.size() < 3) {
                    for (String of : otherFacts) {
                        if (!selectedFacts.contains(of)) {
                            selectedFacts.add(of);
                            if (selectedFacts.size() >= 3) break;
                        }
                    }
                }

                if (!selectedFacts.isEmpty()) {
                    java.util.Map<String, String> meta = candidate.metadata();
                    if (meta == null) {
                        meta = new java.util.HashMap<>();
                    } else {
                        meta = new java.util.HashMap<>(meta);
                    }
                    meta.put("tkg_valid_facts", String.valueOf(selectedFacts.size()));
                    
                    String updatedText = String.join("\n", selectedFacts) + "\n" + (candidate.text() != null ? candidate.text() : "");
                    candidates.set(i, candidate.withModality(candidate.sourceModality(), meta).withText(updatedText));
                }
            } catch (Exception e) {
                log.debug("Failed to weave temporal facts for candidate '{}': {}", candidate.id(), e.getMessage());
            }
        }
    }
}
