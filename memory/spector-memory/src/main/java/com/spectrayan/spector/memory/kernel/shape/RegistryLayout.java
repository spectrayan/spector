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
package com.spectrayan.spector.memory.kernel.shape;

import com.spectrayan.spector.memory.kernel.MemoryLayout;

/**
 * A simple MemoryLayout implementation for registries.
 */
public final class RegistryLayout implements MemoryLayout {
    /** Layout ID for registries ('REG\0'). */
    public static final int LAYOUT_ID = 0x52454700; // 'REG\0'
    
    /** Current schema version. */
    public static final int SCHEMA_VERSION = 1;
    
    @Override 
    public int layoutId() { 
        return LAYOUT_ID; 
    }
    
    @Override 
    public int schemaVersion() { 
        return SCHEMA_VERSION; 
    }
    
    @Override 
    public int recordStride() { 
        return 0; // variable-length
    }
    
    @Override 
    public boolean crcEnabled() { 
        return false; 
    }
    
    @Override 
    public String name() { 
        return "RegistryLayout"; 
    }
}
