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

/**
 * Thrown when an operation cannot be completed due to a conflict with existing data state
 * (e.g. duplicate username, existing key, or concurrent mutation conflict).
 */
public class SynapseConflictException extends SynapseException {

    private final String conflictField;
    private final String conflictValue;

    public SynapseConflictException(String conflictField, String conflictValue) {
        super(ErrorCode.API_CONFLICT, conflictField + " '" + conflictValue + "'");
        this.conflictField = conflictField;
        this.conflictValue = conflictValue;
    }

    public SynapseConflictException(String message) {
        super(ErrorCode.API_CONFLICT, message);
        this.conflictField = null;
        this.conflictValue = null;
    }

    public String getConflictField() {
        return conflictField;
    }

    public String getConflictValue() {
        return conflictValue;
    }
}
