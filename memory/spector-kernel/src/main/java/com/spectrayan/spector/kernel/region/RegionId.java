/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.spectrayan.spector.kernel.region;

/**
 * Stable numeric IDs for Bundle regions.
 */
public enum RegionId {
    // Partition bundle regions
    SEMANTIC(0), 
    EPISODIC(1), 
    PROCEDURAL(2), 
    TEXT(3),
    STRENGTH(4),
    
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
    INSULA(24),
    CONTINUITY(25),
    PROVENANCE(26);

    private final int id;
    
    private static final RegionId[] LOOKUP = new RegionId[27];
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
