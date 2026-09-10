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
package com.spectrayan.spector.kernel.migration;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorException;

/**
 * Thrown when migration execution or format detection fails.
 */
public class MigrationException extends SpectorException {

    public enum Reason {
        UNRECOGNIZED_FORMAT,
        NO_UPGRADE_PATH,
        STEP_FAILED,
        VALIDATION_FAILED
    }

    private final Reason reason;
    private final FormatId formatId;

    public MigrationException(Reason reason, FormatId formatId, String message) {
        super(ErrorCode.STORAGE_MIGRATION_FAILED, message);
        this.reason = reason;
        this.formatId = formatId;
    }

    public MigrationException(Reason reason, FormatId formatId, String message, Throwable cause) {
        super(ErrorCode.STORAGE_MIGRATION_FAILED, cause, message);
        this.reason = reason;
        this.formatId = formatId;
    }

    public Reason reason() {
        return reason;
    }

    public FormatId formatId() {
        return formatId;
    }
}
