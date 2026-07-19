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

    public ConfigResolutionService(ConfigRepository repository) {
        this.repository = repository;
        this.policy = ConfigOverridePolicy.DEFAULT;
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

    // ── System Defaults (env vars + hardcoded) ──

    private Map<String, Object> systemDefaults(ConfigCategory category) {
        return switch (category) {
            case LLM_PROVIDER -> llmDefaults();
            case INGESTION -> ingestionDefaults();
            case RAG -> ragDefaults();
            case SALIENCE, SOUL -> Map.of();
        };
    }

    private Map<String, Object> llmDefaults() {
        var map = new LinkedHashMap<String, Object>();
        map.put("provider", "ollama");
        map.put("model", envOr("SPECTOR_OLLAMA_MODEL", "llama3.2"));
        map.put("base-url", envOr("SPECTOR_OLLAMA_BASE_URL", "http://localhost:11434"));
        map.put("temperature", 0.7);
        map.put("api-key", "");
        return map;
    }

    private static Map<String, Object> ingestionDefaults() {
        var map = new LinkedHashMap<String, Object>();
        map.put("chunk-size", 800);
        map.put("chunk-overlap", 100);
        map.put("parent-child-linking", false);
        return map;
    }

    private static Map<String, Object> ragDefaults() {
        var map = new LinkedHashMap<String, Object>();
        map.put("top-k", 5);
        map.put("similarity-threshold", 0.7);
        return map;
    }

    private void mergeOverrides(Map<String, Object> effective, Map<String, Object> overrides) {
        overrides.forEach((key, value) -> { if (value != null) effective.put(key, value); });
    }

    private static String envOr(String key, String defaultValue) {
        String val = System.getenv(key);
        return (val != null && !val.isBlank()) ? val : defaultValue;
    }

    /** Configuration value with source annotation for UI override badges. */
    public record AnnotatedValue(Object value, String source) {}
}
