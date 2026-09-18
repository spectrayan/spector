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
package com.spectrayan.spector.synapse.error;

import com.spectrayan.spector.commons.error.ErrorCode;

/**
 * Thrown when a requested resource or entity is not found in Synapse.
 */
public class SynapseNotFoundException extends SynapseException {

    private final String resourceType;
    private final String resourceId;

    /**
     * Creates a new not-found exception.
     *
     * @param resourceType the type of resource (e.g. "User", "Credential", "Route", "Config")
     * @param resourceId   the unique identifier or handle requested
     */
    public SynapseNotFoundException(String resourceType, String resourceId) {
        super(ErrorCode.API_NOT_FOUND, resourceType + " '" + resourceId + "'");
        this.resourceType = resourceType;
        this.resourceId = resourceId;
    }

    public String getResourceType() {
        return resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }
}
