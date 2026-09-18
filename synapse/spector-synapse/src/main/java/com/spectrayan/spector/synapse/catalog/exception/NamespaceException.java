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

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.synapse.error.SynapseException;

/**
 * Base exception for all namespace catalog operations.
 */
public abstract class NamespaceException extends SynapseException {

    private final String errorCodeName;

    /**
     * Creates a new namespace exception.
     *
     * @param errorCode     the stable error code identifying this condition
     * @param errorCodeName the name identifier of the error code
     * @param args          values to substitute into the template's {@code {}} placeholders
     */
    protected NamespaceException(ErrorCode errorCode, String errorCodeName, Object... args) {
        super(errorCode, args);
        this.errorCodeName = errorCodeName;
    }

    /**
     * Gets the error code name identifier.
     *
     * @return the error code name
     */
    public String getErrorCodeName() {
        return errorCodeName;
    }
}
