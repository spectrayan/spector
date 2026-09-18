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
package com.spectrayan.spector.memory.aisme.phi;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;

/**
 * Immutable record capturing the consciousness continuity state and IIT integration metrics.
 *
 * <h3>Biological Analog: Neural Integration & Subjective Identity Continuity</h3>
 * <p>Represents the holistic synergy of a retrieved memory subgraph, quantifying both
 * internal causal integration (Phi) and experiential alignment to the persona's core identity.</p>
 */
public record ConsciousnessContinuityState(
        float rawPhi,
        float soulAlignment,
        float compositePhiCC,
        boolean isCohesive,
        int candidateCount,
        long timestampMs
) {

    /**
     * Compact constructor with validation.
     */
    public ConsciousnessContinuityState {
        if (Float.isNaN(rawPhi) || rawPhi < 0.0f) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "rawPhi must be non-negative");
        }
        if (Float.isNaN(soulAlignment) || soulAlignment < 0.0f || soulAlignment > 1.0f) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "soulAlignment must be in [0, 1]");
        }
        if (Float.isNaN(compositePhiCC) || compositePhiCC < 0.0f) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "compositePhiCC must be non-negative");
        }
        if (candidateCount < 0) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "candidateCount cannot be negative");
        }
    }

    /**
     * Default empty / uninitialized continuity state.
     *
     * @return empty state
     */
    public static ConsciousnessContinuityState empty() {
        return new ConsciousnessContinuityState(0.0f, 1.0f, 0.0f, true, 0, System.currentTimeMillis());
    }
}
