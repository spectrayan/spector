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
    private final FaultKind kind;
    private final boolean nested;

    /**
     * Constructs a new exception with the specified detail message.
     *
     * @param message the detail message
     */
    public CognitivePathwayException(final String message) {
        super(ErrorCode.MEMORY_PATHWAY_FAILED, message);
        this.pathwayName = "unknown";
        this.relayName = "unknown";
        this.kind = FaultKind.INTERNAL;
        this.nested = false;
    }

    /**
     * Constructs a new exception with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause   the cause
     */
    public CognitivePathwayException(final String message, final Throwable cause) {
        super(ErrorCode.MEMORY_PATHWAY_FAILED, cause, message);
        this.pathwayName = "unknown";
        this.relayName = "unknown";
        this.kind = Faults.kindOf(cause);
        this.nested = false;
    }

    /**
     * Constructs a new exception with pathway, relay, and cause.
     *
     * @param pathwayName the name of the pathway
     * @param relayName   the name of the relay
     * @param cause       the cause
     */
    public CognitivePathwayException(final String pathwayName, final String relayName, final Throwable cause) {
        this(pathwayName, relayName, Faults.kindOf(cause), false, cause);
    }

    /**
     * Constructs a new exception with full pathway, relay, fault kind, and nesting details.
     *
     * @param pathwayName the name of the pathway
     * @param relayName   the name of the relay
     * @param kind        the classified fault kind
     * @param nested      true if this failure originated from a nested pathway
     * @param cause       the cause
     */
    public CognitivePathwayException(final String pathwayName,
                                     final String relayName,
                                     final FaultKind kind,
                                     final boolean nested,
                                     final Throwable cause) {
        this(ErrorCode.MEMORY_PATHWAY_FAILED, pathwayName, relayName, kind, nested, cause);
    }

    /**
     * Constructs a new exception with explicit ErrorCode, pathway, relay, fault kind, and nesting details.
     *
     * @param errorCode   the specific error code
     * @param pathwayName the name of the pathway
     * @param relayName   the name of the relay
     * @param kind        the classified fault kind
     * @param nested      true if this failure originated from a nested pathway
     * @param cause       the cause
     */
    public CognitivePathwayException(final ErrorCode errorCode,
                                     final String pathwayName,
                                     final String relayName,
                                     final FaultKind kind,
                                     final boolean nested,
                                     final Throwable cause) {
        super(errorCode != null ? errorCode : ErrorCode.MEMORY_PATHWAY_FAILED,
                cause,
                "Failed at relay '" + relayName + "' in pathway '" + pathwayName + "': "
                        + (cause != null ? cause.getMessage() : "unknown"));
        this.pathwayName = pathwayName != null ? pathwayName : "unknown";
        this.relayName = relayName != null ? relayName : "unknown";
        this.kind = kind != null ? kind : FaultKind.INTERNAL;
        this.nested = nested;
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

    /**
     * Returns the classified fault kind.
     *
     * @return fault kind
     */
    public FaultKind kind() {
        return kind;
    }

    /**
     * Returns whether this failure originated from a nested pathway invocation.
     *
     * @return true if nested
     */
    public boolean nested() {
        return nested;
    }
}
