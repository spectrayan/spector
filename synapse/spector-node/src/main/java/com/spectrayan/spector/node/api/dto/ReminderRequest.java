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
package com.spectrayan.spector.node.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;

/**
 * Request DTO for {@code POST /memory/reminder}.
 *
 * @param text         the reminder text
 * @param delaySeconds seconds until the reminder triggers
 * @param tags         optional comma-separated tags
 */
public record ReminderRequest(
        @JsonProperty("text") String text,
        @JsonProperty("delaySeconds") int delaySeconds,
        @JsonProperty("tags") String tags
) {
    public ReminderRequest {
        if (text == null || text.isBlank()) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "text", "required and must not be blank");
        }
        if (delaySeconds <= 0) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_OUT_OF_RANGE, "delaySeconds", 1, Integer.MAX_VALUE, delaySeconds);
        }
    }

    /** Returns tags as an array, or empty array if none provided. */
    public String[] tagsArray() {
        if (tags == null || tags.isBlank()) return new String[0];
        return tags.split(",");
    }
}
