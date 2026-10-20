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
package com.spectrayan.spector.mel;

import java.util.List;
import java.util.Optional;

/**
 * Result of evaluating a MEL statement against a memory engine.
 *
 * @param status         OK or ERROR
 * @param output         human-readable formatted output
 * @param elapsedMs      execution time in milliseconds
 * @param durationMicros execution time in microseconds
 * @param error          error message if status is ERROR
 * @param statementType  statement type name (e.g. REMEMBER, RECALL, CONSOLIDATE, FORGET, EXPLAIN_RECALL, INTROSPECT)
 * @param affectedIds    memory IDs created, recalled, or modified by this statement
 * @param candidates     structured recall candidate items (empty for non-recall statements)
 * @param explainRows    detailed per-candidate signal explanation rows
 * @param funnel         6-phase cascading filter funnel telemetry
 * @param diagnostics    syntax, parse, or semantic diagnostics with source positions
 */
public record MelResult(
        Status status,
        String output,
        long elapsedMs,
        long durationMicros,
        Optional<String> error,
        String statementType,
        List<String> affectedIds,
        List<MelCandidateItem> candidates,
        List<MelExplainRow> explainRows,
        FunnelTelemetry funnel,
        List<MelDiagnostic> diagnostics
) {
    public enum Status { OK, ERROR }

    public MelResult {
        affectedIds = affectedIds != null ? List.copyOf(affectedIds) : List.of();
        candidates = candidates != null ? List.copyOf(candidates) : List.of();
        explainRows = explainRows != null ? List.copyOf(explainRows) : List.of();
        diagnostics = diagnostics != null ? List.copyOf(diagnostics) : List.of();
        if (funnel == null) {
            funnel = FunnelTelemetry.empty();
        }
        if (statementType == null) {
            statementType = "UNKNOWN";
        }
    }

    /** Backward-compatible constructor without extended telemetry. */
    public MelResult(Status status, String output, long elapsedMs, Optional<String> error) {
        this(status, output, elapsedMs, elapsedMs * 1000L, error, "UNKNOWN",
             List.of(), List.of(), List.of(), FunnelTelemetry.empty(), List.of());
    }

    /** Creates a successful result with output and elapsed time. */
    public static MelResult ok(String output, long elapsedMs) {
        return new MelResult(Status.OK, output, elapsedMs, elapsedMs * 1000L, Optional.empty(),
                "UNKNOWN", List.of(), List.of(), List.of(), FunnelTelemetry.empty(), List.of());
    }

    /** Creates a successful rich result. */
    public static MelResult ok(String output, long elapsedMs, long durationMicros,
                              String statementType, List<String> affectedIds,
                              List<MelCandidateItem> candidates, List<MelExplainRow> explainRows,
                              FunnelTelemetry funnel, List<MelDiagnostic> diagnostics) {
        return new MelResult(Status.OK, output, elapsedMs, durationMicros, Optional.empty(),
                statementType, affectedIds, candidates, explainRows, funnel, diagnostics);
    }

    /** Creates an error result. */
    public static MelResult error(String message, long elapsedMs) {
        return new MelResult(Status.ERROR, "", elapsedMs, elapsedMs * 1000L, Optional.of(message),
                "UNKNOWN", List.of(), List.of(), List.of(), FunnelTelemetry.empty(), List.of());
    }

    /** Creates an error result with diagnostics. */
    public static MelResult errorWithDiagnostic(String message, long elapsedMs, long durationMicros,
                                                String statementType, List<MelDiagnostic> diagnostics) {
        return new MelResult(Status.ERROR, "", elapsedMs, durationMicros, Optional.of(message),
                statementType, List.of(), List.of(), List.of(), FunnelTelemetry.empty(), diagnostics);
    }

    /** Returns true if this result is successful. */
    public boolean isOk() {
        return status == Status.OK;
    }

    /**
     * Represents a single structured recall candidate item.
     */
    public record MelCandidateItem(
            int rank,
            String id,
            String tier,
            float finalScore,
            CandidateBreakdown scores,
            String content,
            List<String> tags,
            float importance,
            float valence,
            float arousal,
            float decayFactor,
            long createdAtMs,
            String source
    ) {
        public MelCandidateItem {
            tags = tags != null ? List.copyOf(tags) : List.of();
            if (source == null) source = "OBSERVED";
            if (scores == null) scores = CandidateBreakdown.empty();
        }

        public String memoryId() { return id; }
        public float score() { return finalScore; }
        public CandidateBreakdown breakdown() { return scores; }
    }

    /**
     * 7-signal score breakdown for a recall candidate.
     */
    public record CandidateBreakdown(
            float vectorSim,
            float recencyDecay,
            float cognitiveMass,
            float valenceBoost,
            float hebbianCoAct,
            float bm25Lexical,
            float tagMatchRatio
    ) {
        public static CandidateBreakdown empty() {
            return new CandidateBreakdown(0f, 0f, 0f, 0f, 0f, 0f, 0f);
        }
    }

    /**
     * Detailed explanation row for glass-box EXPLAIN RECALL matrix.
     */
    public record MelExplainRow(
            int rank,
            String memoryId,
            String tier,
            float fusedScore,
            float vectorSimilarity,
            float bm25Score,
            float recencyDecay,
            float cognitiveMass,
            float storageStrength,
            float valenceFactor,
            float hebbianBoost,
            float freeEnergyPenalty,
            String signal,
            float rawScore,
            float weight,
            float contribution
    ) {
        public MelExplainRow(
                int rank,
                String memoryId,
                String tier,
                float fusedScore,
                float vectorSimilarity,
                float bm25Score,
                float recencyDecay,
                float cognitiveMass,
                float storageStrength,
                float valenceFactor,
                float hebbianBoost,
                float freeEnergyPenalty
        ) {
            this(rank, memoryId, tier, fusedScore, vectorSimilarity, bm25Score,
                 recencyDecay, cognitiveMass, storageStrength, valenceFactor,
                 hebbianBoost, freeEnergyPenalty, "Composite Scoring", vectorSimilarity, 0.35f, vectorSimilarity * 0.35f);
        }

        public MelExplainRow(
                String memoryId,
                String signal,
                float rawScore,
                float weight,
                float contribution
        ) {
            this(1, memoryId, "UNKNOWN", rawScore * weight, rawScore, 0f, 1f, 1f, 1f, 1f, 0f, 0f,
                 signal, rawScore, weight, contribution);
        }
    }

    /**
     * 6-phase cascading filter funnel metrics.
     */
    public record FunnelTelemetry(
            int totalScanned,
            int phase1Alive,
            int phase2TagSurvivors,
            int phase3ValenceSurvivors,
            int phase4MassSurvivors,
            int phase5SimdEvaluated,
            int phase6FinalReturned
    ) {
        public static FunnelTelemetry empty() {
            return new FunnelTelemetry(0, 0, 0, 0, 0, 0, 0);
        }

        /**
         * Converts the 6-phase funnel into a list of descriptive phase items.
         */
        public List<FunnelPhase> toPhases() {
            return List.of(
                    new FunnelPhase("PHASE_1", "Tombstone & Contradiction Filter", totalScanned, phase1Alive, Math.max(0, totalScanned - phase1Alive)),
                    new FunnelPhase("PHASE_2", "Synaptic Tag Filter", phase1Alive, phase2TagSurvivors, Math.max(0, phase1Alive - phase2TagSurvivors)),
                    new FunnelPhase("PHASE_3", "Valence Range Bounds", phase2TagSurvivors, phase3ValenceSurvivors, Math.max(0, phase2TagSurvivors - phase3ValenceSurvivors)),
                    new FunnelPhase("PHASE_4", "Cognitive Mass Pre-Screen", phase3ValenceSurvivors, phase4MassSurvivors, Math.max(0, phase3ValenceSurvivors - phase4MassSurvivors)),
                    new FunnelPhase("PHASE_5", "SIMD Vector Evaluation", phase4MassSurvivors, phase5SimdEvaluated, Math.max(0, phase4MassSurvivors - phase5SimdEvaluated)),
                    new FunnelPhase("PHASE_6", "Top-K Selection", phase5SimdEvaluated, phase6FinalReturned, Math.max(0, phase5SimdEvaluated - phase6FinalReturned))
            );
        }
    }

    /**
     * Descriptive phase metric in the recall funnel.
     */
    public record FunnelPhase(
            String phase,
            String label,
            int evaluated,
            int survived,
            int pruned
    ) {}

    /**
     * Syntax or semantic diagnostic message with editor line and column coordinates.
     */
    public record MelDiagnostic(
            int line,
            int column,
            String message,
            String severity
    ) {}
}
