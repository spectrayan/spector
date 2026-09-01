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

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

/**
 * Orchestrates region growth and capacity monitoring for a {@link RuntimeBundle}.
 *
 * <p>When a runtime store reaches full capacity, it delegates to
 * {@code BundleManager.growRegion(regionId)} which in turn calls
 * {@link RuntimeBundle#growRegion(RegionId)}. This class adds:
 * <ul>
 *   <li><b>Usage tracking</b> — {@link #regionUsage(RegionId)} to monitor fill ratio</li>
 *   <li><b>Growth threshold</b> — configurable fill ratio that triggers proactive growth</li>
 *   <li><b>Compaction</b> — future support for reclaiming dead space</li>
 * </ul>
 *
 * @since 1.2.0
 * @see RuntimeBundle
 */
public final class BundleManager {

    private static final Logger log = LoggerFactory.getLogger(BundleManager.class);

    /** Default fill ratio threshold (80%) above which proactive growth is triggered. */
    private static final float DEFAULT_GROWTH_THRESHOLD = 0.80f;

    private final RuntimeBundle bundle;
    private final float growthThreshold;

    /**
     * Creates a BundleManager with default growth threshold (80%).
     *
     * @param bundle the runtime bundle to manage
     */
    public BundleManager(RuntimeBundle bundle) {
        this(bundle, DEFAULT_GROWTH_THRESHOLD);
    }

    /**
     * Creates a BundleManager with a custom growth threshold.
     *
     * @param bundle          the runtime bundle to manage
     * @param growthThreshold fill ratio (0.0–1.0) above which proactive growth triggers
     */
    public BundleManager(RuntimeBundle bundle, float growthThreshold) {
        this.bundle = bundle;
        this.growthThreshold = growthThreshold;
    }

    /**
     * Grows the specified region by relocating it to the tail with doubled capacity.
     *
     * <p>Delegates to {@link RuntimeBundle#growRegion(RegionId)} which handles
     * the StampedLock, unmap, file extend, remap cycle.</p>
     *
     * @param regionId the region to grow
     */
    public void growRegion(RegionId regionId) {
        log.info("BundleManager: growing region {} (usage={}%)",
                regionId, String.format(Locale.ROOT, "%.1f", regionUsage(regionId) * 100));
        bundle.growRegion(regionId);
    }

    /**
     * Returns the usage ratio (usedSize / allocatedSize) for a region.
     *
     * @param regionId the region to check
     * @return a value between 0.0 and 1.0, or 0.0 if the region doesn't exist
     */
    public float regionUsage(RegionId regionId) {
        return bundle.regionUsage(regionId);
    }

    /**
     * Checks if the specified region needs proactive growth based on the threshold.
     *
     * @param regionId the region to check
     * @return true if the region's fill ratio exceeds the growth threshold
     */
    public boolean needsGrowth(RegionId regionId) {
        return regionUsage(regionId) >= growthThreshold;
    }

    /**
     * Grows the region if it exceeds the growth threshold.
     *
     * @param regionId the region to check and potentially grow
     * @return true if the region was grown
     */
    public boolean growIfNeeded(RegionId regionId) {
        if (needsGrowth(regionId)) {
            growRegion(regionId);
            return true;
        }
        return false;
    }

    /**
     * Reclaims dead space from previously relocated regions by defragmenting
     * the runtime bundle and truncating the bundle file on disk.
     *
     * @return number of bytes reclaimed
     */
    public long compact() {
        log.info("BundleManager: compacting runtime bundle");
        return bundle.compact();
    }

    /**
     * Compacts the bundle if its fragmentation ratio exceeds the specified threshold.
     *
     * @param fragmentationThreshold fragmentation ratio (0.0–1.0) above which compaction triggers
     * @return true if compaction was performed
     */
    public boolean compactIfNeeded(float fragmentationThreshold) {
        if (fragmentationRatio() >= fragmentationThreshold) {
            compact();
            return true;
        }
        return false;
    }

    /**
     * Returns the fragmentation ratio (dead space / total file size) of the bundle.
     *
     * @return a value between 0.0 and 1.0
     */
    public float fragmentationRatio() {
        return bundle.fragmentationRatio();
    }

    /**
     * Returns the number of dead or uncompacted bytes in the bundle.
     *
     * @return dead space in bytes
     */
    public long deadSpaceBytes() {
        return bundle.deadSpaceBytes();
    }

    /**
     * Returns the managed runtime bundle.
     */
    public RuntimeBundle bundle() {
        return bundle;
    }
}
