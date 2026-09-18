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
 * Exception thrown when graph decay or pruning fails during consolidation.
 *
 * <p>Covers Hebbian edge decay, temporal chain pruning,
 * and entity graph homeostasis ({@code SPE-310-011}).</p>
 *
 * @see ErrorCode#GRAPH_DECAY_FAILED
 */
public class SpectorGraphDecayException extends SpectorGraphException {

    private final String details;

    public SpectorGraphDecayException(String details) {
        super(ErrorCode.GRAPH_DECAY_FAILED, details);
        this.details = details;
    }

    public SpectorGraphDecayException(String details, Throwable cause) {
        super(ErrorCode.GRAPH_DECAY_FAILED, cause, details);
        this.details = details;
    }

    /** Returns details of the decay failure. */
    public String getDetails() {
        return details;
    }
}
