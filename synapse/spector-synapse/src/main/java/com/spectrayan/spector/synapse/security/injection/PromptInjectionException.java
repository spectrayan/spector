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
package com.spectrayan.spector.synapse.security.injection;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.synapse.error.SynapseException;

/**
 * Thrown when {@link PromptShield} blocks content in {@code BLOCK} mode.
 */
public final class PromptInjectionException extends SynapseException {

    private final InjectionResult result;

    public PromptInjectionException(InjectionResult result) {
        super(
                ErrorCode.PROMPT_INJECTION_BLOCKED,
                result.type() != null ? result.type().name() : "UNKNOWN",
                result.message() != null ? result.message() : "injection detected");
        this.result = result;
    }

    public InjectionResult getResult() {
        return result;
    }
}
