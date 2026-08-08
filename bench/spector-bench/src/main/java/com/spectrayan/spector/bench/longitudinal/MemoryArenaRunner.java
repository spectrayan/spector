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
package com.spectrayan.spector.bench.longitudinal;

import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.model.CognitiveProfile;
import com.spectrayan.spector.memory.model.CognitiveResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Benchmark runner orchestrating multi-session longitudinal trajectory evaluations.
 */
public class MemoryArenaRunner {

    private static final Logger log = LoggerFactory.getLogger(MemoryArenaRunner.class);

    private final ConsistencyDetector consistencyDetector;

    public MemoryArenaRunner() {
        this.consistencyDetector = new ConsistencyDetector();
    }

    /**
     * Executes a longitudinal trajectory evaluation against a given SpectorMemory engine instance.
     *
     * @param trajectory Target multi-session trajectory
     * @param memoryEngine Spector memory engine instance
     * @param condition Label for experimental condition (e.g. "spector-full", "baseline-vector")
     * @param profile Cognitive profile configuration
     * @return Evaluated LongitudinalMetrics
     */
    public LongitudinalMetrics evaluateTrajectory(
            LongitudinalTrajectory trajectory,
            SpectorMemory memoryEngine,
            String condition,
            CognitiveProfile profile
    ) {
        Objects.requireNonNull(trajectory, "trajectory cannot be null");
        Objects.requireNonNull(memoryEngine, "memoryEngine cannot be null");
        Objects.requireNonNull(condition, "condition cannot be null");

        MemoryArenaAdapter adapter = new MemoryArenaAdapter(memoryEngine, profile);
        log.info("Starting longitudinal evaluation [condition={}, profile={}, sessions={}]",
                condition, profile, trajectory.sessions().size());

        double totalPreferenceScore = 0.0;
        double totalErrorScore = 0.0;
        int evaluatedSessions = 0;

        for (LongitudinalSession session : trajectory.sessions()) {
            // 1. Ingest ground truth context into memory if present
            session.groundTruthContext().forEach((key, val) -> {
                adapter.remember(key + ": " + val, 0.8f, 0.5f);
            });

            // 2. Perform recall query for current session prompt
            List<CognitiveResult> recalledRecords = adapter.recall(session.prompt(), 5);
            List<String> recalledText = recalledRecords.stream()
                    .map(CognitiveResult::text)
                    .collect(Collectors.toList());

            // 3. Evaluate metrics for session
            double prefScore = consistencyDetector.evaluatePreferenceStability(
                    session.groundTruthContext(), recalledText);
            double errorScore = consistencyDetector.evaluateErrorNonRepetition(
                    session.negativeConstraints(), recalledText);

            totalPreferenceScore += prefScore;
            totalErrorScore += errorScore;
            evaluatedSessions++;
        }

        double avgPrefScore = evaluatedSessions > 0 ? totalPreferenceScore / evaluatedSessions : 1.0;
        double avgErrorScore = evaluatedSessions > 0 ? totalErrorScore / evaluatedSessions : 1.0;
        double completionRate = (avgPrefScore + avgErrorScore) / 2.0;

        return new LongitudinalMetrics(
                condition,
                completionRate,
                avgPrefScore,
                avgPrefScore,
                avgErrorScore,
                completionRate,
                evaluatedSessions
        );
    }
}
