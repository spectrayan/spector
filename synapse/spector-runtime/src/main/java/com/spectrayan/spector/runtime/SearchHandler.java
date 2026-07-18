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
package com.spectrayan.spector.runtime;

import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.SpectorMemory;

/**
 * Search service querying the cognitive memory subsystem.
 *
 * <p>Obtained via {@code runtime.search()}. Not instantiated directly.</p>
 */
public final class SearchHandler {

    private static final Logger log = LoggerFactory.getLogger(SearchHandler.class);

    private final SpectorMemory memory;

    SearchHandler(SpectorMemory memory) {
        this.memory = Objects.requireNonNull(memory, "memory");
    }

    /**
     * Executes a cognitive memory search.
     *
     * @param text query text
     * @param topK maximum results to return
     * @return list of unified results
     */
    public List<SpectorResult> query(String text, int topK) {
        var options = RecallOptions.builder().topK(topK).build();
        List<CognitiveResult> results = memory.recall(text, options);
        return results.stream()
                .map(r -> SpectorResult.fromMemory(
                        r.id(), r.text(), r.score(),
                        r.importance(), r.ageDays(),
                        r.valence(), r.synapticTags(), r.memoryType(),
                        r.sourceModality(), r.sourceUri()))
                .toList();
    }
}
