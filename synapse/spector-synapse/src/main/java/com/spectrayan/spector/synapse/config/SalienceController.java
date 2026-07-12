/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-synapse/LICENSE
 *
 * Change Date: July 6, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.synapse.config;

import com.spectrayan.spector.memory.model.InterestLevel;
import com.spectrayan.spector.memory.model.PersonaContext;
import com.spectrayan.spector.memory.neurodivergent.IcnuWeights;
import com.spectrayan.spector.synapse.agent.service.CognitiveSoulService;
import com.spectrayan.spector.synapse.config.SynapseSalienceProvider.InterestEntry;
import com.spectrayan.spector.synapse.config.SynapseSalienceProvider.SalienceSnapshot;
import com.spectrayan.spector.synapse.memory.MemoryAccessObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * REST controller for user salience profile management.
 *
 * <h3>Purpose</h3>
 * <p>Provides APIs for configuring how the memory engine scores memories
 * relative to the user's interests, personality, and cognitive preferences.
 * Changes take effect immediately for all subsequent ingestion and recall
 * operations.</p>
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>{@code GET  /api/v1/config/salience} — Current salience configuration</li>
 *   <li>{@code PUT  /api/v1/config/salience/interests} — Update topic interests</li>
 *   <li>{@code PUT  /api/v1/config/salience/weights} — Update scoring weights</li>
 *   <li>{@code PUT  /api/v1/config/salience/persona} — Update user persona</li>
 * </ul>
 *
 * <h3>Architecture</h3>
 * <pre>
 *   SalienceController   (HTTP layer — validation + routing)
 *        │
 *   SynapseSalienceProvider   (profile management + embedding computation)
 *        │
 *   SpectorMemory.setSalienceProfile()   (effective during ingestion/recall)
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/config/salience")
public class SalienceController {

    private static final Logger log = LoggerFactory.getLogger(SalienceController.class);

    private final SynapseSalienceProvider salienceProvider;
    private final CognitiveSoulService soulService;
    private final MemoryAccessObject mao;

    public SalienceController(SynapseSalienceProvider salienceProvider,
                              CognitiveSoulService soulService,
                              MemoryAccessObject mao) {
        this.salienceProvider = salienceProvider;
        this.soulService = soulService;
        this.mao = mao;
    }

    // ══════════════════════════════════════════════════════════════
    // GET — Current salience configuration
    // ══════════════════════════════════════════════════════════════

    /**
     * Returns the current salience configuration snapshot.
     *
     * <p>Includes active interests, disinterests, scoring weight overrides,
     * persona status, and whether the profile is currently active.</p>
     */
    @GetMapping
    public ResponseEntity<SalienceSnapshot> getSalienceConfig() {
        return ResponseEntity.ok(salienceProvider.snapshot());
    }

    // ══════════════════════════════════════════════════════════════
    // PUT — Update interests
    // ══════════════════════════════════════════════════════════════

    /**
     * Updates the user's topic interests and disinterests.
     *
     * <p>Embeddings are computed immediately for each topic, enabling
     * semantic matching against memory content during the scoring pipeline.</p>
     *
     * <h4>Example Request</h4>
     * <pre>{@code
     * PUT /api/v1/config/salience/interests
     * {
     *   "interests": [
     *     {"topic": "database performance", "level": "CRITICAL"},
     *     {"topic": "distributed systems", "level": "HIGH"}
     *   ],
     *   "disinterests": [
     *     {"topic": "celebrity gossip", "level": "IGNORE"}
     *   ]
     * }
     * }</pre>
     */
    @PutMapping("/interests")
    public ResponseEntity<Map<String, Object>> updateInterests(@RequestBody InterestsRequest request) {
        if (request.interests() == null && request.disinterests() == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "At least one of 'interests' or 'disinterests' must be provided"));
        }

        List<InterestEntry> interests = request.interests() != null
                ? request.interests().stream()
                        .map(e -> new InterestEntry(
                                e.topic(),
                                e.level() != null ? e.level() : InterestLevel.MEDIUM))
                        .toList()
                : List.of();

        List<InterestEntry> disinterests = request.disinterests() != null
                ? request.disinterests().stream()
                        .map(e -> new InterestEntry(
                                e.topic(),
                                e.level() != null ? e.level() : InterestLevel.LOW))
                        .toList()
                : List.of();

        salienceProvider.updateInterests(interests, disinterests);

        log.info("[SalienceAPI] Interests updated — {} interests, {} disinterests",
                interests.size(), disinterests.size());

        return ResponseEntity.ok(Map.of(
                "status", "updated",
                "interests", interests.size(),
                "disinterests", disinterests.size(),
                "message", "Topic interests applied. Embeddings computed for semantic matching."));
    }

    // ══════════════════════════════════════════════════════════════
    // PUT — Update scoring weights
    // ══════════════════════════════════════════════════════════════

    /**
     * Updates ICNU fusion weights and alpha/beta scoring overrides.
     *
     * <h4>Example Request</h4>
     * <pre>{@code
     * PUT /api/v1/config/salience/weights
     * {
     *   "icnu": {"interest": 0.30, "challenge": 0.10, "novelty": 0.40, "urgency": 0.20},
     *   "alpha": 0.6,
     *   "beta": 0.4
     * }
     * }</pre>
     */
    @PutMapping("/weights")
    public ResponseEntity<Map<String, Object>> updateWeights(@RequestBody WeightsRequest request) {
        IcnuWeights icnu = null;
        if (request.icnu() != null) {
            var i = request.icnu();
            icnu = new IcnuWeights(
                    i.interest() != null ? i.interest() : 0.30f,
                    i.challenge() != null ? i.challenge() : 0.10f,
                    i.novelty() != null ? i.novelty() : 0.40f,
                    i.urgency() != null ? i.urgency() : 0.20f);
        }

        salienceProvider.updateScoringWeights(icnu, request.alpha(), request.beta());

        log.info("[SalienceAPI] Scoring weights updated — icnu={}, alpha={}, beta={}",
                icnu != null, request.alpha(), request.beta());

        return ResponseEntity.ok(Map.of(
                "status", "updated",
                "message", "Scoring weights applied. Takes effect on next ingestion/recall."));
    }

    // ══════════════════════════════════════════════════════════════
    // PUT — Update user persona
    // ══════════════════════════════════════════════════════════════

    /**
     * Updates the user persona (personality, occupation, values, etc.).
     *
     * <p>This is a convenience wrapper around
     * {@link CognitiveSoulService#saveUserSoul(PersonaContext)}. The persona
     * is persisted in cognitive memory and immediately applied to the salience
     * provider.</p>
     */
    @PutMapping("/persona")
    public ResponseEntity<Map<String, Object>> updatePersona(@RequestBody PersonaContext persona) {
        if (persona == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Persona context must not be null"));
        }

        soulService.saveUserSoul(persona);

        log.info("[SalienceAPI] User persona updated — occupation={}, hasValues={}",
                persona.occupation(), !persona.values().isEmpty());

        return ResponseEntity.ok(Map.of(
                "status", "updated",
                "hasEmbeddings", persona.hasEmbeddings(),
                "message", "User persona applied. Self-relevance scoring and valence modulation active."));
    }

    // ══════════════════════════════════════════════════════════════
    // PUT — Rescore existing memories (Phase 3)
    // ══════════════════════════════════════════════════════════════

    /**
     * Triggers a rescore of all existing memories with the current salience profile.
     *
     * <p>When the user's salience profile changes (interests, persona, weights),
     * previously ingested memories still have their old importance scores. This
     * endpoint triggers the memory engine's consolidation pass to re-evaluate
     * importance scores under the new profile.</p>
     *
     * <h4>Example Request</h4>
     * <pre>{@code
     * PUT /api/v1/config/salience/rescore
     * }</pre>
     */
    @PutMapping("/rescore")
    public ResponseEntity<Map<String, Object>> rescoreMemories() {
        if (!mao.isAvailable()) {
            return ResponseEntity.ok(Map.of(
                    "status", "skipped",
                    "message", "Memory engine not available (stub mode)"));
        }

        long start = System.nanoTime();
        int rescored = mao.rescoreWithCurrentProfile();
        long durationMs = (System.nanoTime() - start) / 1_000_000;

        log.info("[SalienceAPI] Rescore completed — {} memories rescored in {}ms",
                rescored, durationMs);

        return ResponseEntity.ok(Map.of(
                "status", "completed",
                "rescored", rescored,
                "durationMs", durationMs,
                "message", "All memories rescored with current salience profile."));
    }

    // ══════════════════════════════════════════════════════════════
    // Request DTOs
    // ══════════════════════════════════════════════════════════════

    /**
     * Request body for updating topic interests.
     */
    public record InterestsRequest(
            List<InterestEntryDto> interests,
            List<InterestEntryDto> disinterests
    ) {}

    /**
     * A single interest entry in the request.
     */
    public record InterestEntryDto(String topic, InterestLevel level) {}

    /**
     * Request body for updating scoring weights.
     */
    public record WeightsRequest(IcnuDto icnu, Float alpha, Float beta) {}

    /**
     * ICNU weights in the request (nullable fields for partial updates).
     */
    public record IcnuDto(Float interest, Float challenge, Float novelty, Float urgency) {}
}
