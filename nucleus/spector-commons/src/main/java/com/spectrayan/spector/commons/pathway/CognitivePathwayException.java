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
package com.spectrayan.spector.commons.pathway;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorServerException;

/**
 * Exception thrown when an error occurs within a cognitive pathway execution.
 */
public class CognitivePathwayException extends SpectorServerException {

    private final String pathwayName;
    private final String relayName;

    /**
     * Constructs a new exception with the specified detail message.
     *
     * @param message the detail message
     */
    public CognitivePathwayException(final String message) {
        super(ErrorCode.INTERNAL_ERROR, message);
        this.pathwayName = "unknown";
        this.relayName = "unknown";
    }

    /**
     * Constructs a new exception with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause   the cause
     */
    public CognitivePathwayException(final String message, final Throwable cause) {
        super(ErrorCode.INTERNAL_ERROR, cause, message);
        this.pathwayName = "unknown";
        this.relayName = "unknown";
    }

    /**
     * Constructs a new exception with full pathway and relay context.
     *
     * @param pathwayName the name of the pathway
     * @param relayName   the name of the relay
     * @param cause       the cause
     */
    public CognitivePathwayException(final String pathwayName, final String relayName, final Throwable cause) {
        super(ErrorCode.INTERNAL_ERROR, cause, "Failed at relay '" + relayName + "' in pathway '" + pathwayName + "'");
        this.pathwayName = pathwayName;
        this.relayName = relayName;
    }

    /**
     * Returns the name of the pathway where the failure occurred.
     *
     * @return pathway name
     */
    public String pathwayName() {
        return pathwayName;
    }

    /**
     * Returns the name of the relay where the failure occurred.
     *
     * @return relay name
     */
    public String relayName() {
        return relayName;
    }
}
