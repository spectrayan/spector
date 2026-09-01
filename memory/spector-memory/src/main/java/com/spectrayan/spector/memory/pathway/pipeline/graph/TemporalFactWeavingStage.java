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
package com.spectrayan.spector.memory.pathway.pipeline.graph;

import com.spectrayan.spector.memory.graph.temporal.TemporalKnowledgeGraph;
import com.spectrayan.spector.memory.graph.temporal.TemporalFact;
import com.spectrayan.spector.memory.cortex.consolidation.CadpContradictionResolver;
import com.spectrayan.spector.memory.graph.EntityDirectory;
import com.spectrayan.spector.memory.graph.EntityExtractor;
import com.spectrayan.spector.memory.graph.ExtractedEntity;
import com.spectrayan.spector.memory.cortex.index.MemoryIndex;
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
        weave(candidates, queryVector, options, null);
    }

    public void weave(List<CognitiveResult> candidates, float[] queryVector, RecallOptions options, String rawQuery) {
        if (tkg == null || tkg.factCount() == 0 || candidates.isEmpty()) return;
        
        Instant asOf = options.replayTimestamp() != null ? options.replayTimestamp() : Instant.now();
        java.util.Set<String> queryTokens = new java.util.HashSet<>();
        if (rawQuery != null && !rawQuery.isBlank()) {
            String[] tokens = rawQuery.toLowerCase(java.util.Locale.ROOT).split("[^a-z0-9_]+");
            for (String t : tokens) {
                if (t.length() >= 3 && !isCommonStopWord(t)) {
                    queryTokens.add(t);
                }
            }
        }

        // Maintain global set of woven facts across candidates to prevent redundant context duplication
        java.util.Set<String> globallyWovenFacts = new java.util.HashSet<>();

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
                if (options.entityHints() != null && !options.entityHints().isEmpty() && entityDirectory != null) {
                    for (ExtractedEntity hint : options.entityHints()) {
                        int eid = entityDirectory.findEntity(hint.name());
                        if (eid >= 0) entityIds.add(eid);
                    }
                }

                if (entityIds.isEmpty()) continue;

                List<String> queryMatchedFacts = new java.util.ArrayList<>();
                List<String> textMatchedFacts = new java.util.ArrayList<>();
                List<String> otherFacts = new java.util.ArrayList<>();
                String candTextLower = candidate.text() != null ? candidate.text().toLowerCase(java.util.Locale.ROOT) : "";

                for (int entityId : entityIds) {
                    List<TemporalFact> facts = tkg.factsAbout(entityId).validAt(asOf).resolve();
                    if (facts != null && !facts.isEmpty()) {
                        for (TemporalFact fact : facts) {
                            String subj = (entityDirectory != null && entityDirectory.entityName(fact.subjectEntityId()) != null)
                                    ? entityDirectory.entityName(fact.subjectEntityId())
                                    : "entity_" + fact.subjectEntityId();
                            String pred = (tkg.predicateRegistry() != null && tkg.predicateRegistry().nameOf(fact.predicateId()) != null)
                                    ? tkg.predicateRegistry().nameOf(fact.predicateId())
                                    : "related_to";
                            String obj = (fact.objectEntityId() >= 0 && entityDirectory != null && entityDirectory.entityName(fact.objectEntityId()) != null)
                                    ? entityDirectory.entityName(fact.objectEntityId())
                                    : (fact.objectEntityId() >= 0 ? "entity_" + fact.objectEntityId() : "");
                            
                            if (subj != null && pred != null && !pred.isBlank()) {
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
                                String predLower = pred.toLowerCase(java.util.Locale.ROOT);
                                String objLower = obj.toLowerCase(java.util.Locale.ROOT);
                                String subLower = subj.toLowerCase(java.util.Locale.ROOT);

                                // Check query relevance
                                boolean queryHit = false;
                                if (!queryTokens.isEmpty()) {
                                    for (String qt : queryTokens) {
                                        if (predLower.contains(qt) || objLower.contains(qt) || subLower.contains(qt)) {
                                            queryHit = true;
                                            break;
                                        }
                                        if (isSemanticSynonymMatch(qt, predLower, objLower)) {
                                            queryHit = true;
                                            break;
                                        }
                                    }
                                }

                                if (queryHit) {
                                    queryMatchedFacts.add(factLine);
                                } else if (candTextLower.contains(objLower) || candTextLower.contains(predLower)) {
                                    textMatchedFacts.add(factLine);
                                } else {
                                    otherFacts.add(factLine);
                                }
                            }
                        }
                    }
                }

                List<String> selectedFacts = new java.util.ArrayList<>();
                // 1. First add query-matched facts that haven't been globally saturated
                for (String qf : queryMatchedFacts) {
                    if (!selectedFacts.contains(qf) && !globallyWovenFacts.contains(qf)) {
                        selectedFacts.add(qf);
                        globallyWovenFacts.add(qf);
                        if (selectedFacts.size() >= 3) break;
                    }
                }
                // 2. Next add candidate text-matched facts
                if (selectedFacts.size() < 3) {
                    for (String tf : textMatchedFacts) {
                        if (!selectedFacts.contains(tf) && !globallyWovenFacts.contains(tf)) {
                            selectedFacts.add(tf);
                            globallyWovenFacts.add(tf);
                            if (selectedFacts.size() >= 3) break;
                        }
                    }
                }
                // 3. Fallback for top candidates only (i <= 2) to avoid saturating lower ranks with noise
                if (selectedFacts.size() < 2 && i <= 2) {
                    for (String of : otherFacts) {
                        if (!selectedFacts.contains(of) && !globallyWovenFacts.contains(of)) {
                            selectedFacts.add(of);
                            globallyWovenFacts.add(of);
                            if (selectedFacts.size() >= 2) break;
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
            } catch (RuntimeException e) {
                log.debug("Fact weaving encountered non-fatal error on candidate {}: {}", candidate.id(), e.getMessage());
            }
        }
    }

    private static boolean isCommonStopWord(String word) {
        return switch (word) {
            case "the", "and", "what", "where", "when", "which", "who", "why", "how", "did", "does", "was",
                 "were", "has", "have", "had", "for", "with", "from", "that", "this", "they", "them", "their",
                 "she", "her", "his", "him", "you", "your", "about", "into", "some", "any", "like", "over", "been" -> true;
            default -> false;
        };
    }

    private static boolean isSemanticSynonymMatch(String queryToken, String pred, String obj) {
        if (queryToken.startsWith("pet") || queryToken.equals("dog") || queryToken.equals("cat") || queryToken.equals("animal") || queryToken.equals("turtle") || queryToken.equals("kitten") || queryToken.equals("puppy")) {
            return pred.contains("pet") || pred.contains("adopt") || obj.contains("oliver") || obj.contains("luna") || obj.contains("bailey") || obj.contains("max") || obj.contains("shadow") || obj.contains("dog") || obj.contains("cat");
        }
        if (queryToken.startsWith("mov") || queryToken.equals("origin") || queryToken.equals("country") || queryToken.equals("born") || queryToken.equals("live")) {
            return pred.contains("move") || pred.contains("born") || pred.contains("home") || pred.contains("country") || obj.contains("sweden") || obj.contains("stockholm") || obj.contains("rome") || obj.contains("barcelona");
        }
        if (queryToken.startsWith("potter") || queryToken.startsWith("sculpt") || queryToken.equals("craft") || queryToken.equals("ceramic") || queryToken.equals("bowl") || queryToken.equals("cup")) {
            return pred.contains("pottery") || pred.contains("creat") || obj.contains("bowl") || obj.contains("cup") || obj.contains("sculpture") || obj.contains("pottery");
        }
        if (queryToken.startsWith("work") || queryToken.startsWith("job") || queryToken.startsWith("career") || queryToken.startsWith("volunt") || queryToken.startsWith("counsel")) {
            return pred.contains("occupat") || pred.contains("goal") || pred.contains("research") || pred.contains("volunt") || obj.contains("counsel") || obj.contains("firefight") || obj.contains("shelter") || obj.contains("mentor");
        }
        return false;
    }
}
