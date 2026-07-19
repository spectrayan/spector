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
package com.spectrayan.spector.synapse.config.service;

import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.provider.ProviderConfig;
import com.spectrayan.spector.provider.ProviderFactory;
import com.spectrayan.spector.provider.ProviderRegistry;
import com.spectrayan.spector.synapse.config.model.ConfigCategory;
import com.spectrayan.spector.synapse.config.SynapseSalienceProvider;
import com.spectrayan.spector.memory.neurodivergent.IcnuWeights;
import com.spectrayan.spector.memory.model.PersonaContext;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Applies resolved configuration to running subsystems at runtime.
 */
@Service
public class ConfigApplicator {

    private static final Logger log = LoggerFactory.getLogger(ConfigApplicator.class);

    private final ProviderRegistry providerRegistry;
    private final ObjectProvider<SpectorMemory> spectorMemoryProvider;
    private final ObjectProvider<SynapseSalienceProvider> salienceProvider;
    private final ObjectProvider<ObjectMapper> objectMapperProvider;

    private final ConcurrentLinkedQueue<PendingChange> pendingChanges =
            new ConcurrentLinkedQueue<>();

    public ConfigApplicator(ProviderRegistry providerRegistry,
                            ObjectProvider<SpectorMemory> spectorMemoryProvider,
                            ObjectProvider<SynapseSalienceProvider> salienceProvider,
                            ObjectProvider<ObjectMapper> objectMapperProvider) {
        this.providerRegistry = providerRegistry;
        this.spectorMemoryProvider = spectorMemoryProvider;
        this.salienceProvider = salienceProvider;
        this.objectMapperProvider = objectMapperProvider;
        log.info("ConfigApplicator initialized");
    }

    /**
     * Queues a config change for application.
     */
    public void apply(String tenantId, String userId,
                      ConfigCategory category, Map<String, Object> values) {
        pendingChanges.add(new PendingChange(tenantId, userId, category, values));
        log.info("[ConfigApplicator] Queued {} change for tenant={}, user={}",
                category.key(), tenantId, userId);
        drainPending();
    }

    public void drainPending() {
        PendingChange change;
        while ((change = pendingChanges.poll()) != null) {
            try {
                applyChange(change);
            } catch (Exception e) {
                log.error("[ConfigApplicator] Failed to apply {} change for tenant={}: {}",
                        change.category().key(), change.tenantId(), e.getMessage(), e);
            }
        }
    }

    public int pendingCount() {
        return pendingChanges.size();
    }

    private void applyChange(PendingChange change) {
        switch (change.category()) {
            case LLM_PROVIDER -> applyLlmProvider(change);
            case INGESTION -> applyIngestion(change);
            case RAG -> log.info("[ConfigApplicator] RAG config updated: top-k={}, threshold={}",
                    intVal(change.values(), "top-k", 5),
                    doubleVal(change.values(), "similarity-threshold", 0.7));
            case SALIENCE -> applySalience(change);
            case SOUL -> applySoul(change);
        }
    }

    private void applyLlmProvider(PendingChange change) {
        String providerName = stringVal(change.values(), "provider", null);
        if (providerName == null) return;

        if (providerRegistry.generationProviderNames().contains(providerName)) {
            providerRegistry.activateGeneration(providerName);
            log.info("[ConfigApplicator] Activated LLM provider: {}", providerName);
            return;
        }

        String model = stringVal(change.values(), "model", "default");
        String apiKey = stringVal(change.values(), "api-key", "");
        String baseUrl = stringVal(change.values(), "base-url", null);

        for (ProviderFactory factory : ServiceLoader.load(ProviderFactory.class)) {
            if (factory.name().equalsIgnoreCase(providerName) && factory.supportsGeneration()) {
                var config = new ProviderConfig(providerName, "generation", model,
                        apiKey, baseUrl, 0, extractProperties(change.values()));
                factory.createGenerationProvider(config).ifPresent(provider -> {
                    providerRegistry.registerGeneration(providerName, provider);
                    providerRegistry.activateGeneration(providerName);
                    log.info("[ConfigApplicator] Created and activated LLM provider: {} (model={})",
                            providerName, model);
                });
                return;
            }
        }
        log.warn("[ConfigApplicator] No factory found for LLM provider: {}", providerName);
    }

    private void applyIngestion(PendingChange change) {
        int chunkSize = intVal(change.values(), "chunk-size", 800);
        int overlap = intVal(change.values(), "chunk-overlap", 100);
        boolean parentChild = boolVal(change.values(), "parent-child-linking", false);

        SpectorMemory spectorMemory = spectorMemoryProvider.getIfAvailable();
        if (spectorMemory == null) {
            log.warn("[ConfigApplicator] SpectorMemory is not available, skipping ingestion configuration update");
            return;
        }

        var chunkConfig = new com.spectrayan.spector.commons.chunker.ChunkConfig(
                chunkSize, overlap, "text/markdown", null, true, true, false, parentChild);

        spectorMemory.updateChunkConfig(chunkConfig);
        log.info("[ConfigApplicator] Ingestion config updated on SpectorMemory: chunk-size={}, overlap={}, parent-child={}",
                chunkSize, overlap, parentChild);
    }

    private static String stringVal(Map<String, Object> map, String key, String def) {
        Object v = map.get(key);
        return v != null ? v.toString() : def;
    }

    private static int intVal(Map<String, Object> map, String key, int def) {
        Object v = map.get(key);
        if (v instanceof Number n) return n.intValue();
        if (v != null) {
            return com.spectrayan.spector.commons.ParseUtils.parseInteger(v.toString()).orElse(def);
        }
        return def;
    }

    private static double doubleVal(Map<String, Object> map, String key, double def) {
        Object v = map.get(key);
        if (v instanceof Number n) return n.doubleValue();
        if (v != null) {
            return com.spectrayan.spector.commons.ParseUtils.parseDouble(v.toString()).orElse(def);
        }
        return def;
    }

    private static boolean boolVal(Map<String, Object> map, String key, boolean def) {
        Object v = map.get(key);
        if (v instanceof Boolean b) return b;
        if (v != null) return Boolean.parseBoolean(v.toString());
        return def;
    }

    private static Map<String, String> extractProperties(Map<String, Object> values) {
        var props = new LinkedHashMap<String, String>();
        values.forEach((k, v) -> {
            if (v != null && !k.equals("provider") && !k.equals("model")
                    && !k.equals("api-key") && !k.equals("base-url")
                    && !k.equals("dimensions")) {
                props.put(k, v.toString());
            }
        });
        return props;
    }

    @SuppressWarnings("unchecked")
    private void applySalience(PendingChange change) {
        SynapseSalienceProvider provider = salienceProvider.getIfAvailable();
        if (provider == null) {
            log.warn("[ConfigApplicator] SynapseSalienceProvider not available, skipping salience application");
            return;
        }

        Map<String, Object> values = change.values();
        if (values == null || values.isEmpty()) return;

        // 1. Interests & Disinterests
        List<SynapseSalienceProvider.InterestEntry> interestEntries = parseInterests((List<Map<String, Object>>) values.get("interestsList"));
        List<SynapseSalienceProvider.InterestEntry> disinterestEntries = parseInterests((List<Map<String, Object>>) values.get("disinterestsList"));
        provider.updateInterests(interestEntries, disinterestEntries);

        // 2. Weights, alpha, beta
        Map<String, Object> icnuMap = (Map<String, Object>) values.get("icnuWeights");
        IcnuWeights icnu = null;
        if (icnuMap != null) {
            float interest = floatVal(icnuMap, "interest", 0.25f);
            float challenge = floatVal(icnuMap, "challenge", 0.25f);
            float novelty = floatVal(icnuMap, "novelty", 0.25f);
            float urgency = floatVal(icnuMap, "urgency", 0.25f);
            icnu = new IcnuWeights(interest, challenge, novelty, urgency);
        }
        Float alpha = values.get("alpha") != null ? ((Number) values.get("alpha")).floatValue() : null;
        Float beta = values.get("beta") != null ? ((Number) values.get("beta")).floatValue() : null;

        provider.updateScoringWeights(icnu, alpha, beta);
        log.info("[ConfigApplicator] Applied Salience config: alpha={}, beta={}", alpha, beta);
    }

    private void applySoul(PendingChange change) {
        SynapseSalienceProvider provider = salienceProvider.getIfAvailable();
        ObjectMapper mapper = objectMapperProvider.getIfAvailable();
        if (provider == null || mapper == null) {
            log.warn("[ConfigApplicator] SynapseSalienceProvider or ObjectMapper not available, skipping user soul application");
            return;
        }

        Map<String, Object> values = change.values();
        if (values == null || values.isEmpty()) return;

        // User soul is resolved when change.userId() represents a user (e.g., "default")
        if (change.userId() != null && !change.userId().isBlank()) {
            try {
                PersonaContext persona = mapper.convertValue(values, PersonaContext.class);
                provider.updateUserPersona(persona);
                log.info("[ConfigApplicator] Applied User Soul (PersonaContext) to salience provider");
            } catch (Exception e) {
                log.warn("[ConfigApplicator] Failed to parse User Soul (PersonaContext): {}", e.getMessage());
            }
        }
    }

    private List<SynapseSalienceProvider.InterestEntry> parseInterests(List<Map<String, Object>> list) {
        if (list == null) return List.of();
        List<SynapseSalienceProvider.InterestEntry> result = new java.util.ArrayList<>();
        for (var map : list) {
            String topic = (String) map.get("topic");
            String lvlStr = (String) map.get("level");
            if (topic != null && lvlStr != null) {
                try {
                    com.spectrayan.spector.memory.model.InterestLevel lvl = 
                            com.spectrayan.spector.memory.model.InterestLevel.valueOf(lvlStr.toUpperCase());
                    result.add(new SynapseSalienceProvider.InterestEntry(topic, lvl));
                } catch (Exception ignored) {}
            }
        }
        return result;
    }

    private static float floatVal(Map<String, Object> map, String key, float def) {
        Object v = map.get(key);
        if (v instanceof Number n) return n.floatValue();
        if (v != null) {
            return com.spectrayan.spector.commons.ParseUtils.parseDouble(v.toString())
                    .map(Double::floatValue)
                    .orElse(def);
        }
        return def;
    }

    private record PendingChange(
            String tenantId, String userId,
            ConfigCategory category, Map<String, Object> values
    ) {}
}
