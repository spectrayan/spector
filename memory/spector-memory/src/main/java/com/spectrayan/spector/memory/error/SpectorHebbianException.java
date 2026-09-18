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
package com.spectrayan.spector.memory.error;

import com.spectrayan.spector.commons.error.ErrorCode;

/**
 * Exception thrown when a Hebbian graph operation fails.
 *
 * <p>Covers edge strengthening, spreading activation, decay,
 * and session boundary detection ({@code SPE-310-006}).</p>
 *
 * @see ErrorCode#GRAPH_HEBBIAN_FAILED
 */
public class SpectorHebbianException extends SpectorGraphException {

    private final String operation;

    public SpectorHebbianException(String operation) {
        super(ErrorCode.GRAPH_HEBBIAN_FAILED, operation);
        this.operation = operation;
    }

    public SpectorHebbianException(String operation, Throwable cause) {
        super(ErrorCode.GRAPH_HEBBIAN_FAILED, cause, operation);
        this.operation = operation;
    }

    /** Returns the Hebbian operation that failed. */
    public String getOperation() {
        return operation;
    }
}
