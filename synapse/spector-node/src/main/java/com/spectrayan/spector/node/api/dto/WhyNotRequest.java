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
 * Request DTO for {@code POST /memory/why-not}.
 *
 * @param memoryId the ID of the memory to investigate
 * @param query    the query it was expected to match
 * @param topK     the topK used in the original recall (optional, default 5)
 */
public record WhyNotRequest(
        @JsonProperty("memoryId") String memoryId,
        @JsonProperty("query") String query,
        @JsonProperty("topK") Integer topK
) {
    public WhyNotRequest {
        if (memoryId == null || memoryId.isBlank()) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "memoryId", "required and must not be blank");
        }
        if (query == null || query.isBlank()) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "query", "required and must not be blank");
        }
    }

    /** Returns the effective topK, defaulting to 5 if not specified. */
    public int effectiveTopK() {
        return topK != null ? topK : 5;
    }
}
