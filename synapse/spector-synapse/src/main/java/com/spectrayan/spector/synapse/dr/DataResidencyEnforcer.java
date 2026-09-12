/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-synapse/LICENSE
 *
 * Change Date: July 6, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.synapse.dr;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.Objects;

/**
 * Enforces geographic and jurisdictional boundaries for tenant data export and standby recovery
 * based on org-to-cell pin policies (ADR-0034 §16, Req R9.1–R9.5, V6).
 *
 * <p>Invariant V6: A tenant's data does not leave its pinned region. Violations strictly
 * refuse operations rather than proceeding with warnings.</p>
 */
public final class DataResidencyEnforcer {

    private static final Logger log = LoggerFactory.getLogger(DataResidencyEnforcer.class);

    private DataResidencyEnforcer() {}

    /**
     * Validates that tenant geographic residency admits export to the target bucket region.
     *
     * @param tenantJurisdiction pinned jurisdiction/region for the tenant (nullable/empty if unpinned)
     * @param targetBucketRegion region where the DR object-store bucket is located
     * @throws ResidencyViolationException if jurisdiction bounds are violated
     */
    public static void validateExportResidency(String tenantJurisdiction, String targetBucketRegion) {
        if (tenantJurisdiction == null || tenantJurisdiction.isBlank()) {
            return; // No geographic pin declared
        }
        Objects.requireNonNull(targetBucketRegion, "targetBucketRegion must not be null");

        if (!isCompatible(tenantJurisdiction, targetBucketRegion)) {
            String msg = String.format(
                    "Data residency refusal: Tenant pinned to jurisdiction '%s' cannot export to target region '%s' (Req R9.3, V6).",
                    tenantJurisdiction, targetBucketRegion
            );
            log.error("[DataResidency] {}", msg);
            throw new ResidencyViolationException(tenantJurisdiction, targetBucketRegion, msg);
        }
    }

    /**
     * Validates that tenant geographic residency admits restore into the standby cell.
     *
     * @param tenantJurisdiction pinned jurisdiction/region for the tenant
     * @param standbyCellRegion region where the standby recovery cell is running
     * @throws ResidencyViolationException if jurisdiction bounds are violated
     */
    public static void validateRestoreResidency(String tenantJurisdiction, String standbyCellRegion) {
        if (tenantJurisdiction == null || tenantJurisdiction.isBlank()) {
            return; // No geographic pin declared
        }
        Objects.requireNonNull(standbyCellRegion, "standbyCellRegion must not be null");

        if (!isCompatible(tenantJurisdiction, standbyCellRegion)) {
            String msg = String.format(
                    "Data residency refusal: Tenant pinned to jurisdiction '%s' cannot be restored into standby cell in region '%s' (Req R9.4, V6).",
                    tenantJurisdiction, standbyCellRegion
            );
            log.error("[DataResidency] {}", msg);
            throw new ResidencyViolationException(tenantJurisdiction, standbyCellRegion, msg);
        }
    }

    /**
     * Matches tenant jurisdiction against target region.
     * Supports exact matches ("us-east-1" == "us-east-1") and prefix/geo matches ("eu" matches "eu-central-1", "eu-west-1").
     */
    public static boolean isCompatible(String tenantJurisdiction, String region) {
        if (tenantJurisdiction == null || tenantJurisdiction.isBlank()) {
            return true;
        }
        if (region == null || region.isBlank()) {
            return false;
        }

        String normPin = tenantJurisdiction.trim().toLowerCase(Locale.ROOT);
        String normRegion = region.trim().toLowerCase(Locale.ROOT);

        if (normPin.equals(normRegion)) {
            return true;
        }

        // Broad geographic matching, e.g. "eu" prefix matches "eu-west-1", "us" matches "us-east-1"
        if (normRegion.startsWith(normPin + "-") || normRegion.startsWith(normPin)) {
            return true;
        }

        return false;
    }
}
