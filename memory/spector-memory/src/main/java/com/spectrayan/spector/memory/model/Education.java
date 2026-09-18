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
package com.spectrayan.spector.memory.model;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;

/**
 * Educational background entry — represents a single degree or qualification.
 *
 * <h3>Cognitive Relevance</h3>
 * <p>Education field is a primary component of the self-schema. Expert memory
 * research (Chase &amp; Simon, 1973; Ericsson &amp; Kintsch, 1995) demonstrates
 * that domain expertise creates encoding scaffolding — a CS graduate encodes
 * technical content more deeply than non-technical content because existing
 * knowledge structures provide "hooks" for new information.</p>
 *
 * <h3>Schema Origin</h3>
 * <p>Mirrors {@code consciousness/identity/Education.yaml} with identical fields.</p>
 *
 * <h3>Scoring Usage</h3>
 * <p>The {@code degree} field is embedded at profile-save time and used for
 * self-relevance matching via cosine similarity in
 * {@link SalienceProfile#computeSelfRelevanceBoost}.</p>
 *
 * @param institution educational institution name (e.g., "Stanford University")
 * @param degree      degree obtained (e.g., "Bachelor of Science in Computer Science")
 * @param startYear   year education started
 * @param endYear     year education completed (nullable if ongoing)
 * @param description additional details (e.g., "Graduated with honors, GPA 3.8")
 */
public record Education(
        String institution,
        String degree,
        int startYear,
        Integer endYear,
        String description
) {

    /**
     * Compact constructor — validates required fields.
     */
    public Education {
        if (institution == null || institution.isBlank()) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID,
                    "institution", "must not be null or blank");
        }
        if (degree == null || degree.isBlank()) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID,
                    "degree", "must not be null or blank");
        }
        if (startYear < 1900 || startYear > 2100) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_OUT_OF_RANGE,
                    "startYear", 1900, 2100, startYear);
        }
        if (endYear != null && endYear < startYear) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID,
                    "endYear", endYear + " must not be before start year " + startYear);
        }
    }

    /**
     * Convenience constructor without description.
     */
    public Education(String institution, String degree, int startYear, Integer endYear) {
        this(institution, degree, startYear, endYear, null);
    }

    /**
     * Returns true if education is ongoing (no end year).
     */
    public boolean isOngoing() {
        return endYear == null;
    }
}
