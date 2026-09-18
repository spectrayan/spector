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
