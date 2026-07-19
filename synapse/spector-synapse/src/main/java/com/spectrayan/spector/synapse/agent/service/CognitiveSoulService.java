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
import com.spectrayan.spector.synapse.config.model.ConfigCategory;
import com.spectrayan.spector.synapse.config.model.ScopedConfig;
import com.spectrayan.spector.synapse.config.repository.ConfigRepository;
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
    private final ConfigRepository configRepository;

    public CognitiveSoulService(MemoryService memoryService, ObjectMapper mapper,
                                SynapseSalienceProvider salienceProvider,
                                ConfigRepository configRepository) {
        this.memoryService = memoryService;
        this.mapper = mapper;
        this.salienceProvider = salienceProvider;
        this.configRepository = configRepository;
    }

    /** Loads an agent soul by ID (or the default if ID is null). */
    @SuppressWarnings("unchecked")
    public Optional<AgentSoul> loadAgentSoul(String id) {
        String scope = id != null ? "agent:" + id : "agent:default";
        return configRepository.get(scope, ConfigCategory.SOUL)
                .map(sc -> {
                    try {
                        return mapper.convertValue(sc.values(), AgentSoul.class);
                    } catch (Exception e) {
                        log.warn("Failed to convert agent soul configuration: {}", e.getMessage());
                        return null;
                    }
                });
    }

    /** Lists all agent souls stored in H2 database. */
    public List<AgentSoul> listAllAgents() {
        return configRepository.findByCategory(ConfigCategory.SOUL).stream()
                .filter(sc -> sc.scope().startsWith("agent:"))
                .map(sc -> {
                    try {
                        return mapper.convertValue(sc.values(), AgentSoul.class);
                    } catch (Exception e) {
                        log.warn("Failed to convert agent soul configuration: {}", e.getMessage());
                        return null;
                    }
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    /** Saves an agent soul to the database. */
    @SuppressWarnings("unchecked")
    public void saveAgentSoul(AgentSoul soul) {
        String scope = "agent:" + soul.id();
        Map<String, Object> values = mapper.convertValue(soul, Map.class);
        ScopedConfig sc = new ScopedConfig(
                scope,
                ConfigCategory.SOUL,
                values,
                java.time.Instant.now(),
                "default"
        );
        configRepository.save(sc);
        log.info("[CognitiveSoul] Saved agent soul '{}' in database", soul.name());
    }

    /**
     * Loads the user soul (PersonaContext).
     *
     * <p>When a user persona is found, it is automatically applied to the
     * {@link SynapseSalienceProvider} so memory scoring reflects the user's
     * personality and identity.</p>
     */
    public Optional<PersonaContext> loadUserSoul() {
        Optional<PersonaContext> persona = configRepository.get("user:default:default", ConfigCategory.SOUL)
                .map(sc -> {
                    try {
                        return mapper.convertValue(sc.values(), PersonaContext.class);
                    } catch (Exception e) {
                        log.warn("Failed to convert user soul configuration: {}", e.getMessage());
                        return null;
                    }
                });

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
    @SuppressWarnings("unchecked")
    public void saveUserSoul(PersonaContext persona) {
        if (persona == null) {
            configRepository.delete("user:default:default", ConfigCategory.SOUL);
            salienceProvider.updateUserPersona(null);
            log.info("[CognitiveSoul] Cleared user persona context");
            return;
        }

        Map<String, Object> values = mapper.convertValue(persona, Map.class);
        ScopedConfig sc = new ScopedConfig(
                "user:default:default",
                ConfigCategory.SOUL,
                values,
                java.time.Instant.now(),
                "default"
        );
        configRepository.save(sc);

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
