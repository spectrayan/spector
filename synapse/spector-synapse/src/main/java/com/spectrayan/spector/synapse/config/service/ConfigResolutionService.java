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

import com.spectrayan.spector.synapse.config.model.ConfigCategory;
import com.spectrayan.spector.synapse.config.model.ConfigOverridePolicy;
import com.spectrayan.spector.synapse.config.model.ScopedConfig;
import com.spectrayan.spector.synapse.config.repository.ConfigRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Resolves the effective configuration for a given tenant/user by merging
 * the hierarchical override chain: system → tenant → user.
 */
@Service
public class ConfigResolutionService {

    private static final Logger log = LoggerFactory.getLogger(ConfigResolutionService.class);

    private final ConfigRepository repository;
    private volatile ConfigOverridePolicy policy;
    private final com.spectrayan.spector.config.SpectorProperties configSnapshot;

    public ConfigResolutionService(ConfigRepository repository) {
        this.repository = repository;
        this.policy = ConfigOverridePolicy.DEFAULT;
        this.configSnapshot = com.spectrayan.spector.config.SpectorProperties.load();
        log.info("ConfigResolutionService initialized");
    }

    public void setPolicy(ConfigOverridePolicy policy) {
        this.policy = policy != null ? policy : ConfigOverridePolicy.DEFAULT;
    }

    public ConfigOverridePolicy policy() {
        return policy;
    }

    /**
     * Resolves the effective configuration for a category, merging
     * system → tenant → user overrides.
     */
    public Map<String, Object> resolve(String tenantId, String userId,
                                        ConfigCategory category) {
        Map<String, Object> effective = new LinkedHashMap<>(systemDefaults(category));

        // Tenant overrides
        if (tenantId != null && !tenantId.isBlank()
                && policy.isTenantOverridable(category)) {
            repository.get("tenant:" + tenantId, category).ifPresent(tenantConfig -> {
                mergeOverrides(effective, tenantConfig.values());
                log.trace("[Resolve] Applied tenant override for {}: {} keys",
                        category.key(), tenantConfig.values().size());
            });
        }

        // User overrides
        if (userId != null && !userId.isBlank() && tenantId != null
                && policy.isUserOverridable(category)) {
            String userScope = "user:" + tenantId + ":" + userId;
            repository.get(userScope, category).ifPresent(userConfig -> {
                mergeOverrides(effective, userConfig.values());
                log.trace("[Resolve] Applied user override for {}: {} keys",
                        category.key(), userConfig.values().size());
            });
        }

        return effective;
    }

    /**
     * Resolves with source annotations for UI override badges.
     */
    public Map<String, AnnotatedValue> resolveAnnotated(String tenantId, String userId,
                                                         ConfigCategory category) {
        Map<String, Object> systemDefaults = systemDefaults(category);
        Map<String, AnnotatedValue> annotated = new LinkedHashMap<>();
        systemDefaults.forEach((k, v) -> annotated.put(k, new AnnotatedValue(v, "system")));

        if (tenantId != null && !tenantId.isBlank()
                && policy.isTenantOverridable(category)) {
            repository.get("tenant:" + tenantId, category).ifPresent(tenantConfig ->
                    tenantConfig.values().forEach((k, v) -> {
                        if (v != null) annotated.put(k, new AnnotatedValue(v, "tenant"));
                    })
            );
        }

        if (userId != null && !userId.isBlank() && tenantId != null
                && policy.isUserOverridable(category)) {
            repository.get("user:" + tenantId + ":" + userId, category).ifPresent(userConfig ->
                    userConfig.values().forEach((k, v) -> {
                        if (v != null) annotated.put(k, new AnnotatedValue(v, "user"));
                    })
            );
        }

        return annotated;
    }

    /**
     * Saves a scoped config override after validating the override policy.
     */
    public void saveOverride(ScopedConfig config) {
        String scopeLevel = config.scopeLevel();
        if (!policy.isOverridable(scopeLevel, config.category())) {
            throw new IllegalArgumentException(String.format(
                    "Scope '%s' is not allowed to override category '%s' by current policy",
                    scopeLevel, config.category().key()));
        }
        repository.save(config);
    }

    /**
     * Removes a scoped config override.
     */
    public boolean removeOverride(String scope, ConfigCategory category) {
        return repository.delete(scope, category);
    }

    // ── System Defaults (projected from SpectorProperties aggregate snapshot) ──

    private Map<String, Object> systemDefaults(ConfigCategory category) {
        return switch (category) {
            case LLM_PROVIDER -> llmDefaults();
            case INGESTION -> ingestionDefaults();
            case RAG -> ragDefaults();
            case SALIENCE, SOUL -> Map.of();
        };
    }

    private Map<String, Object> llmDefaults() {
        var gen = configSnapshot.provider() != null ? configSnapshot.provider().getGeneration() : null;
        var map = new LinkedHashMap<String, Object>();
        map.put("provider", gen != null && gen.getType() != null ? gen.getType() : "ollama");
        map.put("model", gen != null && gen.getModel() != null ? gen.getModel() : "llama3.2");
        map.put("base-url", gen != null && gen.getBaseUrl() != null ? gen.getBaseUrl() : "http://localhost:11434");
        double temp = 0.7;
        if (gen != null && gen.getProperties() != null && gen.getProperties().containsKey("temperature")) {
            try {
                temp = Double.parseDouble(gen.getProperties().get("temperature"));
            } catch (NumberFormatException ignored) {}
        }
        map.put("temperature", temp);
        map.put("api-key", gen != null && gen.getApiKey() != null ? gen.getApiKey() : "");
        return map;
    }

    private Map<String, Object> ingestionDefaults() {
        var chunk = configSnapshot.memory() != null && configSnapshot.memory().getRemember() != null
                ? configSnapshot.memory().getRemember().getChunk() : null;
        var map = new LinkedHashMap<String, Object>();
        map.put("chunk-size", chunk != null && chunk.getSize() > 0 ? chunk.getSize() : 2500);
        map.put("chunk-overlap", chunk != null && chunk.getOverlap() >= 0 ? chunk.getOverlap() : 200);
        map.put("parent-child-linking", false);
        return map;
    }

    private Map<String, Object> ragDefaults() {
        int topK = com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_QUERY_DEFAULT_TOP_K;
        float similarityThreshold = configSnapshot.memory() != null ? configSnapshot.memory().getGraphExpansionThreshold() : 0.7f;
        var map = new LinkedHashMap<String, Object>();
        map.put("top-k", topK);
        map.put("similarity-threshold", (double) similarityThreshold);
        return map;
    }

    private void mergeOverrides(Map<String, Object> effective, Map<String, Object> overrides) {
        overrides.forEach((key, value) -> { if (value != null) effective.put(key, value); });
    }

    /** Configuration value with source annotation for UI override badges. */
    public record AnnotatedValue(Object value, String source) {}
}
