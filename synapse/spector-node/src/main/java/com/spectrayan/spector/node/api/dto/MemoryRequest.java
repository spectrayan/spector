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

/**
 * Request DTO for storing a memory via the REST API.
 *
 * <p>All cognitive parameters (tier, ICNU hints, valence, arousal) are optional
 * and backward compatible with basic text-only usage.</p>
 *
 * @param id        unique memory identifier (required)
 * @param text      memory content (required)
 * @param tier      memory tier: WORKING, EPISODIC, SEMANTIC (default), PROCEDURAL
 * @param source    provenance: USER_STATED, OBSERVED (default), INFERRED, PROCEDURAL
 * @param tags      comma-separated contextual tags
 * @param interest  ICNU Interest (0.0–1.0)
 * @param challenge ICNU Challenge (0.0–1.0)
 * @param urgency   ICNU Urgency (0.0–1.0)
 * @param valence   emotional valence (-128 to +127)
 * @param arousal   emotional intensity (0 to 255)
 */
public record MemoryRequest(
        @JsonProperty("id") String id,
        @JsonProperty("text") String text,
        @JsonProperty("tier") String tier,
        @JsonProperty("source") String source,
        @JsonProperty("tags") String tags,
        @JsonProperty("interest") Float interest,
        @JsonProperty("challenge") Float challenge,
        @JsonProperty("urgency") Float urgency,
        @JsonProperty("valence") Integer valence,
        @JsonProperty("arousal") Integer arousal
) {
    /** Returns the tier or default "SEMANTIC". */
    public String effectiveTier() {
        return tier != null && !tier.isBlank() ? tier.toUpperCase() : "SEMANTIC";
    }

    /** Returns the source or default "OBSERVED". */
    public String effectiveSource() {
        return source != null && !source.isBlank() ? source.toUpperCase() : "OBSERVED";
    }

    /** Returns tags as array, or empty array. */
    public String[] tagsArray() {
        if (tags == null || tags.isBlank()) return new String[0];
        return tags.split(",");
    }

    /** Returns true if any ICNU/emotional hint was provided. */
    public boolean hasCognitiveHints() {
        return (interest != null && interest > 0)
                || (challenge != null && challenge > 0)
                || (urgency != null && urgency > 0)
                || (valence != null && valence != 0)
                || (arousal != null && arousal != 0);
    }
}
