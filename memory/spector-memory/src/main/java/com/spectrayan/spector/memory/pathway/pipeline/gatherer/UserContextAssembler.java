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
package com.spectrayan.spector.memory.pathway.pipeline.gatherer;

import com.spectrayan.spector.memory.model.SalienceProfile;
import com.spectrayan.spector.memory.graph.temporal.TemporalFact;

import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.UserContext;
import com.spectrayan.spector.memory.graph.temporal.TemporalKnowledgeGraph;
import com.spectrayan.spector.memory.graph.temporal.TemporalFact;
import com.spectrayan.spector.memory.graph.EntityDirectory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Pure formatter that takes the recall pipeline's final CognitiveResult list
 * and formats it into a UserContext. No LLM calls.
 */
public final class UserContextAssembler {

    private final TemporalKnowledgeGraph tkg;
    private final EntityDirectory directory;

    public UserContextAssembler(TemporalKnowledgeGraph tkg, EntityDirectory directory) {
        this.tkg = tkg;
        this.directory = directory;
    }

    public UserContext assemble(List<CognitiveResult> results, SalienceProfile profile) {
        String personaSummary = "";
        if (profile != null && profile.hasPersona()) {
            personaSummary = profile.persona().about();
            if (personaSummary == null) personaSummary = "";
        }

        Set<Integer> mentionedEntityIds = new HashSet<>();
        Map<String, Integer> nameIndex = directory != null ? directory.nameIndex() : Map.of();
        
        for (CognitiveResult result : results) {
            if (result.text() == null) continue;
            String text = result.text().toLowerCase(Locale.ROOT);
            for (Map.Entry<String, Integer> entry : nameIndex.entrySet()) {
                if (text.contains(entry.getKey())) {
                    mentionedEntityIds.add(entry.getValue());
                }
            }
        }

        List<UserContext.TemporalBelief> beliefs = new ArrayList<>();
        if (tkg != null) {
            for (int entityId : mentionedEntityIds) {
                List<TemporalFact> facts = tkg.factsAbout(entityId).resolve();
                for (TemporalFact fact : facts) {
                    String subject = getEntityName(fact.subjectEntityId(), nameIndex);
                    String predicate = tkg.predicateRegistry().nameOf(fact.predicateId());
                    String object = fact.objectEntityId() != TemporalFact.SENTINEL_ENTITY ? 
                        getEntityName(fact.objectEntityId(), nameIndex) : "literal";
                    beliefs.add(new UserContext.TemporalBelief(subject, predicate, object, 
                        fact.validFrom(), fact.validTo(), fact.confidence()));
                }
            }
        }

        List<UserContext.MemoryChunk> relevantChunks = results.stream()
            .map(r -> new UserContext.MemoryChunk(r.id(), r.text(), r.memoryType(), r.score()))
            .collect(Collectors.toList());

        List<UserContext.CausalNarrative> narratives = extractNarratives(results);

        return new UserContext(personaSummary, beliefs, relevantChunks, narratives, Map.of());
    }

    private String getEntityName(int id, Map<String, Integer> nameIndex) {
        for (Map.Entry<String, Integer> entry : nameIndex.entrySet()) {
            if (entry.getValue() == id) {
                return entry.getKey();
            }
        }
        return "unknown";
    }

    private List<UserContext.CausalNarrative> extractNarratives(List<CognitiveResult> results) {
        List<UserContext.CausalNarrative> narratives = new ArrayList<>();
        for (CognitiveResult r : results) {
            if (r.metadata() != null && "TEMPORAL".equals(r.metadata().get("graph_source"))) {
                String seedId = r.metadata().get("graph_seed_id");
                if (seedId != null) {
                    narratives.add(new UserContext.CausalNarrative(
                        "Temporal chain sequence", 
                        List.of(seedId, r.id())
                    ));
                }
            }
        }
        return narratives;
    }
}
