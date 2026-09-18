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
 * Exception thrown when the cognitive recall pipeline or memory identification fails.
 *
 * @see SpectorMemoryException
 */
public class SpectorMemoryRecallException extends SpectorMemoryException {

    private final String details;

    public SpectorMemoryRecallException(String details) {
        super(ErrorCode.MEMORY_RECALL_FAILED, details);
        this.details = details;
    }

    public SpectorMemoryRecallException(String details, Throwable cause) {
        super(ErrorCode.MEMORY_RECALL_FAILED, cause, details);
        this.details = details;
    }

    public SpectorMemoryRecallException(ErrorCode errorCode, String details) {
        super(errorCode, details);
        this.details = details;
    }

    public SpectorMemoryRecallException(ErrorCode errorCode, Throwable cause, String details) {
        super(errorCode, cause, details);
        this.details = details;
    }

    /** Returns details of the recall pipeline failure. */
    public String getDetails() {
        return details;
    }
}
