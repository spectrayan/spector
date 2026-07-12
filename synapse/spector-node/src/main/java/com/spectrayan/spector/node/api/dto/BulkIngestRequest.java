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

import java.util.List;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;

/**
 * Request DTO for bulk document ingestion ({@code POST /api/v1/ingest/bulk}).
 */
public class BulkIngestRequest {

    /** List of documents to ingest. */
    public List<IngestRequest> documents;

    /**
     * Validates that the documents list is non-empty.
     *
     * @throws ValidationException if validation fails
     */
    public void validate() {
        if (documents == null || documents.isEmpty()) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "documents", "non-empty array required");
        }
    }
}
