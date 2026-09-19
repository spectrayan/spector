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
package com.spectrayan.spector.synapse.agent.chat.infrastructure;

import com.spectrayan.spector.kernel.api.MemorySource;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.memory.model.RecallMode;
import com.spectrayan.spector.synapse.agent.chat.dto.ChatDto.PrimedMemory;
import com.spectrayan.spector.synapse.agent.chat.policy.MemoryTagPolicy;
import com.spectrayan.spector.synapse.agent.chat.service.ChatCognitivePort;
import com.spectrayan.spector.synapse.memory.MemoryDto.RecallRequest;
import com.spectrayan.spector.synapse.memory.MemoryDto.RecallResult;
import com.spectrayan.spector.synapse.memory.MemoryDto.RememberRequest;
import com.spectrayan.spector.synapse.memory.MemoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * Adapter implementation of {@link ChatCognitivePort} routing through {@link MemoryService}.
 *
 * <p>Enforces ADR-0084 invariants:
 * <ul>
 *   <li>Primes context using {@link RecallMode#OBSERVE} to suppress associative/Hebbian side effects</li>
 *   <li>Enforces {@link MemoryTagPolicy} validations before writing to cognitive memory</li>
 *   <li>Filters out any legacy contaminated tags on recall</li>
 * </ul>
 * </p>
 */
@Component
public class SpectorSalientMemoryAdapter implements ChatCognitivePort {

    private static final Logger log = LoggerFactory.getLogger(SpectorSalientMemoryAdapter.class);

    private final MemoryService memoryService;
    private final MemoryTagPolicy tagPolicy;

    public SpectorSalientMemoryAdapter(MemoryService memoryService, MemoryTagPolicy tagPolicy) {
        this.memoryService = Objects.requireNonNull(memoryService, "MemoryService must not be null");
        this.tagPolicy = Objects.requireNonNull(tagPolicy, "MemoryTagPolicy must not be null");
    }

    @Override
    public List<PrimedMemory> primeContext(String query, int limit) {
        if (query == null || query.isBlank() || !memoryService.isEngineAvailable()) {
            return List.of();
        }

        int effLimit = limit > 0 ? Math.min(limit, 20) : 5;

        try {
            // Strictly invoke recall in OBSERVE mode (zero LTP or decay side effects)
            RecallRequest req = new RecallRequest(
                    query,
                    effLimit,
                    1,
                    null,
                    null,
                    RecallMode.OBSERVE.name()
            );

            List<RecallResult> results = memoryService.recall(req);
            if (results == null || results.isEmpty()) {
                return List.of();
            }

            return results.stream()
                    // Defensive guard: filter out any legacy contaminated tags
                    .filter(r -> r.tags() == null || r.tags().stream().noneMatch(tagPolicy::isForbiddenTag))
                    .map(r -> new PrimedMemory(
                            r.text(),
                            r.memoryType(),
                            r.ageDescription(),
                            (float) r.cognitiveScore(),
                            (float) r.cognitiveScore(),
                            r.tags() != null ? r.tags() : List.of()
                    ))
                    .toList();

        } catch (Exception e) {
            log.warn("[SpectorSalientAdapter] Context priming failed for query '{}': {}", query, e.getMessage());
            return List.of();
        }
    }

    @Override
    public void ingestSalientMemory(String text, MemoryType type, MemorySource source, List<String> tags) {
        // Enforce ADR-0084 hygiene policy
        tagPolicy.validate(text, tags, source);

        MemoryType effType = type != null ? type : MemoryType.EPISODIC;
        MemorySource effSource = source != null ? source : MemorySource.OBSERVED;
        String tagsStr = (tags != null && !tags.isEmpty()) ? String.join(",", tags) : null;

        RememberRequest req = new RememberRequest(
                null, // TSID auto-generated
                text,
                effType.name(),
                effSource.name(),
                tagsStr,
                null, null, null, null, null, null
        );

        memoryService.remember(req);
        log.debug("[SpectorSalientAdapter] Ingested salient memory (type={}, source={}, tags={})",
                effType, effSource, tags);
    }
}
