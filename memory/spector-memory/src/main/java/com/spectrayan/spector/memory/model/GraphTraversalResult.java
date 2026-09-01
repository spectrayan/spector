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
package com.spectrayan.spector.memory.model;

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Structured result of a graph traversal.
 */
public record GraphTraversalResult(
    String startEntityName,
    String startEntityType,
    String targetEntityName,
    String targetEntityType,
    int maxHops,
    Set<DiscoveredEntity> discoveredEntities,
    List<RelationalPath> discoveredPaths,
    List<GroundingMemory> groundingMemories,
    long elapsedMs,
    String error
) {
    public GraphTraversalResult {
        discoveredEntities = discoveredEntities != null ? Set.copyOf(discoveredEntities) : Collections.emptySet();
        discoveredPaths = discoveredPaths != null ? List.copyOf(discoveredPaths) : Collections.emptyList();
        groundingMemories = groundingMemories != null ? List.copyOf(groundingMemories) : Collections.emptyList();
    }

    public record DiscoveredEntity(String name, String type, int memoryRefCount) {}

    public record RelationalPath(List<PathNode> nodes, int hopCount) {
        public RelationalPath {
            nodes = nodes != null ? List.copyOf(nodes) : Collections.emptyList();
        }
    }

    public record PathNode(String entityName, String entityType, String relation, String source) {}

    public record GroundingMemory(String id, String memoryType, String textExcerpt) {}

    public static GraphTraversalResult empty(String error) {
        return new GraphTraversalResult(null, null, null, null, 0, null, null, null, 0L, error);
    }

    public static GraphTraversalResult success(
        String startEntityName,
        String startEntityType,
        String targetEntityName,
        String targetEntityType,
        int maxHops,
        Set<DiscoveredEntity> discoveredEntities,
        List<RelationalPath> discoveredPaths,
        List<GroundingMemory> groundingMemories,
        long elapsedMs
    ) {
        return new GraphTraversalResult(
            startEntityName,
            startEntityType,
            targetEntityName,
            targetEntityType,
            maxHops,
            discoveredEntities,
            discoveredPaths,
            groundingMemories,
            elapsedMs,
            null
        );
    }

    public boolean isError() {
        return this.error != null;
    }

    public int pathCount() {
        return this.discoveredPaths.size();
    }

    public int entityCount() {
        return this.discoveredEntities.size();
    }
}
