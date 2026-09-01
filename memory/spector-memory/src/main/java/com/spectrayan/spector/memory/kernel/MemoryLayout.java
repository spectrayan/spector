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
 * Describes the physical schema and characteristics of a memory's records.
 */
public interface MemoryLayout {
    
    /** 
     * Unique stable identifier for this layout type. Used in file headers. 
     * 
     * @return The unique layout ID.
     */
    int layoutId();
    
    /** 
     * Schema version for this layout. Used for migration via Codec. 
     * 
     * @return The schema version.
     */
    int schemaVersion();
    
    /** 
     * Bytes per record (fixed-size layouts) or 0 for variable-length (AppendMemory). 
     * 
     * @return The size of a record in bytes, or 0 if variable-length.
     */
    int recordStride();
    
    /** 
     * Whether CRC32C integrity checking is enabled for this layout. 
     * 
     * @return true if CRC32C is enabled, false otherwise.
     */
    boolean crcEnabled();
    
    /** 
     * Human-readable name for diagnostics. 
     * 
     * @return The diagnostic name of the layout.
     */
    String name();
}
