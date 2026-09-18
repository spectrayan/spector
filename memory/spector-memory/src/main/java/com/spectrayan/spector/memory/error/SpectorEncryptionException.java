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
