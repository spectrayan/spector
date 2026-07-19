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
package com.spectrayan.spector.synapse.config.api;

import com.spectrayan.spector.synapse.config.model.ConfigCategory;
import com.spectrayan.spector.synapse.config.model.ScopedConfig;
import com.spectrayan.spector.synapse.config.service.ConfigApplicator;
import com.spectrayan.spector.synapse.config.service.ConfigResolutionService;
import com.spectrayan.spector.synapse.security.SecurityUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

/**
 * REST controller for managing settings configuration overrides in Spector OSS.
 */
@RestController
@RequestMapping("/api/v1/config")
public class ConfigController {

    private static final Logger log = LoggerFactory.getLogger(ConfigController.class);

    private final ConfigResolutionService resolutionService;
    private final ConfigApplicator applicator;

    public ConfigController(ConfigResolutionService resolutionService,
                            ConfigApplicator applicator) {
        this.resolutionService = resolutionService;
        this.applicator = applicator;
    }

    /**
     * Lists all configuration categories.
     */
    @GetMapping("/categories")
    public Map<String, Object> listCategories() {
        var policy = resolutionService.policy();
        var categories = new ArrayList<Map<String, Object>>();

        for (ConfigCategory cat : ConfigCategory.values()) {
            var entry = new LinkedHashMap<String, Object>();
            entry.put("key", cat.key());
            entry.put("displayName", cat.name().replace("_", " "));
            entry.put("tenantOverridable", policy.isTenantOverridable(cat));
            entry.put("userOverridable", policy.isUserOverridable(cat));
            categories.add(entry);
        }

        return Map.of("categories", categories);
    }

    /**
     * Gets the effective configuration for a category.
     */
    @GetMapping("/{category}")
    public Map<String, Object> getEffective(@PathVariable String category) {
        ConfigCategory cat = parseCategory(category);
        String tenantId = SecurityUtils.getTenantId();
        String userId = SecurityUtils.getUserId();

        Map<String, Object> effective = resolutionService.resolve(tenantId, userId, cat);
        return maskApiKeys(effective);
    }

    /**
     * Gets annotated configuration for UI badges.
     */
    @GetMapping("/{category}/annotated")
    public Map<String, ConfigResolutionService.AnnotatedValue> getAnnotated(@PathVariable String category) {
        ConfigCategory cat = parseCategory(category);
        String tenantId = SecurityUtils.getTenantId();
        String userId = SecurityUtils.getUserId();

        Map<String, ConfigResolutionService.AnnotatedValue> annotated =
                resolutionService.resolveAnnotated(tenantId, userId, cat);
        return maskAnnotatedApiKeys(annotated);
    }

    /**
     * Gets raw override configuration at current scope.
     */
    @GetMapping("/{category}/raw")
    public Map<String, Object> getRaw(@PathVariable String category) {
        ConfigCategory cat = parseCategory(category);
        String tenantId = SecurityUtils.getTenantId();
        String userId = SecurityUtils.getUserId();

        Map<String, Object> effective = resolutionService.resolve(tenantId, userId, cat);
        return Map.of("category", cat.key(), "raw", effective);
    }

    /**
     * Saves a configuration override.
     */
    @PutMapping("/{category}")
    public Map<String, Object> saveOverride(@PathVariable String category,
                                            @RequestBody Map<String, Object> request) {
        ConfigCategory cat = parseCategory(category);
        String tenantId = SecurityUtils.getTenantId();
        String userId = SecurityUtils.getUserId();

        String scopeType = (String) request.getOrDefault("scope", "tenant");
        @SuppressWarnings("unchecked")
        Map<String, Object> values = (Map<String, Object>) request.get("values");

        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("Missing 'values' in request body");
        }

        ScopedConfig config;
        if ("user".equals(scopeType)) {
            config = ScopedConfig.user(tenantId, userId, cat, values);
        } else {
            config = ScopedConfig.tenant(tenantId, cat, values, userId);
        }

        resolutionService.saveOverride(config);

        // Apply to running system
        Map<String, Object> effective = resolutionService.resolve(tenantId, userId, cat);
        applicator.apply(tenantId, userId, cat, effective);

        log.info("Config saved: category={}, scope={}, editor={}", cat.key(), config.scope(), userId);

        return Map.of(
                "status", "saved",
                "scope", config.scope(),
                "category", cat.key(),
                "appliedAt", Instant.now().toString()
        );
    }

    /**
     * Deletes a configuration override.
     */
    @DeleteMapping("/{category}")
    public Map<String, Object> deleteOverride(@PathVariable String category,
                                             @RequestParam(defaultValue = "tenant") String scope) {
        ConfigCategory cat = parseCategory(category);
        String tenantId = SecurityUtils.getTenantId();
        String userId = SecurityUtils.getUserId();

        String scopeStr = "user".equals(scope) ? "user:" + tenantId + ":" + userId : "tenant:" + tenantId;
        boolean deleted = resolutionService.removeOverride(scopeStr, cat);

        // Re-apply defaults
        Map<String, Object> effective = resolutionService.resolve(tenantId, userId, cat);
        applicator.apply(tenantId, userId, cat, effective);

        return Map.of(
                "status", deleted ? "deleted" : "not_found",
                "category", cat.key(),
                "scope", scopeStr
        );
    }

    /**
     * Gets the JSON schema fields for a configuration category.
     */
    @GetMapping("/schema/{category}")
    public Map<String, Object> schema(@PathVariable String category) {
        ConfigCategory cat = parseCategory(category);
        List<Map<String, Object>> fields = switch (cat) {
            case LLM_PROVIDER -> List.of(
                    Map.of("key", "provider", "defaultValue", "ollama", "type", "string", "description", "Active LLM provider (ollama, google, etc.)"),
                    Map.of("key", "model", "defaultValue", "llama3.2", "type", "string", "description", "Model name for chat generation"),
                    Map.of("key", "temperature", "defaultValue", 0.7, "type", "number", "description", "Temperature for LLM generation"),
                    Map.of("key", "api-key", "defaultValue", "", "type", "string", "description", "API key (for cloud providers)"),
                    Map.of("key", "base-url", "defaultValue", "http://localhost:11434", "type", "string", "description", "Base URL (for Ollama)")
            );
            case INGESTION -> List.of(
                    Map.of("key", "chunk-size", "defaultValue", 800, "type", "number", "description", "Maximum chunk size in characters"),
                    Map.of("key", "chunk-overlap", "defaultValue", 100, "type", "number", "description", "Overlapping character count between chunks"),
                    Map.of("key", "parent-child-linking", "defaultValue", false, "type", "boolean", "description", "Enable parent-child chunk mapping for documents")
            );
            case RAG -> List.of(); // RAG is no longer active in cognitive memory
            case SALIENCE, SOUL -> List.of();
        };

        return Map.of(
                "category", cat.key(),
                "fields", fields
        );
    }

    /**
     * Gets the current override policy.
     */
    @GetMapping("/policy")
    public Map<String, Object> policy() {
        return Map.of(
                "overrideOrder", List.of("system", "tenant", "user"),
                "categories", ConfigCategory.values().length
        );
    }

    /**
     * Lists all available providers.
     */
    @GetMapping("/providers/available")
    public Map<String, Object> availableProviders() {
        return Map.of("providers", List.of(
                Map.of("name", "ollama", "type", "local", "status", "available"),
                Map.of("name", "google", "type", "cloud", "status", "available")
        ));
    }

    private ConfigCategory parseCategory(String key) {
        try {
            return ConfigCategory.fromKey(key);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown config category: " + key);
        }
    }

    private Map<String, Object> maskApiKeys(Map<String, Object> values) {
        var masked = new LinkedHashMap<>(values);
        masked.computeIfPresent("api-key", (k, v) -> {
            String s = v.toString();
            if (s.length() > 8) {
                return s.substring(0, 3) + "****" + s.substring(s.length() - 4);
            }
            return s.isEmpty() ? "" : "****";
        });
        return masked;
    }

    private Map<String, ConfigResolutionService.AnnotatedValue> maskAnnotatedApiKeys(
            Map<String, ConfigResolutionService.AnnotatedValue> values) {
        var masked = new LinkedHashMap<>(values);
        masked.computeIfPresent("api-key", (k, v) -> {
            String s = v.value().toString();
            String maskedStr;
            if (s.length() > 8) {
                maskedStr = s.substring(0, 3) + "****" + s.substring(s.length() - 4);
            } else {
                maskedStr = s.isEmpty() ? "" : "****";
            }
            return new ConfigResolutionService.AnnotatedValue(maskedStr, v.source());
        });
        return masked;
    }
}
