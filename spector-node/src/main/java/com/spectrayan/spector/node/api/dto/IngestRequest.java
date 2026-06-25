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

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;

/**
 * Request DTO for document ingestion ({@code POST /api/v1/ingest}).
 *
 * <p>For auto-embedding (no vector provided), use {@code POST /api/v1/ingest/auto}
 * which does not require the {@code vector} field.</p>
 */
public class IngestRequest {

    /** Document ID (required). */
    public String id;

    /** Optional document title. */
    public String title;

    /** Document content (required). */
    public String content;

    /** Pre-computed embedding vector (required for /ingest, optional for /ingest/auto). */
    public float[] vector;

    /**
     * Validates the required fields for manual ingestion (with vector).
     *
     * @param expectedDimensions the expected vector dimensions from engine config
     * @throws ValidationException if validation fails
     */
    public void validateForIngest(int expectedDimensions) {
        if (id == null || id.isEmpty()) throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "id", "required");
        if (content == null || content.isEmpty()) throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "content", "required");
        if (vector == null || vector.length == 0) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "vector", "required (use /api/v1/ingest/auto for auto-embedding)");
        }
        if (vector.length != expectedDimensions) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "vector", "dimension mismatch: expected " + expectedDimensions + ", got " + vector.length);
        }
    }

    /**
     * Validates the required fields for auto-embedding ingestion.
     *
     * @throws ValidationException if validation fails
     */
    public void validateForAutoIngest() {
        if (id == null || id.isEmpty()) throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "id", "required");
        if (content == null || content.isEmpty()) throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "content", "required");
    }

    /** Returns the title, defaulting to empty string if null. */
    public String titleOrEmpty() {
        return title != null ? title : "";
    }
}
