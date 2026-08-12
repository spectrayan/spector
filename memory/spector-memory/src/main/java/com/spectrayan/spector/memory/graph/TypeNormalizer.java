/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-memory/LICENSE
 *
 * Change Date: May 27, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.memory.graph;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.Optional;

public final class TypeNormalizer {
    private static final Logger log = LoggerFactory.getLogger(TypeNormalizer.class);
    
    private final OntologyConfig config;
    
    public TypeNormalizer(OntologyConfig config) { this.config = config; }
    
    public record NormalizedType(
        String canonical,
        String original,
        boolean wasNormalized,
        boolean isKnown
    ) {}
    
    public NormalizedType normalize(String rawType) {
        String upper = rawType.trim().toUpperCase(Locale.ROOT);
        // 1. Exact canonical match
        if (config.isKnownType(upper)) return new NormalizedType(upper, rawType, false, true);
        // 2. Alias resolution
        Optional<String> resolved = config.resolveType(upper);
        if (resolved.isPresent()) return new NormalizedType(resolved.get(), rawType, true, true);
        // 3. Unknown - handle per strictness
        switch (config.strictness()) {
            case LOG -> log.debug("Unknown entity type: '{}'", rawType);
            case WARN -> log.warn("Unknown entity type: '{}'", rawType);
            case REJECT -> { log.warn("Rejecting unknown entity type: '{}'", rawType); return new NormalizedType("OTHER", rawType, true, false); }
        }
        return new NormalizedType(upper, rawType, false, false);
    }
    
    public String normalizePredicate(String rawPredicate) {
        String upper = rawPredicate.trim().toUpperCase(Locale.ROOT);
        return config.resolvePredicate(upper).orElse(upper);
    }
    
    public boolean areMergeCompatible(String typeA, String typeB) {
        return config.areMergeCompatible(typeA, typeB);
    }
}
