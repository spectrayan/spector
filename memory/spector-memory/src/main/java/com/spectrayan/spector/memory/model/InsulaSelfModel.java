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

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.Map;

/**
 * Container representation of the self-model JSON payload stored inside the INSULA region.
 */
public record InsulaSelfModel(
        @JsonProperty("type") String type,
        @JsonProperty("soul") SoulContext soul,
        @JsonProperty("salience") SalienceProfile salience,
        @JsonProperty("metadata") Map<String, Object> metadata
) {
    public InsulaSelfModel {
        metadata = metadata != null ? Collections.unmodifiableMap(metadata) : Map.of();
    }
}
