/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.spectrayan.spector.connector.sink;

/**
 * Per-tenant resource configuration for capacity management.
 *
 * <h3>Design</h3>
 * <p>Defines limits for a single tenant's resource consumption.
 * Defaults are generous — suitable for most tenants. Override
 * per-tenant via the database configuration.</p>
 *
 * <h3>Enforcement Model</h3>
 * <p>By default, exceeding a limit logs a warning (soft enforcement).
 * Set {@code hardEnforce} to true for HTTP 429 rejection.</p>
 *
 * @param maxMemories       maximum number of memory records per tenant (default: 100,000)
 * @param maxSegmentMb      maximum total segment size in MB per tenant (default: 512)
 * @param maxIngestionsPerMin maximum ingestion rate per minute (default: 1000)
 * @param idleEvictionMs    evict inactive tenant segments after this many milliseconds (default: 30 min)
 * @param hardEnforce       if true, exceeding limits returns HTTP 429 (default: false — soft warning)
 */
public record TenantResourceConfig(
        int maxMemories,
        int maxSegmentMb,
        int maxIngestionsPerMin,
        long idleEvictionMs,
        boolean hardEnforce
) {
    /** Default configuration for all tenants. */
    public static TenantResourceConfig defaults() {
        return new TenantResourceConfig(
                100_000,             // 100K memories per tenant
                512,                 // 512 MB per tenant
                1000,                // 1000 ingestions/min
                30 * 60 * 1000L,     // 30 min idle eviction
                false                // soft warning by default
        );
    }

    /** Unlimited configuration (for testing or special tenants). */
    public static TenantResourceConfig unlimited() {
        return new TenantResourceConfig(
                Integer.MAX_VALUE,
                Integer.MAX_VALUE,
                Integer.MAX_VALUE,
                Long.MAX_VALUE,
                false
        );
    }
}
