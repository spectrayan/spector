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

import java.lang.foreign.MemorySegment;

/**
 * Handles schema migration and encoding/decoding operations for a specific MemoryLayout.
 *
 * @param <L> The MemoryLayout type this codec manages.
 */
public interface Codec<L extends MemoryLayout> {
    
    /** 
     * The layout this codec handles. 
     * 
     * @return The associated memory layout.
     */
    L layout();
    
    /** 
     * Whether this codec can upgrade from the given schema version. 
     * 
     * @param fromVersion The schema version to upgrade from.
     * @return true if the upgrade is supported, false otherwise.
     */
    boolean canUpgrade(int fromVersion);
    
    /** 
     * Upgrade data from one schema version to the current. 
     * 
     * @param fromVersion The original schema version.
     * @param source The source segment containing the old data.
     * @param target The target segment to write the upgraded data into.
     */
    void upgrade(int fromVersion, MemorySegment source, MemorySegment target);
}
