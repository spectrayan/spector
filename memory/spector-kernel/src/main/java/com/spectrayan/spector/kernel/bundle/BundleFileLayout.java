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

    /**
     * Bundle-format version this binary <strong>writes</strong>.
     *
     * <p>Stamped into two places per file by {@code BundleDirectory.write}: the offset-0
     * {@link com.spectrayan.spector.kernel.region.RegionPreamble} and the {@code BundleSubHeader}.</p>
     *
     * <p>Bump this when the directory layout changes in a way an older reader cannot interpret, and raise
     * {@link #MIN_READABLE_SCHEMA_VERSION} only when support for reading an older version is deliberately
     * dropped.</p>
     */
    public static final int SCHEMA_VERSION = 1;

    /**
     * Oldest bundle-format version this binary can <strong>read</strong>.
     *
     * <p>Together with {@link #SCHEMA_VERSION} this defines the closed range
     * {@code [MIN_READABLE_SCHEMA_VERSION, SCHEMA_VERSION]} that {@code BundleDirectory.read} accepts.
     * Anything outside it is refused rather than interpreted.</p>
     *
     * <h3>Why a range rather than a major/minor comparison</h3>
     * <p>Every on-disk version in the kernel is a flat {@code int} — there is no major/minor split in any
     * binary header, and inventing one would have to either reinterpret bytes already written or claim
     * reserved space. ({@code SchemaVersion}, which does have major/minor/patch, versions the namespace
     * <em>directory</em> layout and is persisted as JSON; it never touches a bundle header.) A declared
     * readable range expresses the same guarantee without changing a single persisted byte.</p>
     */
    public static final int MIN_READABLE_SCHEMA_VERSION = 1;

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
