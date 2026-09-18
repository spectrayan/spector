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
package com.spectrayan.spector.memory.api;

import com.spectrayan.spector.kernel.api.EpisodeRecord;
import com.spectrayan.spector.memory.model.CognitiveProfile;
import com.spectrayan.spector.memory.model.CognitiveRecord;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.FactHistory;
import com.spectrayan.spector.memory.model.GraphRecallOptions;
import com.spectrayan.spector.memory.model.GraphTraversalResult;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.model.WhyNotExplanation;
import com.spectrayan.spector.memory.session.EpisodicSessionIndex;
import com.spectrayan.spector.kernel.store.TemporalFact;

import java.time.Instant;
import java.util.List;

/**
 * Recall, inspection, query, and search operations on cognitive memory.
 *
 * @since 1.4.0
 */
public interface MemoryRecall {

    List<CognitiveResult> recall(String queryText, RecallOptions options);

    List<CognitiveResult> recall(String queryText, CognitiveProfile profile);

    List<CognitiveResult> recall(String queryText);

    default RecallOptions defaultRecallOptions() {
        return RecallOptions.DEFAULT;
    }

    WhyNotExplanation whyNot(String memoryId, String queryText, RecallOptions options);

    CognitiveRecord inspect(String id);

    List<CognitiveRecord> browse(String... tags);

    default List<EpisodeRecord> browseEpisodic(long sessionId, int offset, int limit) {
        return List.of();
    }

    default List<EpisodeRecord> tailEpisodic(long sessionId, int count) {
        return List.of();
    }

    default EpisodicSessionIndex episodicSessionIndex() {
        return null;
    }

    String exportJson();

    GraphTraversalResult graphRecall(GraphRecallOptions options);

    List<TemporalFact> factsAbout(String entityName, Instant asOf);

    FactHistory factHistory(String subject, String predicate);
}
