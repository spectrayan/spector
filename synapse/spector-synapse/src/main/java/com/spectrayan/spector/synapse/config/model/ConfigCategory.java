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

/**
 * Enum defining configuration categories for Spector.
 */
public enum ConfigCategory {
    LLM_PROVIDER,
    INGESTION,
    RAG;

    public String key() {
        return name().toLowerCase();
    }

    public static ConfigCategory fromKey(String key) {
        if (key == null) return null;
        for (ConfigCategory cat : values()) {
            if (cat.key().equalsIgnoreCase(key)) {
                return cat;
            }
        }
        throw new IllegalArgumentException("Unknown config category: " + key);
    }
}
