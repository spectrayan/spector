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
import com.spectrayan.spector.commons.error.SpectorException;

/**
 * Base abstract exception for all Spector Synapse operational, data access, and API failures.
 *
 * <p>Extends {@link SpectorException} to maintain unified {@link ErrorCode} tracking and
 * consistent error telemetry across the Spectrayan platform.</p>
 */
public abstract class SynapseException extends SpectorException {

    /**
     * Creates a new Synapse exception with a formatted message based on {@link ErrorCode}.
     *
     * @param errorCode the stable error code identifying this condition
     * @param args      values to substitute into the template's {@code {}} placeholders
     */
    protected SynapseException(ErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }

    /**
     * Creates a new Synapse exception with a cause and formatted message.
     *
     * @param errorCode the stable error code identifying this condition
     * @param cause     the underlying cause
     * @param args      values to substitute into the template's {@code {}} placeholders
     */
    protected SynapseException(ErrorCode errorCode, Throwable cause, Object... args) {
        super(errorCode, cause, args);
    }
}
