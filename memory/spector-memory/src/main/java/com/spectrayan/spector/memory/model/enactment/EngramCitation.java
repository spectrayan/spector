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
package com.spectrayan.spector.memory.model.enactment;

import com.spectrayan.spector.kernel.api.MemoryType;

import java.util.List;

/**
 * Citation pointing to a specific engram justifying an enactment stance (ADR-0032).
 */
public record EngramCitation(
        String memoryId,
        MemoryType tier,
        List<String> tags,
        float score,
        boolean synthetic
) {
    public EngramCitation {
        tags = (tags != null) ? List.copyOf(tags) : List.of();
    }
}
