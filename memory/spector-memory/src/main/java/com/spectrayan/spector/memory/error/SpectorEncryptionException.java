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
package com.spectrayan.spector.memory.error;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorMemoryException;

/**
 * Exception thrown when memory encryption or decryption fails.
 *
 * <p>Uses {@link ErrorCode#MEMORY_ENCRYPTION_FAILED} ({@code SPE-310-019}).</p>
 *
 * @since 1.4.0
 */
public class SpectorEncryptionException extends SpectorMemoryException {

    private final String details;

    public SpectorEncryptionException(String details) {
        super(ErrorCode.MEMORY_ENCRYPTION_FAILED, details);
        this.details = details;
    }

    public SpectorEncryptionException(String details, Throwable cause) {
        super(ErrorCode.MEMORY_ENCRYPTION_FAILED, cause, details);
        this.details = details;
    }

    public SpectorEncryptionException(ErrorCode errorCode, String details) {
        super(errorCode, details);
        this.details = details;
    }

    public SpectorEncryptionException(ErrorCode errorCode, Throwable cause, String details) {
        super(errorCode, cause, details);
        this.details = details;
    }

    public String getDetails() {
        return details;
    }
}
