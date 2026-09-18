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
 * Exception thrown when a temporal chain operation fails.
 *
 * <p>Covers link, followForward, followBackward,
 * and session-boundary operations ({@code SPE-310-007}).</p>
 *
 * @see ErrorCode#GRAPH_TEMPORAL_FAILED
 */
public class SpectorTemporalChainException extends SpectorGraphException {

    private final String operation;

    public SpectorTemporalChainException(String operation) {
        super(ErrorCode.GRAPH_TEMPORAL_FAILED, operation);
        this.operation = operation;
    }

    public SpectorTemporalChainException(String operation, Throwable cause) {
        super(ErrorCode.GRAPH_TEMPORAL_FAILED, cause, operation);
        this.operation = operation;
    }

    /** Returns the temporal chain operation that failed. */
    public String getOperation() {
        return operation;
    }
}
