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
package com.spectrayan.spector.memory.aisme.workspace;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;

/**
 * Meta-cognitive representation of the agent's active attentional state (Graziano's Attention Schema).
 *
 * <h3>Biological Analog: Attention Schema / Meta-Attentional Awareness</h3>
 * <p>Maintains an explicit internal model of what the cognitive architecture is currently paying
 * attention to, enabling self-monitoring, uncertainty communication, and focal stability.</p>
 */
public record AttentionSchema(
        String dominantFocus,
        float focusPrecision,
        long timestampMs,
        String descriptiveRationale
) {

    /**
     * Compact constructor with validation and sensible defaults.
     */
    public AttentionSchema {
        if (dominantFocus == null || dominantFocus.isBlank()) {
            dominantFocus = "GENERAL_ASSOCIATION";
        }
        if (descriptiveRationale == null) {
            descriptiveRationale = "";
        }
        if (Float.isNaN(focusPrecision) || focusPrecision < 0.0f) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Focus precision must be non-negative");
        }
    }

    /**
     * Creates a default baseline attention schema.
     *
     * @return baseline AttentionSchema
     */
    public static AttentionSchema defaultSchema() {
        return new AttentionSchema("GENERAL_ASSOCIATION", 1.0f, System.currentTimeMillis(), "Default exploratory attention");
    }
}
