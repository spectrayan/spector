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

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorException;

/**
 * Thrown when a disaster recovery export or restore violates tenant geographic data residency constraints
 * (ADR-0034 §11.2, §16, Req R9.2, R9.3, R9.4, V6).
 */
public class ResidencyViolationException extends SpectorException {

    private final String tenantJurisdiction;
    private final String targetRegion;

    public ResidencyViolationException(String tenantJurisdiction, String targetRegion, String message) {
        super(ErrorCode.ARGUMENT_INVALID, message);
        this.tenantJurisdiction = tenantJurisdiction;
        this.targetRegion = targetRegion;
    }

    public String getTenantJurisdiction() {
        return tenantJurisdiction;
    }

    public String getTargetRegion() {
        return targetRegion;
    }
}
