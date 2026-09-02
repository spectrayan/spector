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
import com.spectrayan.spector.memory.model.MemoryType;

/**
 * Record representing a single corpus trace in the MF-001 Conformance Test Suite.
 */
public record MfCorpusRecord(
        String id,
        String text,
        String title,
        MemoryType memoryType,
        String source,
        long timestampMs,
        String sessionId,
        float importance,
        byte valence,
        int arousal,
        float interest,
        float challenge,
        float urgency,
        float novelty,
        boolean resolved,
        List<String> synapticTags,
        String rememberer,
        String soulMatch
) {
    public MfCorpusRecord {
        synapticTags = synapticTags != null ? List.copyOf(synapticTags) : List.of();
        memoryType = memoryType != null ? memoryType : MemoryType.EPISODIC;
        source = source != null ? source : "experienced";
    }
}
