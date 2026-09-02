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
package com.spectrayan.spector.bench.conformance.model;

import java.util.List;
import com.spectrayan.spector.memory.model.CognitiveProfile;

/**
 * Record representing a single query/cue in the MF-001 Conformance Test Suite.
 */
public record MfQuery(
        String id,
        String text,
        List<String> goldConstraintIds,
        List<String> staleIds,
        CognitiveProfile cognitiveProfile,
        MfValenceWindow valenceWindow,
        MfTimeWindow timeWindow,
        Float minImportance,
        int topK,
        boolean allowSimulated,
        String expectedSubsystem
) {
    public MfQuery {
        goldConstraintIds = goldConstraintIds != null ? List.copyOf(goldConstraintIds) : List.of();
        staleIds = staleIds != null ? List.copyOf(staleIds) : List.of();
        cognitiveProfile = cognitiveProfile != null ? cognitiveProfile : CognitiveProfile.BALANCED;
        topK = topK > 0 ? topK : 10;
    }
}
