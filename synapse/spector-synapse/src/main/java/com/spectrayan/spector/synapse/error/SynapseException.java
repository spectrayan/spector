/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-synapse/LICENSE
 *
 * Change Date: July 6, 2030
 * Change License: Apache License, Version 2.0
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
