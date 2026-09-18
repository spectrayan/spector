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
 * Exception thrown when memory layout migration or codec version resolution fails.
 *
 * <p>Uses {@link ErrorCode#MEMORY_LAYOUT_MIGRATION_FAILED} ({@code SPE-310-018}).</p>
 *
 * @since 1.4.0
 */
public class SpectorLayoutMigrationException extends SpectorMemoryException {

    private final String details;

    public SpectorLayoutMigrationException(String details) {
        super(ErrorCode.MEMORY_LAYOUT_MIGRATION_FAILED, details);
        this.details = details;
    }

    public SpectorLayoutMigrationException(String details, Throwable cause) {
        super(ErrorCode.MEMORY_LAYOUT_MIGRATION_FAILED, cause, details);
        this.details = details;
    }

    public SpectorLayoutMigrationException(ErrorCode errorCode, String details) {
        super(errorCode, details);
        this.details = details;
    }

    public SpectorLayoutMigrationException(ErrorCode errorCode, Throwable cause, String details) {
        super(errorCode, cause, details);
        this.details = details;
    }

    public String getDetails() {
        return details;
    }
}
