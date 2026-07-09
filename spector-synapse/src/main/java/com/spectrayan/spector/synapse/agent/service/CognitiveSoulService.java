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
package com.spectrayan.spector.synapse.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrayan.spector.memory.model.PersonaContext;
import com.spectrayan.spector.synapse.agent.AgentSoul;
import com.spectrayan.spector.synapse.memory.MemoryDto.RecallRequest;
import com.spectrayan.spector.synapse.memory.MemoryDto.StoreRequest;
import com.spectrayan.spector.synapse.memory.MemoryService;
import com.spectrayan.spector.synapse.config.SynapseSalienceProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service for managing agent and user souls using Spector's cognitive memory.
 *
 * <p>Replaces H2-based storage with cognitive persistence. Souls are serialized
 * to JSON and stored as memories with specific identity tags.</p>
 *
 * <p>When the user persona is loaded or saved, this service automatically
 * updates the {@link SynapseSalienceProvider} so that the memory engine
 * applies user-salience-driven importance, valence, and arousal modulation
 * on all subsequent ingestion and recall operations.</p>
 */
@Service
public class CognitiveSoulService {

    private static final Logger log = LoggerFactory.getLogger(CognitiveSoulService.class);
    private static final String TAG_AGENT_SOUL = "identity:agent";
    private static final String TAG_USER_SOUL = "identity:user";

    private final MemoryService memoryService;
    private final ObjectMapper mapper;
    private final SynapseSalienceProvider salienceProvider;

    public CognitiveSoulService(MemoryService memoryService, ObjectMapper mapper,
                                SynapseSalienceProvider salienceProvider) {
        this.memoryService = memoryService;
        this.mapper = mapper;
        this.salienceProvider = salienceProvider;
    }

    /** Loads an agent soul by ID (or the default if ID is null). */
    public Optional<AgentSoul> loadAgentSoul(String id) {
        String query = id != null ? "agent soul id:" + id : "default agent soul";
        var results = memoryService.recall(new RecallRequest(query, 5, null));

        return results.stream()
                .filter(r -> r.tags() != null && r.tags().contains(TAG_AGENT_SOUL))
                .map(r -> fromJson(r.text(), AgentSoul.class))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
    }

    /** Saves an agent soul to cognitive memory. */
    public void saveAgentSoul(AgentSoul soul) {
        // First, "forget" old versions to keep memory clean
        forgetOldSouls(TAG_AGENT_SOUL);

        String json = toJson(soul);
        memoryService.store(new StoreRequest(
                json,
                List.of(TAG_AGENT_SOUL, "soul:" + soul.id()),
                null,
                Map.of()
        ));
        log.info("[CognitiveSoul] Saved agent soul '{}'", soul.name());
    }

    /**
     * Loads the user soul (PersonaContext).
     *
     * <p>When a user persona is found, it is automatically applied to the
     * {@link SynapseSalienceProvider} so memory scoring reflects the user's
     * personality and identity.</p>
     */
    public Optional<PersonaContext> loadUserSoul() {
        var results = memoryService.recall(new RecallRequest("user persona context", 5, null));

        Optional<PersonaContext> persona = results.stream()
                .filter(r -> r.tags() != null && r.tags().contains(TAG_USER_SOUL))
                .map(r -> fromJson(r.text(), PersonaContext.class))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();

        // Propagate to salience provider
        persona.ifPresent(p -> {
            salienceProvider.updateUserPersona(p);
            log.info("[CognitiveSoul] User persona applied to salience provider");
        });

        return persona;
    }

    /**
     * Saves the user soul (PersonaContext).
     *
     * <p>After persistence, the persona is immediately applied to the
     * {@link SynapseSalienceProvider} so all subsequent memory operations
     * use the updated user salience profile.</p>
     */
    public void saveUserSoul(PersonaContext persona) {
        forgetOldSouls(TAG_USER_SOUL);

        String json = toJson(persona);
        memoryService.store(new StoreRequest(
                json,
                List.of(TAG_USER_SOUL),
                null,
                Map.of()
        ));

        // Propagate to salience provider
        salienceProvider.updateUserPersona(persona);

        log.info("[CognitiveSoul] Saved user persona context — salience profile updated");
    }

    /** Helper to find the current agent soul or return a default. */
    public AgentSoul getEffectiveSoul(String id) {
        return loadAgentSoul(id).orElseGet(() -> 
            AgentSoul.of("default", "Assistant", 
                "You are a cognitive assistant powered by the Spector Engine.")
        );
    }

    private void forgetOldSouls(String tag) {
        var results = memoryService.recall(new RecallRequest("identity", 10, null));
        results.stream()
                .filter(r -> r.tags() != null && r.tags().contains(tag))
                .forEach(r -> memoryService.forget(r.id()));
    }

    private String toJson(Object obj) {
        try {
            return mapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.error("[CognitiveSoul] Failed to serialize soul: {}", e.getMessage());
            return "{}";
        }
    }

    private <T> Optional<T> fromJson(String json, Class<T> clazz) {
        try {
            return Optional.of(mapper.readValue(json, clazz));
        } catch (Exception e) {
            log.warn("[CognitiveSoul] Failed to deserialize {}: {}", clazz.getSimpleName(), e.getMessage());
            return Optional.empty();
        }
    }
}
