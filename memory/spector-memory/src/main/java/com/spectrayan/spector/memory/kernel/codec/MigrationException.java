/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-memory/LICENSE
 *
 * Change Date: May 27, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.memory.kernel.codec;

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
