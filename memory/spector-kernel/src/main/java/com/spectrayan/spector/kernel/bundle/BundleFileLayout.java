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
package com.spectrayan.spector.kernel.bundle;

import com.spectrayan.spector.kernel.layout.RegionLayout;

/**
 * Memory layout for the V4 Bundle infrastructure.
 */
public final class BundleFileLayout implements RegionLayout {
    
    public static final BundleFileLayout SINGLETON = new BundleFileLayout();
    
    public static final int LAYOUT_ID = 0x42554E44; // 'BUND'
    public static final int SCHEMA_VERSION = 1;
    public static final int REGION_ENTRY_STRIDE = 64;
    
    private BundleFileLayout() {
        // Singleton
    }
    
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
        return REGION_ENTRY_STRIDE;
    }
    
    @Override
    public boolean crcEnabled() {
        return true;
    }
    
    @Override
    public String name() {
        return "Bundle";
    }
}
