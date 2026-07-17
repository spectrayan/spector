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

    public static final AgentSoul DEFAULT_FALLBACK_SOUL = AgentSoul.builder()
            .id("default")
            .name("Assistant")
            .description("Default Assistant")
            .systemPrompt("You are a helpful AI assistant.")
            .purpose("Help users")
            .personality("Friendly and helpful")
            .expertiseDomains(List.of())
            .coreValues(List.of())
            .ethicalGuardrails(List.of())
            .emotionalBaseline(AgentSoul.EmotionalBaseline.NEUTRAL)
            .communicationStyle("professional")
            .model("qwen3.5:latest")
            .tools(List.of())
            .build();

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

    /** Lists all agent souls stored in cognitive memory. */
    public List<AgentSoul> listAllAgents() {
        var results = memoryService.recall(new RecallRequest("agent soul", 100, null));
        return results.stream()
                .filter(r -> r.tags() != null && r.tags().contains(TAG_AGENT_SOUL))
                .map(r -> fromJson(r.text(), AgentSoul.class))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
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

    /** Get the current active agent soul, or a default fallback. */
    public AgentSoul getActiveSoul() {
        return loadAgentSoul(null).orElse(DEFAULT_FALLBACK_SOUL);
    }

    /** Helper to find the current agent soul or return a default. */
    public AgentSoul getEffectiveSoul(String id) {
        return loadAgentSoul(id).orElse(DEFAULT_FALLBACK_SOUL);
    }

    /** Reset the active agent soul to default settings. */
    public void resetAgentSoul() {
        saveAgentSoul(DEFAULT_FALLBACK_SOUL);
    }

    /** Partially updates the active agent soul. */
    @SuppressWarnings("unchecked")
    public AgentSoul patchAgentSoul(Map<String, Object> updates) {
        AgentSoul current = getActiveSoul();

        var builder = AgentSoul.builder()
                .id(current.id())
                .name(updates.containsKey("name") ? (String) updates.get("name") : current.name())
                .description(updates.containsKey("description") ? (String) updates.get("description") : current.description())
                .systemPrompt(updates.containsKey("systemPrompt") ? (String) updates.get("systemPrompt") : current.systemPrompt())
                .purpose(updates.containsKey("purpose") ? (String) updates.get("purpose") : current.purpose())
                .personality(updates.containsKey("personality") ? (String) updates.get("personality") : current.personality())
                .emotionalBaseline(updates.containsKey("emotionalBaseline") ? parseEmotionalBaseline(updates.get("emotionalBaseline")) : current.emotionalBaseline())
                .communicationStyle(updates.containsKey("communicationStyle") ? (String) updates.get("communicationStyle") : current.communicationStyle())
                .model(updates.containsKey("model") ? (String) updates.get("model") : current.model());

        if (updates.containsKey("expertiseDomains")) {
            builder.expertiseDomains((List<String>) updates.get("expertiseDomains"));
        } else {
            builder.expertiseDomains(current.expertiseDomains());
        }

        if (updates.containsKey("coreValues")) {
            builder.coreValues((List<String>) updates.get("coreValues"));
        } else {
            builder.coreValues(current.coreValues());
        }

        if (updates.containsKey("ethicalGuardrails")) {
            builder.ethicalGuardrails((List<String>) updates.get("ethicalGuardrails"));
        } else {
            builder.ethicalGuardrails(current.ethicalGuardrails());
        }

        if (updates.containsKey("tools")) {
            builder.tools((List<String>) updates.get("tools"));
        } else {
            builder.tools(current.tools());
        }

        AgentSoul updated = builder.build();
        saveAgentSoul(updated);
        return updated;
    }

    private static AgentSoul.EmotionalBaseline parseEmotionalBaseline(Object obj) {
        if (obj == null) {
            return AgentSoul.EmotionalBaseline.NEUTRAL;
        }
        if (obj instanceof AgentSoul.EmotionalBaseline eb) {
            return eb;
        }
        if (obj instanceof Map<?, ?> map) {
            Number val = (Number) map.get("defaultValence");
            Number ar = (Number) map.get("defaultArousal");
            byte valence = val != null ? val.byteValue() : 0;
            byte arousal = ar != null ? ar.byteValue() : (byte) 128;
            return new AgentSoul.EmotionalBaseline(valence, arousal);
        }
        if (obj instanceof String s) {
            return switch (s.toLowerCase()) {
                case "warm" -> AgentSoul.EmotionalBaseline.WARM;
                case "energetic" -> AgentSoul.EmotionalBaseline.ENERGETIC;
                default -> AgentSoul.EmotionalBaseline.NEUTRAL;
            };
        }
        return AgentSoul.EmotionalBaseline.NEUTRAL;
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
