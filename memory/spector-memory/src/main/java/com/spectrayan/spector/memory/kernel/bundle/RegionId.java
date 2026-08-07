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
package com.spectrayan.spector.memory.kernel.bundle;

/**
 * Stable numeric IDs for Bundle regions.
 */
public enum RegionId {
    // Partition bundle regions
    SEMANTIC(0), 
    EPISODIC(1), 
    PROCEDURAL(2), 
    TEXT(3),
    
    // Runtime bundle regions
    WORKING(10), 
    COACTIVATION(11), 
    INDEX_MIDX(12), 
    INDEX_IDPL(13),
    HEBBIAN(14), 
    TEMPORAL_CHAIN(15), 
    TEMPORAL_FACTS(16),
    ENTITY_DIRECTORY(17), 
    ENTITY_NAMES(18), 
    HYPERGRAPH(19),
    ENTITY_TYPES(20), 
    RELATION_TYPES(21), 
    BM25(22), 
    CHECKPOINT(23),
    INSULA(24);

    private final int id;
    
    private static final RegionId[] LOOKUP = new RegionId[25];
    static {
        for (RegionId region : values()) {
            LOOKUP[region.id()] = region;
        }
    }

    RegionId(int id) { 
        this.id = id; 
    }
    
    /**
     * @return the stable numeric ID of the region
     */
    public int id() { 
        return id; 
    }
    
    /**
     * @return true if the region belongs to partition bundles
     */
    public boolean isPartitionRegion() { 
        return id < 10; 
    }
    
    /**
     * @return true if the region belongs to runtime bundles
     */
    public boolean isRuntimeRegion() { 
        return id >= 10; 
    }
    
    /**
     * Returns the RegionId matching the given ID.
     * 
     * @param id The region ID
     * @return The corresponding RegionId
     * @throws IllegalArgumentException if the ID is unknown
     */
    public static RegionId fromId(int id) {
        if (id >= 0 && id < LOOKUP.length) {
            RegionId region = LOOKUP[id];
            if (region != null) {
                return region;
            }
        }
        throw new IllegalArgumentException("Unknown RegionId: " + id);
    }
}
