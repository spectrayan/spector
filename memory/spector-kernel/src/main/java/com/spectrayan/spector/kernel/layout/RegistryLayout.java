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
package com.spectrayan.spector.kernel.layout;

import com.spectrayan.spector.kernel.layout.RegionLayout;

/**
 * A simple RegionLayout implementation for registries.
 */
public final class RegistryLayout implements RegionLayout {
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
