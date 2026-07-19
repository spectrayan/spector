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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.EnumSet;
import java.util.Set;

/**
 * Policy controlling which {@link ConfigCategory} entries tenants and users
 * are allowed to override.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ConfigOverridePolicy(
        Set<ConfigCategory> tenantOverridable,
        Set<ConfigCategory> userOverridable
) {

    public ConfigOverridePolicy {
        tenantOverridable = tenantOverridable != null
                ? EnumSet.copyOf(tenantOverridable)
                : EnumSet.noneOf(ConfigCategory.class);
        userOverridable = userOverridable != null
                ? EnumSet.copyOf(userOverridable)
                : EnumSet.noneOf(ConfigCategory.class);
    }

    /** Default: tenants override all, users override LLM/ingestion/RAG. */
    public static final ConfigOverridePolicy DEFAULT = new ConfigOverridePolicy(
            EnumSet.allOf(ConfigCategory.class),
            EnumSet.of(ConfigCategory.LLM_PROVIDER,
                    ConfigCategory.INGESTION, ConfigCategory.RAG)
    );

    /** Locked — nobody overrides anything. */
    public static final ConfigOverridePolicy LOCKED = new ConfigOverridePolicy(
            EnumSet.noneOf(ConfigCategory.class),
            EnumSet.noneOf(ConfigCategory.class)
    );

    /** Open — both tenants and users can override everything. */
    public static final ConfigOverridePolicy OPEN = new ConfigOverridePolicy(
            EnumSet.allOf(ConfigCategory.class),
            EnumSet.allOf(ConfigCategory.class)
    );

    public boolean isTenantOverridable(ConfigCategory category) {
        return tenantOverridable.contains(category);
    }

    public boolean isUserOverridable(ConfigCategory category) {
        return userOverridable.contains(category);
    }

    public boolean isOverridable(String scopeLevel, ConfigCategory category) {
        return switch (scopeLevel) {
            case "system" -> true;
            case "tenant" -> isTenantOverridable(category);
            case "user" -> isUserOverridable(category);
            default -> false;
        };
    }
}
