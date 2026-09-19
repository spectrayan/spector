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
package com.spectrayan.spector.synapse.agent.chat.service;

import com.spectrayan.spector.kernel.api.MemorySource;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.synapse.agent.chat.dto.ChatDto.PrimedMemory;

import java.util.List;

/**
 * Port interface connecting chat execution to Spector's cognitive memory plane (ADR-0084).
 *
 * <p>Separates read-only context priming from factual/preference memory ingestion,
 * strictly protected by {@link com.spectrayan.spector.synapse.agent.chat.policy.MemoryTagPolicy}.</p>
 */
public interface ChatCognitivePort {

    /**
     * Recalls semantically relevant memories across past interactions in read-only OBSERVE mode.
     * Guaranteed to cause no Long-Term Potentiation (LTP) side effects or decay increments.
     */
    List<PrimedMemory> primeContext(String query, int limit);

    /**
     * Ingests a salient fact, preference, or reflected knowledge into cognitive memory.
     * Enforces provenance and tag hygiene invariants.
     */
    void ingestSalientMemory(String text, MemoryType type, MemorySource source, List<String> tags);
}
