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
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.model.AgentSoul;
import com.spectrayan.spector.memory.model.InsulaSelfModel;
import com.spectrayan.spector.memory.model.PersonaContext;
import com.spectrayan.spector.memory.model.UserSoul;
import com.spectrayan.spector.memory.model.SalienceProfile;
import com.spectrayan.spector.synapse.config.SynapseProperties;
import com.spectrayan.spector.synapse.config.SynapseSalienceProvider;
import com.spectrayan.spector.synapse.memory.UserMemoryRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Service for managing agent and user souls using Spector's INSULA cortex memory region.
 */
@Service
public class CognitiveSoulService {

    private static final Logger log = LoggerFactory.getLogger(CognitiveSoulService.class);

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
            .soulVersion((short) 1)
            .createdAt(java.time.Instant.now())
            .updatedAt(java.time.Instant.now())
            .build();

    private final UserMemoryRegistry userMemoryRegistry;
    private final ObjectMapper mapper;
    private final SynapseSalienceProvider salienceProvider;
    private final SynapseProperties synapseProps;

    public CognitiveSoulService(UserMemoryRegistry userMemoryRegistry,
                                ObjectMapper mapper,
                                SynapseSalienceProvider salienceProvider,
                                SynapseProperties synapseProps) {
        this.userMemoryRegistry = userMemoryRegistry;
        this.mapper = mapper;
        this.salienceProvider = salienceProvider;
        this.synapseProps = synapseProps;
    }

    /** Loads an agent soul by ID (or the default if ID is null). */
    public Optional<AgentSoul> loadAgentSoul(String id) {
        String namespaceId = "agent-" + (id != null ? id : "default");
        SpectorMemory memory = userMemoryRegistry.resolveFor(namespaceId);
        if (memory == null) {
            return Optional.empty();
        }

        return memory.admin().insularCortex().get()
                .flatMap(bytes -> fromJsonBytes(bytes, InsulaSelfModel.class))
                .map(model -> {
                    if (model.soul() instanceof AgentSoul agentSoul) {
                        memory.setSoulVersion(agentSoul.soulVersion());
                        return agentSoul;
                    }
                    return null;
                });
    }

    /** Lists all agent souls stored in sharded directories. */
    public List<AgentSoul> listAllAgents() {
        List<String> agentIds = discoverAgentIds();
        if (agentIds.isEmpty()) {
            return loadAgentSoul(null).map(List::of).orElse(List.of());
        }
        return agentIds.stream()
                .map(this::loadAgentSoul)
                .flatMap(Optional::stream)
                .toList();
    }

    /** Saves an agent soul to the INSULA cortex. */
    public void saveAgentSoul(AgentSoul soul) {
        String namespaceId = "agent-" + soul.id();
        SpectorMemory memory = userMemoryRegistry.resolveFor(namespaceId);
        if (memory == null) {
            log.warn("[CognitiveSoul] Memory not resolved for agent namespace: {}", namespaceId);
            return;
        }

        short nextVersion = 1;
        var insula = memory.admin().insularCortex();
        if (insula != null) {
            nextVersion = insula.get()
                .flatMap(bytes -> fromJsonBytes(bytes, InsulaSelfModel.class))
                .map(model -> {
                    if (model.soul() instanceof AgentSoul as) {
                        return (short)(as.soulVersion() + 1);
                    }
                    return (short)1;
                })
                .orElse((short)1);
        }

        AgentSoul savedSoul = AgentSoul.builder()
                .id(soul.id())
                .name(soul.name())
                .description(soul.description())
                .systemPrompt(soul.systemPrompt())
                .purpose(soul.purpose())
                .personality(soul.personality())
                .expertiseDomains(soul.expertiseDomains())
                .coreValues(soul.coreValues())
                .ethicalGuardrails(soul.ethicalGuardrails())
                .emotionalBaseline(soul.emotionalBaseline())
                .communicationStyle(soul.communicationStyle())
                .model(soul.model())
                .tools(soul.tools())
                .expertiseEmbedding(soul.expertiseEmbedding())
                .purposeEmbedding(soul.purposeEmbedding())
                .soulVersion(nextVersion)
                .createdAt(soul.createdAt() != null ? soul.createdAt() : java.time.Instant.now())
                .updatedAt(java.time.Instant.now())
                .build();

        InsulaSelfModel selfModel = new InsulaSelfModel("AGENT", savedSoul, null, Map.of());
        byte[] bytes = toJsonBytes(selfModel);
        if (bytes != null && insula != null) {
            insula.put(bytes);
        }
        memory.setSoulVersion(nextVersion);
        log.info("[CognitiveSoul] Saved agent soul '{}' v{} in INSULA", soul.name(), nextVersion);
    }

    /**
     * Loads the user soul (PersonaContext).
     */
    public Optional<PersonaContext> loadUserSoul() {
        String nsId = currentNamespaceId();
        SpectorMemory memory = userMemoryRegistry.resolveFor(nsId);
        if (memory == null) {
            return Optional.empty();
        }

        var insula = memory.admin().insularCortex();
        Optional<PersonaContext> persona = (insula == null) ? Optional.empty() : insula.get()
                .flatMap(bytes -> fromJsonBytes(bytes, InsulaSelfModel.class))
                .map(model -> {
                    if (model.soul() instanceof UserSoul userSoul) {
                        return userSoul.persona();
                    }
                    return null;
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
     */
    public void saveUserSoul(PersonaContext persona) {
        String nsId = currentNamespaceId();
        SpectorMemory memory = userMemoryRegistry.resolveFor(nsId);
        if (memory == null) {
            log.warn("[CognitiveSoul] Memory not resolved for namespace: {}", nsId);
            return;
        }

        if (persona == null) {
            var insula = memory.admin().insularCortex();
            if (insula != null) {
                insula.clear();
            }
            salienceProvider.updateUserPersona(null);
            log.info("[CognitiveSoul] Cleared user persona context in INSULA for namespace: {}", nsId);
            return;
        }

        short nextVersion = 1;
        java.time.Instant createdAt = java.time.Instant.now();
        var insula = memory.admin().insularCortex();
        if (insula != null) {
            var existingOpt = insula.get()
                .flatMap(bytes -> fromJsonBytes(bytes, InsulaSelfModel.class))
                .map(model -> {
                    if (model.soul() instanceof UserSoul us) {
                        return us;
                    }
                    return null;
                });
            if (existingOpt.isPresent()) {
                UserSoul us = existingOpt.get();
                nextVersion = (short)(us.soulVersion() + 1);
                if (us.createdAt() != null) {
                    createdAt = us.createdAt();
                }
            }
        }

        UserSoul userSoul = new UserSoul(nsId, "User", "User Persona", persona, persona.aboutEmbedding(), nextVersion, createdAt, java.time.Instant.now());
        InsulaSelfModel selfModel = new InsulaSelfModel("USER", userSoul, salienceProvider.effectiveProfile(), Map.of());
        
        byte[] bytes = toJsonBytes(selfModel);
        if (bytes != null && insula != null) {
            insula.put(bytes);
        }
        memory.setSoulVersion(nextVersion);

        // Propagate to salience provider
        salienceProvider.updateUserPersona(persona);
        log.info("[CognitiveSoul] Saved user persona v{} to INSULA — salience profile updated", nextVersion);
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
                .model(updates.containsKey("model") ? (String) updates.get("model") : current.model())
                .expertiseEmbedding(current.expertiseEmbedding())
                .purposeEmbedding(current.purposeEmbedding())
                .soulVersion(current.soulVersion())
                .createdAt(current.createdAt());

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

    private String currentNamespaceId() {
        if (synapseProps.auth() != null && synapseProps.auth().enabled()) {
            String userId = com.spectrayan.spector.synapse.security.SecurityUtils.getUserId();
            if (userId != null && !userId.isBlank() && !"default".equals(userId)) {
                return userId;
            }
        }
        return "default";
    }

    private Path basePath() {
        String path = synapseProps.getMemory().getPersistencePath();
        if (path == null || path.isBlank()) {
            path = synapseProps.dataDir();
        }
        return Path.of(path);
    }

    private List<String> discoverAgentIds() {
        Path namespacesDir = basePath().resolve("namespaces");
        if (!Files.exists(namespacesDir)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.walk(namespacesDir, 3)) {
            return stream
                    .filter(Files::isDirectory)
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .filter(name -> name.startsWith("agent-"))
                    .map(name -> name.substring("agent-".length()))
                    .toList();
        } catch (IOException e) {
            log.warn("Failed to walk namespaces directory: {}", e.getMessage());
            return List.of();
        }
    }

    private byte[] toJsonBytes(Object obj) {
        try {
            return mapper.writeValueAsBytes(obj);
        } catch (Exception e) {
            log.error("[CognitiveSoul] Failed to serialize soul: {}", e.getMessage());
            return null;
        }
    }

    private <T> Optional<T> fromJsonBytes(byte[] bytes, Class<T> clazz) {
        if (bytes == null || bytes.length == 0) {
            return Optional.empty();
        }
        try {
            return Optional.of(mapper.readValue(bytes, clazz));
        } catch (Exception e) {
            log.warn("[CognitiveSoul] Failed to deserialize {}: {}", clazz.getSimpleName(), e.getMessage());
            return Optional.empty();
        }
    }
}
