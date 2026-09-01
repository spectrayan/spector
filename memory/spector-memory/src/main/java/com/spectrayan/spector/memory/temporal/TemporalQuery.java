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
package com.spectrayan.spector.memory.temporal;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Fluent query builder for temporal fact retrieval.
 *
 * <h3>Biological Analog: Cue-Dependent Retrieval</h3>
 * <p>Just as the brain narrows memory search based on contextual cues
 * (who, what, when), this builder progressively constrains the fact
 * search space. Each filter method adds a cue that refines the result
 * set, mirroring how temporal and semantic context improves recall
 * precision in episodic memory retrieval.</p>
 *
 * <h3>Usage Examples</h3>
 * <pre>
 * // Current state: "What do we know about Alice?"
 * tkg.factsAbout(aliceId).validAt(Instant.now()).excludeRetracted().resolve();
 *
 * // Point-in-time: "Where did Alice work in Jan 2024?"
 * tkg.factsAbout(aliceId).withPredicate("works_at")
 *     .validAt(Instant.parse("2024-01-15T00:00:00Z")).resolve();
 *
 * // Bitemporal: "What did we know about Alice as of last Tuesday?"
 * tkg.factsAbout(aliceId).validAt(Instant.now())
 *     .asOf(Instant.parse("2026-07-22T00:00:00Z")).resolve();
 *
 * // Interval: "Facts about Project Alpha valid during Q1 2026?"
 * tkg.factsAbout(projectId).validDuring(q1Start, q1End).resolve();
 * </pre>
 *
 * @see TemporalKnowledgeGraph
 * @see TemporalFact
 * @see ContradictionResolver
 */
public final class TemporalQuery {

    private final TemporalKnowledgeGraph graph;
    private final int entityId;

    // ── Filter state ──
    private Instant validAtInstant;
    private Instant validDuringFrom;
    private Instant validDuringTo;
    private Instant asOfInstant;
    private String predicateName;
    private int predicateId = -1;
    private float minConfidence = 0.0f;
    private boolean excludeRetracted = false;
    private boolean excludeInferred = false;

    /**
     * Creates a new query builder scoped to facts about the given entity.
     *
     * @param graph    the TKG instance
     * @param entityId the subject entity ID to query
     */
    TemporalQuery(TemporalKnowledgeGraph graph, int entityId) {
        this.graph = graph;
        this.entityId = entityId;
    }

    /**
     * Filters facts valid at a specific instant.
     *
     * <p>Returns facts where {@code validFrom <= instant < validTo}.</p>
     *
     * @param instant the point in time to query
     * @return this builder
     */
    public TemporalQuery validAt(Instant instant) {
        this.validAtInstant = instant;
        return this;
    }

    /**
     * Filters facts valid during a time interval (Allen's overlap).
     *
     * <p>Returns facts where the fact's valid interval overlaps with
     * {@code [from, to)}: {@code fact.validFrom < to && fact.validTo > from}.</p>
     *
     * @param from interval start (inclusive)
     * @param to   interval end (exclusive)
     * @return this builder
     */
    public TemporalQuery validDuring(Instant from, Instant to) {
        this.validDuringFrom = from;
        this.validDuringTo = to;
        return this;
    }

    /**
     * Adds a transaction-time cutoff for bitemporal queries.
     *
     * <p>Only facts asserted before {@code asOf} are considered. This
     * reconstructs the system's knowledge state at a prior point in
     * time <em>without</em> WAL replay.</p>
     *
     * @param asOf transaction-time cutoff
     * @return this builder
     */
    public TemporalQuery asOf(Instant asOf) {
        this.asOfInstant = asOf;
        return this;
    }

    /**
     * Filters facts by predicate name (e.g., "works_at", "has_title").
     *
     * @param predicate the predicate name
     * @return this builder
     */
    public TemporalQuery withPredicate(String predicate) {
        this.predicateName = predicate;
        return this;
    }

    /**
     * Filters facts by minimum confidence score.
     *
     * @param minConfidence minimum confidence [0.0, 1.0]
     * @return this builder
     */
    public TemporalQuery withMinConfidence(float minConfidence) {
        this.minConfidence = minConfidence;
        return this;
    }

    /**
     * Excludes facts that have been retracted.
     *
     * @return this builder
     */
    public TemporalQuery excludeRetracted() {
        this.excludeRetracted = true;
        return this;
    }

    /**
     * Excludes facts that were inferred (LLM-extracted) rather than
     * explicitly asserted.
     *
     * @return this builder
     */
    public TemporalQuery excludeInferred() {
        this.excludeInferred = true;
        return this;
    }

    /**
     * Executes the query and returns matching facts.
     *
     * <p>Evaluation order:
     * <ol>
     *   <li>Load all facts for the subject entity</li>
     *   <li>Filter by valid-time (point or interval)</li>
     *   <li>Filter by transaction-time (asOf cutoff)</li>
     *   <li>Filter by predicate</li>
     *   <li>Filter by confidence threshold</li>
     *   <li>Filter by flags (inferred, retracted)</li>
     *   <li>Resolve contradictions per predicate</li>
     * </ol>
     * </p>
     *
     * @return list of matching facts, ordered by txTime descending
     */
    public List<TemporalFact> resolve() {
        // 1. Load all facts for entity
        List<TemporalFact> candidates = graph.readFactsForEntity(entityId);
        if (candidates.isEmpty()) {
            return List.of();
        }

        // Resolve predicate name to ID (if specified)
        if (predicateName != null) {
            predicateId = graph.predicateRegistry().idOf(predicateName);
            if (predicateId < 0) {
                // Predicate not registered — no facts can match
                return List.of();
            }
        }

        // 2. Get retracted fact IDs (if needed)
        Set<Integer> retractedIds = excludeRetracted ? graph.retractedFactIds() : Set.of();

        // 3. Apply all filters
        List<TemporalFact> filtered = new ArrayList<>();
        for (TemporalFact fact : candidates) {
            // Skip retractions themselves
            if (fact.isRetraction()) continue;

            // Retraction filter
            if (excludeRetracted && retractedIds.contains(fact.factId())) continue;

            // Valid-time point filter
            if (validAtInstant != null && !fact.validAtInstant(validAtInstant)) continue;

            // Valid-time interval filter (Allen's overlap)
            if (validDuringFrom != null && validDuringTo != null) {
                if (!fact.validDuring(validDuringFrom.toEpochMilli(),
                        validDuringTo.toEpochMilli())) continue;
            }

            // Transaction-time cutoff (bitemporal)
            if (asOfInstant != null && fact.txTime() > asOfInstant.toEpochMilli()) continue;

            // Predicate filter
            if (predicateId >= 0 && fact.predicateId() != predicateId) continue;

            // Confidence filter
            if (fact.confidence() < minConfidence) continue;

            // Inferred filter
            if (excludeInferred && fact.isInferred()) continue;

            filtered.add(fact);
        }

        if (filtered.isEmpty()) {
            return List.of();
        }

        // 4. Contradiction resolution: group by predicate, resolve each group
        Map<Integer, List<TemporalFact>> byPredicate = new HashMap<>();
        for (TemporalFact fact : filtered) {
            byPredicate.computeIfAbsent(fact.predicateId(), k -> new ArrayList<>()).add(fact);
        }

        List<TemporalFact> resolved = new ArrayList<>();
        ContradictionResolver resolver = graph.resolver();
        for (Map.Entry<Integer, List<TemporalFact>> entry : byPredicate.entrySet()) {
            List<TemporalFact> group = entry.getValue();
            if (group.size() == 1) {
                resolved.add(group.get(0));
            } else {
                // Multiple facts for same predicate — resolve
                resolved.add(resolver.resolve(group));
            }
        }

        // 5. Sort by txTime descending (most recent first)
        resolved.sort(Comparator.comparingLong(TemporalFact::txTime).reversed());
        return resolved;
    }

    /**
     * Executes the query and returns all matching facts without
     * contradiction resolution. Useful for auditing and history review.
     *
     * @return list of all matching facts, ordered by txTime descending
     */
    public List<TemporalFact> resolveAll() {
        List<TemporalFact> candidates = graph.readFactsForEntity(entityId);
        if (candidates.isEmpty()) {
            return List.of();
        }

        if (predicateName != null) {
            predicateId = graph.predicateRegistry().idOf(predicateName);
            if (predicateId < 0) return List.of();
        }

        Set<Integer> retractedIds = excludeRetracted ? graph.retractedFactIds() : Set.of();

        List<TemporalFact> filtered = new ArrayList<>();
        for (TemporalFact fact : candidates) {
            if (fact.isRetraction()) continue;
            if (excludeRetracted && retractedIds.contains(fact.factId())) continue;
            if (validAtInstant != null && !fact.validAtInstant(validAtInstant)) continue;
            if (validDuringFrom != null && validDuringTo != null) {
                if (!fact.validDuring(validDuringFrom.toEpochMilli(),
                        validDuringTo.toEpochMilli())) continue;
            }
            if (asOfInstant != null && fact.txTime() > asOfInstant.toEpochMilli()) continue;
            if (predicateId >= 0 && fact.predicateId() != predicateId) continue;
            if (fact.confidence() < minConfidence) continue;
            if (excludeInferred && fact.isInferred()) continue;
            filtered.add(fact);
        }

        filtered.sort(Comparator.comparingLong(TemporalFact::txTime).reversed());
        return filtered;
    }
}
