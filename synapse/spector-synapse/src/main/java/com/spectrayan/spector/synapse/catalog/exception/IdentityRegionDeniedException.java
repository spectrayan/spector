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
package com.spectrayan.spector.synapse.catalog.exception;

import com.spectrayan.spector.commons.error.ErrorCode;

/**
 * Thrown when access is denied to a specific identity bundle region.
 */
public class IdentityRegionDeniedException extends NamespaceException {

    private final String regionId;
    private final String bundleId;

    /**
     * Creates a new identity region denied exception.
     *
     * @param regionId the identifier of the identity region
     * @param bundleId the identifier of the identity bundle
     */
    public IdentityRegionDeniedException(String regionId, String bundleId) {
        super(ErrorCode.IDENTITY_REGION_DENIED, "IdentityRegionDenied", regionId, bundleId);
        this.regionId = regionId;
        this.bundleId = bundleId;
    }

    /**
     * Gets the identity region identifier.
     *
     * @return the region ID
     */
    public String getRegionId() {
        return regionId;
    }

    /**
     * Gets the identity bundle identifier.
     *
     * @return the bundle ID
     */
    public String getBundleId() {
        return bundleId;
    }
}
