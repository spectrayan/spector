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
package com.spectrayan.spector.synapse.config.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A scoped configuration entry in the hierarchical override chain.
 *
 * <h3>Scope Format</h3>
 * <ul>
 *   <li>{@code "system"} — global defaults</li>
 *   <li>{@code "tenant:{tenantId}"} — per-tenant overrides</li>
 *   <li>{@code "user:{tenantId}:{userId}"} — per-user overrides</li>
 * </ul>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ScopedConfig(
        String scope,
        ConfigCategory category,
        Map<String, Object> values,
        Instant updatedAt,
        String updatedBy
) {

    public ScopedConfig {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(category, "category must not be null");
        values = values != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(values))
                : Map.of();
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
    }

    /** Creates a system-level config. */
    public static ScopedConfig system(ConfigCategory category, Map<String, Object> values) {
        return new ScopedConfig("system", category, values, Instant.now(), null);
    }

    /** Creates a tenant-level config. */
    public static ScopedConfig tenant(String tenantId, ConfigCategory category,
                                       Map<String, Object> values, String updatedBy) {
        return new ScopedConfig("tenant:" + tenantId, category, values,
                Instant.now(), updatedBy);
    }

    /** Creates a user-level config. */
    public static ScopedConfig user(String tenantId, String userId,
                                     ConfigCategory category, Map<String, Object> values) {
        return new ScopedConfig("user:" + tenantId + ":" + userId, category,
                values, Instant.now(), userId);
    }

    /** Returns the scope level: "system", "tenant", or "user". */
    @JsonIgnore
    public String scopeLevel() {
        if (scope.startsWith("user:")) return "user";
        if (scope.startsWith("tenant:")) return "tenant";
        return "system";
    }

    /** Extracts the tenant ID from a tenant or user scope. Returns null for system scope. */
    @JsonIgnore
    public String tenantId() {
        if (scope.startsWith("tenant:")) return scope.substring("tenant:".length());
        if (scope.startsWith("user:")) {
            String rest = scope.substring("user:".length());
            int colon = rest.indexOf(':');
            return colon > 0 ? rest.substring(0, colon) : rest;
        }
        return null;
    }

    /** Extracts the user ID from a user scope. */
    @JsonIgnore
    public String userId() {
        if (scope.startsWith("user:")) {
            String rest = scope.substring("user:".length());
            int colon = rest.indexOf(':');
            return colon > 0 ? rest.substring(colon + 1) : null;
        }
        return null;
    }

    /** Creates a copy with updated values and timestamp. */
    public ScopedConfig withValues(Map<String, Object> newValues, String editor) {
        return new ScopedConfig(scope, category, newValues, Instant.now(), editor);
    }

    /** Returns a single typed value, or null if absent. */
    @SuppressWarnings("unchecked")
    public <T> T value(String key, Class<T> type) {
        Object val = values.get(key);
        if (val == null) return null;
        if (type.isInstance(val)) return (T) val;
        String str = val.toString();
        if (type == Integer.class) {
            return type.cast(com.spectrayan.spector.commons.ParseUtils.parseInteger(str).orElse(null));
        }
        if (type == Long.class) {
            try {
                return type.cast(Long.parseLong(str.trim()));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        if (type == Double.class) {
            return type.cast(com.spectrayan.spector.commons.ParseUtils.parseDouble(str).orElse(null));
        }
        if (type == Boolean.class) return type.cast(Boolean.parseBoolean(str));
        return type.cast(str);
    }

    public String stringValue(String key, String defaultValue) {
        Object val = values.get(key);
        return val != null ? val.toString() : defaultValue;
    }

    public int intValue(String key, int defaultValue) {
        Object val = values.get(key);
        if (val == null) return defaultValue;
        if (val instanceof Number n) return n.intValue();
        return com.spectrayan.spector.commons.ParseUtils.parseInteger(val.toString()).orElse(defaultValue);
    }

    public double doubleValue(String key, double defaultValue) {
        Object val = values.get(key);
        if (val == null) return defaultValue;
        if (val instanceof Number n) return n.doubleValue();
        return com.spectrayan.spector.commons.ParseUtils.parseDouble(val.toString()).orElse(defaultValue);
    }

    public boolean boolValue(String key, boolean defaultValue) {
        Object val = values.get(key);
        if (val == null) return defaultValue;
        if (val instanceof Boolean b) return b;
        return Boolean.parseBoolean(val.toString());
    }
}
