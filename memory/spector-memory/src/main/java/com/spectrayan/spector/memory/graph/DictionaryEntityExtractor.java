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
package com.spectrayan.spector.memory.graph;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.spectrayan.spector.kernel.store.TemporalFact;
import com.spectrayan.spector.memory.graph.temporal.TemporalKnowledgeGraph;

/**
 * High-performance ontology-driven entity extractor that matches terms and multi-word phrases
 * in memory text against known entities in {@link EntityDirectory} and enterprise taxonomy types
 * in {@link OntologyConfig}.
 *
 * <p>Contains zero hardcoded keywords or entity lists. All vocabulary, aliases, and predicates
 * are loaded dynamically from {@link OntologyConfig} (e.g. {@code entity-types.yaml},
 * {@code relationship-predicates.yaml}) and {@link EntityDirectory#nameIndex()}. Stopwords are
 * externalized to {@code ontology-stopwords.txt}.</p>
 *
 * <p>When {@link TemporalKnowledgeGraph} is present, it also attaches structured
 * relations between any co-occurring entities that have active facts in the graph.</p>
 *
 * <p>Used as the primary extractor when running offline without external LLM providers,
 * or as a zero-latency fast path / fallback during background graph enrichment.</p>
 *
 * @since 0.1.0-beta
 */
public final class DictionaryEntityExtractor implements EntityExtractor {

    private static final Logger log = LoggerFactory.getLogger(DictionaryEntityExtractor.class);

    private static final Pattern WORD_SPLIT = Pattern.compile("[\\s,;:.!?()\\[\\]{}\"'/\\\\<>]+");
    private static final String STOPWORDS_RESOURCE = "/ontology-stopwords.txt";

    private final EntityDirectory entityDirectory;
    private final TemporalKnowledgeGraph temporalKnowledgeGraph;
    private final OntologyConfig ontologyConfig;
    private final int maxEntitiesPerMemory;
    private final Set<String> stopwords;

    public DictionaryEntityExtractor(EntityDirectory entityDirectory) {
        this(entityDirectory, null, null, 15);
    }

    public DictionaryEntityExtractor(EntityDirectory entityDirectory, TemporalKnowledgeGraph temporalKnowledgeGraph) {
        this(entityDirectory, temporalKnowledgeGraph, null, 15);
    }

    public DictionaryEntityExtractor(EntityDirectory entityDirectory, TemporalKnowledgeGraph temporalKnowledgeGraph,
                                     OntologyConfig ontologyConfig) {
        this(entityDirectory, temporalKnowledgeGraph, ontologyConfig, 15);
    }

    public DictionaryEntityExtractor(EntityDirectory entityDirectory, TemporalKnowledgeGraph temporalKnowledgeGraph,
                                     OntologyConfig ontologyConfig, int maxEntitiesPerMemory) {
        this.entityDirectory = entityDirectory;
        this.temporalKnowledgeGraph = temporalKnowledgeGraph;
        this.ontologyConfig = ontologyConfig != null ? ontologyConfig : OntologyConfig.defaultInstance();
        this.maxEntitiesPerMemory = Math.max(1, maxEntitiesPerMemory);
        this.stopwords = loadStopwords();
    }

    private static Set<String> loadStopwords() {
        Set<String> words = new HashSet<>();
        try (InputStream in = DictionaryEntityExtractor.class.getResourceAsStream(STOPWORDS_RESOURCE)) {
            if (in != null) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (!line.isEmpty() && !line.startsWith("#")) {
                            words.add(line.toLowerCase(Locale.ROOT));
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to load stopwords from {}: {}", STOPWORDS_RESOURCE, e.getMessage());
        }
        return Collections.unmodifiableSet(words);
    }

    @Override
    public List<ExtractedEntity> extract(String id, String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }

        Map<String, Integer> nameIndex = entityDirectory != null ? entityDirectory.nameIndex() : Collections.emptyMap();

        // Tokenize text for candidate n-gram generation
        String[] rawTokens = WORD_SPLIT.split(text);
        List<String> tokens = new ArrayList<>(rawTokens.length);
        for (String raw : rawTokens) {
            String t = raw.trim().toLowerCase(Locale.ROOT);
            if (!t.isEmpty()) {
                tokens.add(t);
            }
        }

        if (tokens.isEmpty()) {
            return Collections.emptyList();
        }

        // Map: matched phrase / entity key -> ExtractedCandidate
        record Candidate(String name, String typeName, Integer entityId) {}
        Map<String, Candidate> matchedCandidates = new LinkedHashMap<>();

        int n = tokens.size();

        // 1. Match n-grams up to 4 tokens against EntityDirectory and OntologyConfig
        for (int i = 0; i < n; i++) {
            StringBuilder phrase = new StringBuilder();
            for (int j = i; j < Math.min(n, i + 4); j++) {
                if (j > i) {
                    phrase.append(' ');
                }
                phrase.append(tokens.get(j));
                String cand = phrase.toString();
                if (cand.length() < 3) {
                    continue;
                }
                if (j == i && stopwords.contains(cand)) {
                    continue;
                }

                // Check EntityDirectory first
                Integer entityId = nameIndex.get(cand);
                if (entityId != null && entityId >= 0) {
                    String canonicalName = entityDirectory.entityName(entityId);
                    String entityType = entityDirectory.entityType(entityId);
                    matchedCandidates.putIfAbsent(cand, new Candidate(
                            canonicalName != null ? canonicalName : cand,
                            entityType != null ? entityType : "OTHER",
                            entityId));
                    continue;
                }

                // Check OntologyConfig taxonomy (canonical types and aliases)
                if (ontologyConfig != null) {
                    String upper = cand.replace(' ', '_').toUpperCase(Locale.ROOT);
                    if (ontologyConfig.isKnownType(upper)) {
                        matchedCandidates.putIfAbsent(cand, new Candidate(cand, upper, null));
                    } else {
                        var resolved = ontologyConfig.resolveType(upper);
                        if (resolved.isPresent()) {
                            matchedCandidates.putIfAbsent(cand, new Candidate(cand, resolved.get(), null));
                        }
                    }
                }
            }
        }

        // 2. Check whitespace-split tokens (preserving tech tags, hyphens, prefixes like @)
        String[] wsTokens = text.split("\\s+");
        for (String ws : wsTokens) {
            String cand = ws.trim().toLowerCase(Locale.ROOT);
            while (cand.length() > 3 && (cand.startsWith("(") || cand.startsWith("[") || cand.startsWith("\"") || cand.startsWith("'") || cand.startsWith("@"))) {
                cand = cand.substring(1);
            }
            while (cand.length() > 3 && (cand.endsWith(")") || cand.endsWith("]") || cand.endsWith("\"") || cand.endsWith("'") || cand.endsWith(",") || cand.endsWith("."))) {
                cand = cand.substring(0, cand.length() - 1);
            }
            if (cand.length() >= 3 && !stopwords.contains(cand)) {
                Integer entityId = nameIndex.get(cand);
                if (entityId != null && entityId >= 0) {
                    String canonicalName = entityDirectory.entityName(entityId);
                    String entityType = entityDirectory.entityType(entityId);
                    matchedCandidates.putIfAbsent(cand, new Candidate(
                            canonicalName != null ? canonicalName : cand,
                            entityType != null ? entityType : "OTHER",
                            entityId));
                }
            }
        }

        if (matchedCandidates.isEmpty()) {
            return Collections.emptyList();
        }

        // Sort by length descending to prioritize more specific entity phrases
        List<Candidate> sortedCandidates = new ArrayList<>(matchedCandidates.values());
        sortedCandidates.sort(Comparator.comparingInt((Candidate c) -> c.name().length()).reversed());
        if (sortedCandidates.size() > maxEntitiesPerMemory) {
            sortedCandidates = sortedCandidates.subList(0, maxEntitiesPerMemory);
        }

        Set<Integer> selectedEntityIds = new HashSet<>();
        for (Candidate c : sortedCandidates) {
            if (c.entityId() != null) {
                selectedEntityIds.add(c.entityId());
            }
        }

        // Build ExtractedEntity instances with relations from TemporalKnowledgeGraph
        List<ExtractedEntity> results = new ArrayList<>(sortedCandidates.size());
        for (Candidate c : sortedCandidates) {
            List<EntityRelation> relations = Collections.emptyList();
            if (c.entityId() != null && temporalKnowledgeGraph != null && temporalKnowledgeGraph.predicateRegistry() != null) {
                var facts = temporalKnowledgeGraph.readFactsForEntity(c.entityId());
                if (facts != null && !facts.isEmpty()) {
                    Set<Integer> retractedIds = temporalKnowledgeGraph.retractedFactIds();
                    var predRegistry = temporalKnowledgeGraph.predicateRegistry();
                    relations = new ArrayList<>();
                    for (TemporalFact fact : facts) {
                        if (fact.isRetraction()) continue;
                        if (retractedIds != null && retractedIds.contains(fact.factId())) continue;
                        int targetId = fact.objectEntityId();
                        if (targetId != c.entityId() && selectedEntityIds.contains(targetId)) {
                            String targetName = entityDirectory.entityName(targetId);
                            if (targetName != null && !targetName.isBlank()) {
                                String predName = predRegistry.nameOf((int) fact.predicateId());
                                if (predName == null || predName.isBlank()) {
                                    predName = "RELATED_TO";
                                }
                                relations.add(new EntityRelation(targetName, predName));
                            }
                        }
                    }
                }
            }

            results.add(new ExtractedEntity(c.name(), c.typeName(), relations));
        }

        return results;
    }

    @Override
    public boolean isAvailable() {
        return entityDirectory != null || ontologyConfig != null;
    }
}
