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
