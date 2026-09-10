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
package com.spectrayan.spector.client.exception;

import com.spectrayan.spector.kernel.id.MemoryId;

/**
 * Exception thrown when a requested memory record or resource is not found (HTTP 404).
 */
public class MemoryNotFoundException extends SpectorClientException {

    private final String memoryId;

    public MemoryNotFoundException(String memoryId, int statusCode, String message, String responseBody, Throwable cause) {
        super(statusCode, message, responseBody, cause);
        this.memoryId = memoryId;
    }

    /**
     * The ID of the memory that could not be found, or {@code null} if not applicable.
     */
    public String getMemoryId() {
        return memoryId;
    }
}
