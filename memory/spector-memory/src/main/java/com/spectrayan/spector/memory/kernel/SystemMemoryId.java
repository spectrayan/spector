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
package com.spectrayan.spector.memory.kernel;

/**
 * Centralized, strongly-typed registry of all system cognitive memory identities.
 * Replaces hardcoded string instances of MemoryId.of.
 */
public enum SystemMemoryId {
    HEBBIAN_CSR("graph", "hebbian-csr"),
    TEMPORAL_CHAIN("temporal", "chain"),
    COACTIVATION("hebbian", "coactivation"),
    ENTITY_DIRECTORY("graph", "entity-directory"),
    ENTITY("graph", "entity"),
    CORTEX_TEXT("cortex", "text"),
    TEMPORAL_FACTS("temporal", "facts"),
    WORKING("tier", "working"),
    SEMANTIC("tier", "semantic"),
    PROCEDURAL("tier", "procedural"),
    EPISODIC("tier", "episodic"),
    HYPERGRAPH("spector", "hyper-entity-graph"),
    ENTITY_TYPE("graph", "entity-type"),
    RELATION_TYPE("graph", "relation-type"),
    INDEX("kernel", "index"),
    INDEX_IDPOOL("index", "idpool"),
    INDEX_SLOT("index", "slot");

    private final String namespace;
    private final String memoryName;
    private final MemoryId memoryId;

    SystemMemoryId(String namespace, String memoryName) {
        this.namespace = namespace;
        this.memoryName = memoryName;
        @SuppressWarnings("deprecation")
        MemoryId id = MemoryId.of(namespace, memoryName);
        this.memoryId = id;
    }

    public MemoryId id() {
        return memoryId;
    }

    public MemoryId id(int partitionSeq) {
        return new MemoryId(namespace, memoryName, partitionSeq);
    }
}
