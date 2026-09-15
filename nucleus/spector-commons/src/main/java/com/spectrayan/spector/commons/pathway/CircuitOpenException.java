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

/**
 * Exception thrown when a cognitive pathway relay is invoked while its circuit breaker is in OPEN state.
 */
public class CircuitOpenException extends CognitivePathwayException {

    /**
     * Constructs a new circuit open exception.
     *
     * @param pathwayName the name of the pathway
     * @param relayName   the name of the relay
     */
    public CircuitOpenException(final String pathwayName, final String relayName) {
        this(pathwayName, relayName, null);
    }

    /**
     * Constructs a new circuit open exception with a cause.
     *
     * @param pathwayName the name of the pathway
     * @param relayName   the name of the relay
     * @param cause       the cause of the circuit tripping, if known
     */
    public CircuitOpenException(final String pathwayName, final String relayName, final Throwable cause) {
        super(ErrorCode.PATHWAY_CIRCUIT_OPEN, pathwayName, relayName, FaultKind.TRANSIENT, false, cause);
    }
}
