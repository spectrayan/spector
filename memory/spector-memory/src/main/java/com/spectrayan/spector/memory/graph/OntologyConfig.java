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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Immutable in-memory configuration and taxonomy rules for entity types and relationship predicates.
 *
 * <p>Supports modular configuration split across {@code entity-types.yaml} and
 * {@code relationship-predicates.yaml} or a combined {@code ontology.yaml}.</p>
 *
 * <p>Thread safety: Fully immutable after creation, shared as a lock-free JVM singleton
 * via {@link #defaultInstance()}.</p>
 */
public final class OntologyConfig {

    public enum Strictness { LOG, WARN, REJECT }
    
    public record EntityTypeConfig(String name, List<String> aliases, String parent, List<String> children) {}
    
    public record PredicateConfig(String name, List<String> aliases, String parent, List<String> children, String inverse) {}
    
    private final Strictness strictness;
    private final Map<String, EntityTypeConfig> entityTypes;
    private final Map<String, PredicateConfig> predicates;

    // Fast lookup maps
    private final Map<String, String> entityAliasMap = new HashMap<>();
    private final Map<String, String> predicateAliasMap = new HashMap<>();

    /**
     * Lazy JVM singleton holder for high-concurrency zero-contention access (ADR-0010).
     */
    private static final class DefaultHolder {
        static final OntologyConfig INSTANCE = loadDefault();
    }

    /**
     * Returns the JVM-wide default singleton instance of {@link OntologyConfig}.
     *
     * @return the shared default ontology configuration
     */
    public static OntologyConfig defaultInstance() {
        return DefaultHolder.INSTANCE;
    }

    private OntologyConfig(Strictness strictness, Map<String, EntityTypeConfig> entityTypes, Map<String, PredicateConfig> predicates) {
        this.strictness = strictness;
        this.entityTypes = Map.copyOf(entityTypes);
        this.predicates = Map.copyOf(predicates);

        for (EntityTypeConfig config : entityTypes.values()) {
            entityAliasMap.put(config.name(), config.name());
            for (String alias : config.aliases()) {
                entityAliasMap.put(alias, config.name());
            }
        }
        for (PredicateConfig config : predicates.values()) {
            predicateAliasMap.put(config.name(), config.name());
            for (String alias : config.aliases()) {
                predicateAliasMap.put(alias, config.name());
            }
        }
    }

    /**
     * Loads the default ontology from classpath, preferring modular {@code entity-types.yaml}
     * and {@code relationship-predicates.yaml}, with fallback to {@code ontology.yaml}.
     */
    public static OntologyConfig loadDefault() {
        InputStream entitiesStream = OntologyConfig.class.getResourceAsStream("/entity-types.yaml");
        InputStream predicatesStream = OntologyConfig.class.getResourceAsStream("/relationship-predicates.yaml");

        if (entitiesStream != null || predicatesStream != null) {
            try {
                return load(entitiesStream, predicatesStream);
            } finally {
                closeQuietly(entitiesStream);
                closeQuietly(predicatesStream);
            }
        }

        try (InputStream in = OntologyConfig.class.getResourceAsStream("/ontology.yaml")) {
            if (in == null) throw new IllegalStateException("Neither modular ontology YAMLs nor ontology.yaml found on classpath");
            return load(in);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load ontology.yaml", e);
        }
    }

    /**
     * Loads ontology configuration from two modular input streams (entities and relationship predicates).
     *
     * @param entityTypesStream input stream for entity types YAML (optional if predicatesStream provided)
     * @param predicatesStream  input stream for relationship predicates YAML (optional if entityTypesStream provided)
     * @return the merged immutable OntologyConfig
     */
    public static OntologyConfig load(InputStream entityTypesStream, InputStream predicatesStream) {
        Strictness strictness = Strictness.LOG;
        Map<String, EntityTypeConfig> entityTypes = new HashMap<>();
        Map<String, PredicateConfig> predicates = new HashMap<>();

        if (entityTypesStream != null) {
            OntologyConfig parsedEntities = load(entityTypesStream);
            strictness = parsedEntities.strictness();
            entityTypes.putAll(parsedEntities.entityTypes);
            predicates.putAll(parsedEntities.predicates);
        }

        if (predicatesStream != null) {
            OntologyConfig parsedPredicates = load(predicatesStream);
            if (entityTypesStream == null) strictness = parsedPredicates.strictness();
            entityTypes.putAll(parsedPredicates.entityTypes);
            predicates.putAll(parsedPredicates.predicates);
        }

        return new OntologyConfig(strictness, entityTypes, predicates);
    }

    public static OntologyConfig load(InputStream in) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            Strictness strictness = Strictness.LOG;
            Map<String, EntityTypeConfig> entityTypes = new HashMap<>();
            Map<String, PredicateConfig> predicates = new HashMap<>();

            String currentSection = null;
            String currentItem = null;
            
            // Temporary builders
            Map<String, Map<String, Object>> entityBuilders = new HashMap<>();
            Map<String, Map<String, Object>> predicateBuilders = new HashMap<>();

            while ((line = reader.readLine()) != null) {
                int commentIdx = line.indexOf('#');
                if (commentIdx != -1) line = line.substring(0, commentIdx);
                if (line.trim().isEmpty()) continue;

                int indent = 0;
                while (indent < line.length() && line.charAt(indent) == ' ') indent++;
                
                String trimmed = line.trim();
                
                if (indent == 0) {
                    if (trimmed.startsWith("strictness:")) {
                        strictness = Strictness.valueOf(trimmed.substring(11).trim());
                    } else if (trimmed.startsWith("entity_types:")) {
                        currentSection = "entity_types";
                        currentItem = null;
                    } else if (trimmed.startsWith("relationship_predicates:")) {
                        currentSection = "relationship_predicates";
                        currentItem = null;
                    }
                } else if (indent == 2) {
                    if (trimmed.endsWith(":")) {
                        currentItem = trimmed.substring(0, trimmed.length() - 1);
                        if ("entity_types".equals(currentSection)) {
                            entityBuilders.put(currentItem, new HashMap<>());
                        } else if ("relationship_predicates".equals(currentSection)) {
                            predicateBuilders.put(currentItem, new HashMap<>());
                        }
                    }
                } else if (indent == 4 && currentItem != null) {
                    Map<String, Object> builder = "entity_types".equals(currentSection) ? entityBuilders.get(currentItem) : predicateBuilders.get(currentItem);
                    if (builder != null) {
                        int colonIdx = trimmed.indexOf(':');
                        if (colonIdx != -1) {
                            String key = trimmed.substring(0, colonIdx).trim();
                            String val = trimmed.substring(colonIdx + 1).trim();
                            if (val.startsWith("[") && val.endsWith("]")) {
                                String listInner = val.substring(1, val.length() - 1).trim();
                                List<String> list = new ArrayList<>();
                                if (!listInner.isEmpty()) {
                                    for (String item : listInner.split(",")) {
                                        list.add(item.trim());
                                    }
                                }
                                builder.put(key, list);
                            } else {
                                builder.put(key, val);
                            }
                        }
                    }
                }
            }

            for (Map.Entry<String, Map<String, Object>> e : entityBuilders.entrySet()) {
                String name = e.getKey();
                Map<String, Object> props = e.getValue();
                @SuppressWarnings("unchecked")
                List<String> aliases = (List<String>) props.getOrDefault("aliases", List.of());
                String parent = (String) props.get("parent");
                @SuppressWarnings("unchecked")
                List<String> children = (List<String>) props.getOrDefault("children", List.of());
                entityTypes.put(name, new EntityTypeConfig(name, aliases, parent, children));
            }

            for (Map.Entry<String, Map<String, Object>> e : predicateBuilders.entrySet()) {
                String name = e.getKey();
                Map<String, Object> props = e.getValue();
                @SuppressWarnings("unchecked")
                List<String> aliases = (List<String>) props.getOrDefault("aliases", List.of());
                String parent = (String) props.get("parent");
                String inverse = (String) props.get("inverse");
                @SuppressWarnings("unchecked")
                List<String> children = (List<String>) props.getOrDefault("children", List.of());
                predicates.put(name, new PredicateConfig(name, aliases, parent, children, inverse));
            }

            return new OntologyConfig(strictness, entityTypes, predicates);
        } catch (IOException e) {
            throw new RuntimeException("Error reading ontology config", e);
        }
    }

    private static void closeQuietly(InputStream stream) {
        if (stream != null) {
            try {
                stream.close();
            } catch (IOException ignored) {}
        }
    }

    public Strictness strictness() { return strictness; }

    public Set<String> canonicalTypes() { return entityTypes.keySet(); }

    public Optional<String> resolveType(String rawType) {
        return Optional.ofNullable(entityAliasMap.get(rawType));
    }

    public boolean isKnownType(String type) {
        return entityTypes.containsKey(type);
    }

    public Optional<String> parentType(String type) {
        EntityTypeConfig cfg = entityTypes.get(type);
        return cfg != null ? Optional.ofNullable(cfg.parent()) : Optional.empty();
    }

    public boolean areMergeCompatible(String typeA, String typeB) {
        if (typeA.equals(typeB)) return true;
        // Check if one is ancestor of another
        return isAncestor(typeA, typeB) || isAncestor(typeB, typeA);
    }

    private boolean isAncestor(String ancestor, String descendant) {
        String current = descendant;
        while (current != null) {
            if (current.equals(ancestor)) return true;
            Optional<String> parent = parentType(current);
            current = parent.orElse(null);
        }
        return false;
    }

    public Set<String> canonicalPredicates() { return predicates.keySet(); }

    public Optional<String> resolvePredicate(String rawPredicate) {
        return Optional.ofNullable(predicateAliasMap.get(rawPredicate));
    }

    public boolean isKnownPredicate(String predicate) {
        return predicates.containsKey(predicate);
    }

    public Optional<String> inversePredicate(String canonical) {
        PredicateConfig cfg = predicates.get(canonical);
        return cfg != null ? Optional.ofNullable(cfg.inverse()) : Optional.empty();
    }

    public boolean arePredicateCompatible(String predA, String predB) {
        if (predA.equals(predB)) return true;
        String curA = predA;
        while (curA != null) {
            if (curA.equals(predB)) return true;
            PredicateConfig cfg = predicates.get(curA);
            curA = cfg != null ? cfg.parent() : null;
        }
        String curB = predB;
        while (curB != null) {
            if (curB.equals(predA)) return true;
            PredicateConfig cfg = predicates.get(curB);
            curB = cfg != null ? cfg.parent() : null;
        }
        return false;
    }
}
