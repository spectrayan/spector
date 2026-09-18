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
package com.spectrayan.spector.synapse.catalog.exception;

import com.spectrayan.spector.kernel.region.RegionId;

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
