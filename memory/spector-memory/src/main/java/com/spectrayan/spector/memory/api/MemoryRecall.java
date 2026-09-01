/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-memory/LICENSE
 *
 * Change Date: May 27, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.memory.api;

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.kernel.layout.EpisodicFieldAccessor;
import com.spectrayan.spector.memory.model.CognitiveProfile;
import com.spectrayan.spector.memory.model.CognitiveRecord;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.FactHistory;
import com.spectrayan.spector.memory.model.GraphRecallOptions;
import com.spectrayan.spector.memory.model.GraphTraversalResult;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.model.WhyNotExplanation;
import com.spectrayan.spector.memory.session.EpisodicSessionIndex;
import com.spectrayan.spector.memory.temporal.TemporalFact;

import java.time.Instant;
import java.util.List;

/**
 * Interface Segregation (ISP): Recall, inspection, query, and search operations on cognitive memory.
 *
 * @since 1.4.0
 */
public interface MemoryRecall {

    List<CognitiveResult> recall(String queryText, RecallOptions options);

    List<CognitiveResult> recall(String queryText, CognitiveProfile profile);

    List<CognitiveResult> recall(String queryText);

    WhyNotExplanation whyNot(String memoryId, String queryText, RecallOptions options);

    CognitiveRecord inspect(String id);

    List<CognitiveRecord> browse(String... tags);

    default List<EpisodicFieldAccessor.EpisodicRecord> browseEpisodic(long sessionId, int offset, int limit) {
        return List.of();
    }

    default List<EpisodicFieldAccessor.EpisodicRecord> tailEpisodic(long sessionId, int count) {
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
