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
package com.spectrayan.spector.memory.graph.causal;

import com.spectrayan.spector.memory.graph.EntityDirectory;
import com.spectrayan.spector.memory.graph.HyperEntityGraphMemory;
import com.spectrayan.spector.memory.graph.HyperEntityGraphMemory.HyperEdge;
import com.spectrayan.spector.memory.graph.OntologyConfig;
import com.spectrayan.spector.memory.graph.TypeRegistryMemory;
import com.spectrayan.spector.memory.cortex.index.MemoryIndex;
import com.spectrayan.spector.memory.model.CognitiveRecord;
import com.spectrayan.spector.memory.graph.temporal.TemporalFact;
import com.spectrayan.spector.memory.graph.temporal.TemporalKnowledgeGraph;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Function;

/**
 * Causal reasoning engine that traverses causal subgraphs to answer "Why did X happen?"
 * and "What resulted from Y?" queries across the knowledge graph (ADR-0010, #273).
 *
 * <h3>Reasoning Modes</h3>
 * <ul>
 *   <li><b>Backward Causal Reasoning ({@link #traceWhy}):</b> Traverses antecedent links
 *       ({@code CAUSED_BY}, {@code TRIGGERED_BY}, {@code RESULTED_FROM}, and inverse
 *       {@code CAUSES}) from an effect/symptom back to its root cause.</li>
 *   <li><b>Forward Consequence Reasoning ({@link #traceEffects}):</b> Traverses downstream
 *       consequences ({@code CAUSES}, {@code LED_TO}, {@code TRIGGERED}) from an event
 *       forward to its cascading impacts.</li>
 * </ul>
 *
 * <h3>Graph Backing</h3>
 * <p>Operates over both {@link TemporalKnowledgeGraph} (bitemporal facts) and
 * {@link HyperEntityGraphMemory} (n-ary typed hyperedges), backed by the central
 * {@link EntityDirectory} for entity identity resolution.</p>
 *
 * @since 1.1.0
 */
public final class CausalQueryEngine {

    private static final Logger log = LoggerFactory.getLogger(CausalQueryEngine.class);

    private static final Set<String> BACKWARD_CAUSAL_PREDICATES = Set.of(
            "CAUSED_BY", "TRIGGERED_BY", "RESULTED_FROM", "INDUCED_BY",
            "DUE_TO", "ROOT_CAUSE_IS", "CONSEQUENCE_OF"
    );

    private static final Set<String> FORWARD_CAUSAL_PREDICATES = Set.of(
            "CAUSES", "LED_TO", "TRIGGERED", "INDUCES", "RESULTS_IN",
            "ROOT_CAUSE_OF", "ACTIVATED", "SPARKED"
    );

    private static final Set<String> PREVENTATIVE_PREDICATES = Set.of(
            "PREVENTED", "PREVENTED_BY", "BLOCKED", "MITIGATED", "AVOIDED"
    );

    private final EntityDirectory entityDirectory;
    private final HyperEntityGraphMemory hyperEntityGraph;
    private final TemporalKnowledgeGraph temporalKnowledgeGraph;
    private final TypeRegistryMemory predicateRegistry;
    private final OntologyConfig ontologyConfig;
    private final MemoryIndex memoryIndex;
    private final Function<String, CognitiveRecord> recordInspector;

    public CausalQueryEngine(EntityDirectory entityDirectory,
                              HyperEntityGraphMemory hyperEntityGraph,
                              TemporalKnowledgeGraph temporalKnowledgeGraph,
                              TypeRegistryMemory predicateRegistry,
                              OntologyConfig ontologyConfig,
                              MemoryIndex memoryIndex,
                              Function<String, CognitiveRecord> recordInspector) {
        this.entityDirectory = entityDirectory;
        this.hyperEntityGraph = hyperEntityGraph;
        this.temporalKnowledgeGraph = temporalKnowledgeGraph;
        this.predicateRegistry = predicateRegistry != null ? predicateRegistry
                : (temporalKnowledgeGraph != null ? temporalKnowledgeGraph.predicateRegistry() : null);
        this.ontologyConfig = ontologyConfig != null ? ontologyConfig : OntologyConfig.defaultInstance();
        this.memoryIndex = memoryIndex;
        this.recordInspector = recordInspector;
    }

    /**
     * Answers "Why did X happen?" or "What caused X?" by traversing causal antecedents
     * backward from the target entity up to {@code maxHops}.
     *
     * @param targetEntityName the name of the entity, incident, or outcome to explain
     * @param maxHops          maximum traversal depth (typically 3 to 7)
     * @return structured causal chain with ordered steps and natural language narrative
     */
    public CausalChain traceWhy(String targetEntityName, int maxHops) {
        if (targetEntityName == null || targetEntityName.isBlank()) {
            return CausalChain.empty("unknown", CausalChain.Direction.BACKWARD_WHY, "Target entity is null or blank");
        }

        int targetId = resolveEntityId(targetEntityName);
        if (targetId < 0) {
            return CausalChain.empty(targetEntityName, CausalChain.Direction.BACKWARD_WHY,
                    "Entity '" + targetEntityName + "' not found in knowledge graph");
        }

        String canonicalTargetName = entityDirectory != null ? entityDirectory.entityName(targetId) : targetEntityName;

        List<CausalStep> steps = new ArrayList<>();
        Set<Integer> visited = new HashSet<>();
        visited.add(targetId);

        int currentId = targetId;
        String currentName = canonicalTargetName;
        float cumulativeConfidence = 1.0f;
        int hops = 0;
        int effectiveMaxHops = maxHops > 0 ? maxHops : 5;

        while (hops < effectiveMaxHops) {
            CausalCandidate bestCandidate = findBestAntecedent(currentId, visited);
            if (bestCandidate == null) {
                break;
            }

            int nextId = bestCandidate.entityId();
            String nextName = entityDirectory != null ? entityDirectory.entityName(nextId) : ("Entity#" + nextId);
            String predicate = bestCandidate.predicate();
            float weight = bestCandidate.weight();
            int memIdx = bestCandidate.memoryIdx();

            String memId = resolveMemoryId(memIdx);
            String snippet = resolveSnippet(memId);

            CausalStep step = new CausalStep(currentName, predicate, nextName, weight, memIdx, memId, snippet);
            steps.add(step);
            visited.add(nextId);

            cumulativeConfidence *= Math.max(0.1f, Math.min(1.0f, weight));
            currentId = nextId;
            currentName = nextName;
            hops++;
        }

        if (steps.isEmpty()) {
            return CausalChain.empty(canonicalTargetName, CausalChain.Direction.BACKWARD_WHY,
                    "No causal antecedent relationships recorded for '" + canonicalTargetName + "'");
        }

        String rootCause = currentName;
        String explanation = formatBackwardExplanation(canonicalTargetName, steps, rootCause, cumulativeConfidence);

        return new CausalChain(
                canonicalTargetName,
                CausalChain.Direction.BACKWARD_WHY,
                steps,
                rootCause,
                cumulativeConfidence,
                explanation
        );
    }

    /**
     * Answers "What resulted from X?" or "What did X cause?" by traversing causal consequences
     * forward from the source entity up to {@code maxHops}.
     *
     * @param sourceEntityName the name of the entity or event to trace effects from
     * @param maxHops          maximum traversal depth
     * @return structured causal chain with downstream consequences
     */
    public CausalChain traceEffects(String sourceEntityName, int maxHops) {
        if (sourceEntityName == null || sourceEntityName.isBlank()) {
            return CausalChain.empty("unknown", CausalChain.Direction.FORWARD_EFFECTS, "Source entity is null or blank");
        }

        int sourceId = resolveEntityId(sourceEntityName);
        if (sourceId < 0) {
            return CausalChain.empty(sourceEntityName, CausalChain.Direction.FORWARD_EFFECTS,
                    "Entity '" + sourceEntityName + "' not found in knowledge graph");
        }

        String canonicalSourceName = entityDirectory != null ? entityDirectory.entityName(sourceId) : sourceEntityName;

        List<CausalStep> steps = new ArrayList<>();
        Set<Integer> visited = new HashSet<>();
        visited.add(sourceId);

        int currentId = sourceId;
        String currentName = canonicalSourceName;
        float cumulativeConfidence = 1.0f;
        int hops = 0;
        int effectiveMaxHops = maxHops > 0 ? maxHops : 5;

        while (hops < effectiveMaxHops) {
            CausalCandidate bestCandidate = findBestConsequence(currentId, visited);
            if (bestCandidate == null) {
                break;
            }

            int nextId = bestCandidate.entityId();
            String nextName = entityDirectory != null ? entityDirectory.entityName(nextId) : ("Entity#" + nextId);
            String predicate = bestCandidate.predicate();
            float weight = bestCandidate.weight();
            int memIdx = bestCandidate.memoryIdx();

            String memId = resolveMemoryId(memIdx);
            String snippet = resolveSnippet(memId);

            CausalStep step = new CausalStep(currentName, predicate, nextName, weight, memIdx, memId, snippet);
            steps.add(step);
            visited.add(nextId);

            cumulativeConfidence *= Math.max(0.1f, Math.min(1.0f, weight));
            currentId = nextId;
            currentName = nextName;
            hops++;
        }

        if (steps.isEmpty()) {
            return CausalChain.empty(canonicalSourceName, CausalChain.Direction.FORWARD_EFFECTS,
                    "No downstream causal consequences recorded for '" + canonicalSourceName + "'");
        }

        String ultimateEffect = currentName;
        String explanation = formatForwardExplanation(canonicalSourceName, steps, ultimateEffect, cumulativeConfidence);

        return new CausalChain(
                canonicalSourceName,
                CausalChain.Direction.FORWARD_EFFECTS,
                steps,
                ultimateEffect,
                cumulativeConfidence,
                explanation
        );
    }

    // ═══════════════════════════════════════════════════════════════
    // ANTECEDENT & CONSEQUENCE SEARCH
    // ═══════════════════════════════════════════════════════════════

    private record CausalCandidate(int entityId, String predicate, float weight, int memoryIdx) {}

    private CausalCandidate findBestAntecedent(int entityId, Set<Integer> visited) {
        List<CausalCandidate> candidates = new ArrayList<>();

        // 1. Check TemporalKnowledgeGraph
        if (temporalKnowledgeGraph != null) {
            try {
                var facts = temporalKnowledgeGraph.factsAbout(entityId).resolveAll();
                var retracted = temporalKnowledgeGraph.retractedFactIds();
                for (TemporalFact fact : facts) {
                    if (retracted.contains(fact.factId())) continue;
                    String pred = resolvePredicateName(fact.predicateId());
                    if (isBackwardCausal(pred) && fact.objectEntityId() != TemporalFact.SENTINEL_ENTITY && !visited.contains(fact.objectEntityId())) {
                        candidates.add(new CausalCandidate(fact.objectEntityId(), pred, fact.confidence(), -1));
                    }
                }
            } catch (Exception e) {
                log.debug("TKG fact lookup failed for entityId {}: {}", entityId, e.getMessage());
            }
        }

        // 2. Check HyperEntityGraph
        if (hyperEntityGraph != null) {
            try {
                List<HyperEdge> edges = hyperEntityGraph.findHyperedgesForEntity(entityId);
                for (HyperEdge edge : edges) {
                    String pred = resolvePredicateName(edge.type());
                    if (edge.vertices().size() == 2) {
                        int v0 = edge.vertices().get(0).entityId();
                        int v1 = edge.vertices().get(1).entityId();

                        if (v0 == entityId && isBackwardCausal(pred) && !visited.contains(v1)) {
                            candidates.add(new CausalCandidate(v1, pred, edge.weight(), edge.memoryIdx()));
                        } else if (v1 == entityId && isForwardCausal(pred) && !visited.contains(v0)) {
                            String inv = ontologyConfig.inversePredicate(pred).orElse("CAUSED_BY");
                            candidates.add(new CausalCandidate(v0, inv, edge.weight(), edge.memoryIdx()));
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("Hypergraph edge lookup failed for entityId {}: {}", entityId, e.getMessage());
            }
        }

        if (candidates.isEmpty()) return null;
        candidates.sort((a, b) -> Float.compare(b.weight(), a.weight()));
        return candidates.get(0);
    }

    private CausalCandidate findBestConsequence(int entityId, Set<Integer> visited) {
        List<CausalCandidate> candidates = new ArrayList<>();

        // 1. Check TemporalKnowledgeGraph
        if (temporalKnowledgeGraph != null) {
            try {
                var facts = temporalKnowledgeGraph.factsAbout(entityId).resolveAll();
                var retracted = temporalKnowledgeGraph.retractedFactIds();
                for (TemporalFact fact : facts) {
                    if (retracted.contains(fact.factId())) continue;
                    String pred = resolvePredicateName(fact.predicateId());
                    if (isForwardCausal(pred) && fact.objectEntityId() != TemporalFact.SENTINEL_ENTITY && !visited.contains(fact.objectEntityId())) {
                        candidates.add(new CausalCandidate(fact.objectEntityId(), pred, fact.confidence(), -1));
                    }
                }
            } catch (Exception e) {
                log.debug("TKG consequence lookup failed for entityId {}: {}", entityId, e.getMessage());
            }
        }

        // 2. Check HyperEntityGraph
        if (hyperEntityGraph != null) {
            try {
                List<HyperEdge> edges = hyperEntityGraph.findHyperedgesForEntity(entityId);
                for (HyperEdge edge : edges) {
                    String pred = resolvePredicateName(edge.type());
                    if (edge.vertices().size() == 2) {
                        int v0 = edge.vertices().get(0).entityId();
                        int v1 = edge.vertices().get(1).entityId();

                        if (v0 == entityId && isForwardCausal(pred) && !visited.contains(v1)) {
                            candidates.add(new CausalCandidate(v1, pred, edge.weight(), edge.memoryIdx()));
                        } else if (v1 == entityId && isBackwardCausal(pred) && !visited.contains(v0)) {
                            String inv = ontologyConfig.inversePredicate(pred).orElse("CAUSES");
                            candidates.add(new CausalCandidate(v0, inv, edge.weight(), edge.memoryIdx()));
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("Hypergraph consequence lookup failed for entityId {}: {}", entityId, e.getMessage());
            }
        }

        if (candidates.isEmpty()) return null;
        candidates.sort((a, b) -> Float.compare(b.weight(), a.weight()));
        return candidates.get(0);
    }

    // ═══════════════════════════════════════════════════════════════
    // ENTITY & PREDICATE RESOLUTION HELPERS
    // ═══════════════════════════════════════════════════════════════

    public int resolveEntityId(String name) {
        if (entityDirectory == null || name == null) return -1;
        var nameIndex = entityDirectory.nameIndex();
        Integer direct = nameIndex.get(name.trim().toLowerCase(Locale.ROOT));
        if (direct != null) return direct;

        // Case-insensitive / prefix fallback
        String lower = name.trim().toLowerCase(Locale.ROOT);
        for (var entry : nameIndex.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(lower) || entry.getKey().contains(lower) || lower.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return -1;
    }

    private String resolvePredicateName(int predicateId) {
        if (predicateRegistry != null) {
            String name = predicateRegistry.nameOf(predicateId);
            if (name != null && !name.equals("UNKNOWN")) return name;
        }
        return "RELATED_TO";
    }

    private boolean isBackwardCausal(String predicate) {
        if (predicate == null) return false;
        String upper = predicate.toUpperCase(Locale.ROOT);
        if (BACKWARD_CAUSAL_PREDICATES.contains(upper)) return true;
        Optional<String> canonical = ontologyConfig.resolvePredicate(upper);
        return canonical.map(BACKWARD_CAUSAL_PREDICATES::contains).orElse(false);
    }

    private boolean isForwardCausal(String predicate) {
        if (predicate == null) return false;
        String upper = predicate.toUpperCase(Locale.ROOT);
        if (FORWARD_CAUSAL_PREDICATES.contains(upper)) return true;
        Optional<String> canonical = ontologyConfig.resolvePredicate(upper);
        return canonical.map(FORWARD_CAUSAL_PREDICATES::contains).orElse(false);
    }

    private String resolveMemoryId(int memoryIdx) {
        if (memoryIdx < 0 || memoryIndex == null) return null;
        Map<Integer, String> slotToId = new HashMap<>();
        Map<String, Integer> idToSlot = new HashMap<>();
        memoryIndex.buildGraphSlotMappings(slotToId, idToSlot);
        return slotToId.get(memoryIdx);
    }

    private String resolveSnippet(String memoryId) {
        if (memoryId == null || recordInspector == null) return null;
        try {
            CognitiveRecord record = recordInspector.apply(memoryId);
            if (record != null && record.text() != null) {
                String text = record.text().trim();
                return text.length() > 140 ? text.substring(0, 137) + "..." : text;
            }
        } catch (Exception ignored) {}
        return null;
    }

    // ═══════════════════════════════════════════════════════════════
    // FORMATTERS
    // ═══════════════════════════════════════════════════════════════

    private String formatBackwardExplanation(String target, List<CausalStep> steps, String rootCause, float confidence) {
        StringBuilder sb = new StringBuilder();
        sb.append("### Causal Explanation for **").append(target).append("**\n\n");
        sb.append("**Reasoning Chain:**\n");

        for (int i = 0; i < steps.size(); i++) {
            CausalStep s = steps.get(i);
            sb.append(i + 1).append(". **").append(s.sourceEntity()).append("**")
              .append(" was *").append(s.relation()).append("* by **").append(s.targetEntity()).append("**");
            if (s.snippet() != null) {
                sb.append(" _(\"").append(s.snippet()).append("\")_");
            }
            sb.append("\n");
        }

        sb.append("\n**Identified Root Cause:** `").append(rootCause).append("`\n");
        sb.append("**Causal Confidence:** ").append(String.format(Locale.ROOT, "%.1f%%", confidence * 100f));
        return sb.toString();
    }

    private String formatForwardExplanation(String source, List<CausalStep> steps, String ultimateEffect, float confidence) {
        StringBuilder sb = new StringBuilder();
        sb.append("### Downstream Consequences of **").append(source).append("**\n\n");
        sb.append("**Cascading Effects:**\n");

        for (int i = 0; i < steps.size(); i++) {
            CausalStep s = steps.get(i);
            sb.append(i + 1).append(". **").append(s.sourceEntity()).append("**")
              .append(" *").append(s.relation()).append("* **").append(s.targetEntity()).append("**");
            if (s.snippet() != null) {
                sb.append(" _(\"").append(s.snippet()).append("\")_");
            }
            sb.append("\n");
        }

        sb.append("\n**Terminal Consequence:** `").append(ultimateEffect).append("`\n");
        sb.append("**Causal Confidence:** ").append(String.format(Locale.ROOT, "%.1f%%", confidence * 100f));
        return sb.toString();
    }
}
