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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
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

    private final ConcurrentLinkedQueue<PendingChange> pendingChanges =
            new ConcurrentLinkedQueue<>();

    public ConfigApplicator(ProviderRegistry providerRegistry, ObjectProvider<SpectorMemory> spectorMemoryProvider) {
        this.providerRegistry = providerRegistry;
        this.spectorMemoryProvider = spectorMemoryProvider;
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
        if (v != null) try { return Integer.parseInt(v.toString()); } catch (NumberFormatException e) {}
        return def;
    }

    private static double doubleVal(Map<String, Object> map, String key, double def) {
        Object v = map.get(key);
        if (v instanceof Number n) return n.doubleValue();
        if (v != null) try { return Double.parseDouble(v.toString()); } catch (NumberFormatException e) {}
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

    private record PendingChange(
            String tenantId, String userId,
            ConfigCategory category, Map<String, Object> values
    ) {}
}
