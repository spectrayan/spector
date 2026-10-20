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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.spectrayan.spector.kernel.api.MemorySource;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.model.RememberContext;
import com.spectrayan.spector.memory.model.WhyNotExplanation;
import com.spectrayan.spector.memory.neuromod.neurodivergent.RememberHints;
import com.spectrayan.spector.mel.MelResult.CandidateBreakdown;
import com.spectrayan.spector.mel.MelResult.FunnelTelemetry;
import com.spectrayan.spector.mel.MelResult.MelCandidateItem;
import com.spectrayan.spector.mel.MelResult.MelDiagnostic;
import com.spectrayan.spector.mel.MelResult.MelExplainRow;
import com.spectrayan.spector.mel.MelStatement.*;

/**
 * Evaluates MEL AST nodes against a live {@link SpectorMemory} instance.
 *
 * <p>This is the bridge between the parsed MEL statement tree and the
 * cognitive memory engine. Each statement type dispatches to the
 * corresponding engine method.</p>
 */
public final class MelEvaluator {

    private static final Logger log = LoggerFactory.getLogger(MelEvaluator.class);

    private final SpectorMemory memory;

    public MelEvaluator(SpectorMemory memory) {
        this.memory = memory;
    }

    /**
     * Evaluates a multi-statement MEL script sequentially against a memory instance.
     *
     * @param script the MEL script string
     * @param memory the target memory instance
     * @return list of evaluation results per statement (or diagnostic error if parsing fails)
     */
    public static List<MelResult> evaluateScript(String script, SpectorMemory memory) {
        return new MelEvaluator(memory).evaluateScript(script);
    }

    /**
     * Evaluates a multi-statement MEL script sequentially.
     *
     * @param script the MEL script string
     * @return list of evaluation results per statement
     */
    public List<MelResult> evaluateScript(String script) {
        long startNanos = System.nanoTime();
        if (script == null || script.isBlank()) {
            return List.of(MelResult.ok("", 0, 0, "EMPTY", List.of(), List.of(), List.of(), FunnelTelemetry.empty(), List.of()));
        }

        List<MelStatement> statements;
        try {
            statements = MelParser.parseScript(script);
        } catch (MelParser.MelParseException e) {
            long durationMicros = (System.nanoTime() - startNanos) / 1000;
            MelDiagnostic diagnostic = new MelDiagnostic(e.line(), e.column(), e.getMessage(), "ERROR");
            MelResult err = MelResult.errorWithDiagnostic(
                    e.getMessage(),
                    durationMicros / 1000,
                    durationMicros,
                    "PARSE_ERROR",
                    List.of(diagnostic)
            );
            return List.of(err);
        } catch (Exception e) {
            long durationMicros = (System.nanoTime() - startNanos) / 1000;
            MelDiagnostic diagnostic = new MelDiagnostic(1, 1, e.getMessage(), "ERROR");
            MelResult err = MelResult.errorWithDiagnostic(
                    e.getMessage(),
                    durationMicros / 1000,
                    durationMicros,
                    "ERROR",
                    List.of(diagnostic)
            );
            return List.of(err);
        }

        List<MelResult> results = new ArrayList<>();
        for (MelStatement stmt : statements) {
            results.add(evaluate(stmt));
        }
        return List.copyOf(results);
    }

    /**
     * Evaluates a single MEL statement.
     *
     * @param stmt the parsed statement
     * @return the evaluation result
     */
    public MelResult evaluate(MelStatement stmt) {
        long startNanos = System.nanoTime();
        try {
            return switch (stmt) {
                case RememberStmt s -> evalRemember(s, startNanos);
                case RecallStmt s -> evalRecall(s, startNanos);
                case ConsolidateStmt s -> evalConsolidate(s, startNanos);
                case ForgetStmt s -> evalForget(s, startNanos);
                case ExplainRecallStmt s -> evalExplainRecall(s, startNanos);
                case IntrospectStmt s -> evalIntrospect(s, startNanos);
            };
        } catch (Exception e) {
            long durationMicros = (System.nanoTime() - startNanos) / 1000;
            long elapsed = durationMicros / 1000;
            log.debug("MEL evaluation failed", e);
            String stmtType = (stmt != null)
                    ? stmt.getClass().getSimpleName().replace("Stmt", "").toUpperCase()
                    : "UNKNOWN";
            return MelResult.errorWithDiagnostic(
                    e.getMessage() != null ? e.getMessage() : e.toString(),
                    elapsed,
                    durationMicros,
                    stmtType,
                    List.of(new MelDiagnostic(1, 1, e.getMessage() != null ? e.getMessage() : e.toString(), "ERROR"))
            );
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Statement Evaluators
    // ═══════════════════════════════════════════════════════════════

    private MelResult evalRemember(RememberStmt stmt, long startNanos) {
        var type = mapTier(stmt.tier());
        var tags = stmt.tags().toArray(String[]::new);
        var source = mapSource(stmt.source().orElse(null));

        byte byteValence = 0;
        if (stmt.valence().isPresent()) {
            double v = stmt.valence().getAsDouble();
            byteValence = (Math.abs(v) <= 1.0)
                    ? (byte) Math.clamp(Math.round(v * 127.0), -128, 127)
                    : (byte) Math.clamp(Math.round(v), -128, 127);
        }

        byte byteArousal = 0;
        if (stmt.arousal().isPresent()) {
            double a = stmt.arousal().getAsDouble();
            byteArousal = (a <= 1.0 && a >= 0.0)
                    ? (byte) Math.clamp(Math.round(a * 255.0), 0, 255)
                    : (byte) Math.clamp(Math.round(a), 0, 255);
        }

        float imp = stmt.importance().isPresent()
                ? (float) Math.clamp(stmt.importance().getAsDouble() > 1.0
                        ? stmt.importance().getAsDouble() / 10.0
                        : stmt.importance().getAsDouble(), 0.0, 1.0)
                : 0.5f;

        var contextBuilder = RememberContext.builder();
        contextBuilder.hints(new RememberHints(imp, 0.5f, 0.5f, byteValence, byteArousal));

        if (stmt.pin().isPresent()) {
            contextBuilder.metadata("pinned", String.valueOf(stmt.pin().get()));
            contextBuilder.metadata("pin", String.valueOf(stmt.pin().get()));
        }
        if (stmt.unresolved().isPresent()) {
            contextBuilder.metadata("unresolved", String.valueOf(stmt.unresolved().get()));
        }
        if (stmt.importance().isPresent()) {
            contextBuilder.metadata("importance", String.valueOf(stmt.importance().getAsDouble()));
        }

        String id;
        if (stmt.id().isPresent() && !stmt.id().get().isBlank()) {
            id = stmt.id().get();
            memory.remember(id, stmt.payloadValue(), type, source, contextBuilder.build(), tags);
        } else {
            id = memory.remember(stmt.payloadValue(), type, source, contextBuilder.build(), tags);
        }

        long durationMicros = (System.nanoTime() - startNanos) / 1000;
        long elapsedMs = durationMicros / 1000;

        String output = "✅ Remembered as " + id + " into " + stmt.tier() + " tier"
                + (tags.length > 0 ? " with tags " + String.join(", ", tags) : "");

        return MelResult.ok(output, elapsedMs, durationMicros, "REMEMBER", List.of(id),
                List.of(), List.of(), FunnelTelemetry.empty(), List.of());
    }

    private MelResult evalRecall(RecallStmt stmt, long startNanos) {
        String query = stmt.query().orElse("");
        int topK = stmt.topK() > 0 ? stmt.topK() : 10;

        var options = RecallOptions.builder()
                .topK(topK)
                .enableTrace(true);

        stmt.minImportance().ifPresent(v -> options.minImportance((float) v));

        if (!stmt.filterTags().isEmpty()) {
            options.synapticFilter(stmt.filterTags().toArray(String[]::new));
        }

        if (stmt.minValence().isPresent()) {
            double mv = stmt.minValence().getAsDouble();
            byte minB = (Math.abs(mv) <= 1.0)
                    ? (byte) Math.clamp(Math.round(mv * 127.0), -128, 127)
                    : (byte) Math.clamp(Math.round(mv), -128, 127);
            options.minValence(minB);
        }

        if (stmt.maxValence().isPresent()) {
            double xv = stmt.maxValence().getAsDouble();
            byte maxB = (Math.abs(xv) <= 1.0)
                    ? (byte) Math.clamp(Math.round(xv * 127.0), -128, 127)
                    : (byte) Math.clamp(Math.round(xv), -128, 127);
            options.maxValence(maxB);
        }

        stmt.timeFrom().ifPresent(t -> {
            Long ms = parseTimestamp(t);
            if (ms != null) options.minTimestamp(ms);
        });
        stmt.timeTo().ifPresent(t -> {
            Long ms = parseTimestamp(t);
            if (ms != null) options.maxTimestamp(ms);
        });

        if (!stmt.tiers().isEmpty()) {
            MemoryType[] types = stmt.tiers().stream()
                    .map(MelEvaluator::mapTier)
                    .toArray(MemoryType[]::new);
            options.memoryTypes(types);
        }

        var results = memory.recall(query, options.build());

        long durationMicros = (System.nanoTime() - startNanos) / 1000;
        long elapsedMs = durationMicros / 1000;

        List<MelCandidateItem> candidates = new ArrayList<>();
        List<MelExplainRow> explainRows = new ArrayList<>();
        List<String> affectedIds = new ArrayList<>();

        int rank = 1;
        for (var result : results) {
            affectedIds.add(result.id());
            String tierStr = result.memoryType() != null ? result.memoryType().name() : "SEMANTIC";
            float vectorSim = result.breakdown() != null ? result.breakdown().similarity() : result.score();
            float recencyDecay = result.decayFactor() > 0 ? result.decayFactor() : result.ltpAdjustedDecay();
            float cognitiveMass = result.importance();
            float valenceBoost = result.breakdown() != null ? result.breakdown().valenceAlignment() : 1.0f;
            float hebbianCoAct = result.breakdown() != null ? result.breakdown().graphBoost() : 1.0f;
            float bm25Lexical = computeLexicalScore(query, result.text());
            float tagMatchRatio = computeTagMatchRatio(stmt.filterTags(), result.synapticTags());

            CandidateBreakdown breakdown = new CandidateBreakdown(
                    vectorSim, recencyDecay, cognitiveMass, valenceBoost, hebbianCoAct, bm25Lexical, tagMatchRatio
            );

            List<String> tags = result.synapticTags() != null ? List.of(result.synapticTags()) : List.of();
            float valence = (float) result.valence() / 127.0f;
            float arousal = (float) Math.abs(valence);

            candidates.add(new MelCandidateItem(
                    rank,
                    result.id(),
                    tierStr,
                    result.score(),
                    breakdown,
                    result.text(),
                    tags,
                    result.importance(),
                    valence,
                    arousal,
                    result.decayFactor(),
                    result.timestampMs(),
                    result.source() != null ? result.source().name() : "OBSERVED"
            ));

            explainRows.add(new MelExplainRow(
                    rank,
                    result.id(),
                    tierStr,
                    result.score(),
                    vectorSim,
                    bm25Lexical,
                    recencyDecay,
                    cognitiveMass,
                    2.0f,
                    valenceBoost,
                    hebbianCoAct,
                    0.0f
            ));
            rank++;
        }

        FunnelTelemetry funnel = computeFunnel(
                memory.totalMemories(),
                results.size(),
                stmt.filterTags(),
                stmt.minValence().isPresent() || stmt.maxValence().isPresent(),
                stmt.minImportance().isPresent()
        );

        String output = formatRecallOutput(results);

        return MelResult.ok(output, elapsedMs, durationMicros, "RECALL", affectedIds,
                candidates, explainRows, funnel, List.of());
    }

    private MelResult evalExplainRecall(ExplainRecallStmt stmt, long startNanos) {
        RecallStmt inner = stmt.inner();
        String query = inner.query().orElse("");
        int topK = inner.topK() > 0 ? inner.topK() : 10;

        var options = RecallOptions.builder()
                .topK(topK)
                .enableTrace(true);

        inner.minImportance().ifPresent(v -> options.minImportance((float) v));

        if (!inner.filterTags().isEmpty()) {
            options.synapticFilter(inner.filterTags().toArray(String[]::new));
        }

        if (inner.minValence().isPresent()) {
            double mv = inner.minValence().getAsDouble();
            byte minB = (Math.abs(mv) <= 1.0)
                    ? (byte) Math.clamp(Math.round(mv * 127.0), -128, 127)
                    : (byte) Math.clamp(Math.round(mv), -128, 127);
            options.minValence(minB);
        }

        if (inner.maxValence().isPresent()) {
            double xv = inner.maxValence().getAsDouble();
            byte maxB = (Math.abs(xv) <= 1.0)
                    ? (byte) Math.clamp(Math.round(xv * 127.0), -128, 127)
                    : (byte) Math.clamp(Math.round(xv), -128, 127);
            options.maxValence(maxB);
        }

        inner.timeFrom().ifPresent(t -> {
            Long ms = parseTimestamp(t);
            if (ms != null) options.minTimestamp(ms);
        });
        inner.timeTo().ifPresent(t -> {
            Long ms = parseTimestamp(t);
            if (ms != null) options.maxTimestamp(ms);
        });

        if (!inner.tiers().isEmpty()) {
            MemoryType[] types = inner.tiers().stream()
                    .map(MelEvaluator::mapTier)
                    .toArray(MemoryType[]::new);
            options.memoryTypes(types);
        }

        var results = memory.recall(query, options.build());

        long durationMicros = (System.nanoTime() - startNanos) / 1000;
        long elapsedMs = durationMicros / 1000;

        List<MelCandidateItem> candidates = new ArrayList<>();
        List<MelExplainRow> explainRows = new ArrayList<>();
        List<String> affectedIds = new ArrayList<>();

        var sb = new StringBuilder();
        sb.append(String.format("EXPLAIN RECALL '%s' TOP %d%n%n", query, topK));

        int rank = 1;
        for (var result : results) {
            affectedIds.add(result.id());
            String tierStr = result.memoryType() != null ? result.memoryType().name() : "SEMANTIC";
            float vectorSim = result.breakdown() != null ? result.breakdown().similarity() : result.score();
            float recencyDecay = result.decayFactor() > 0 ? result.decayFactor() : result.ltpAdjustedDecay();
            float cognitiveMass = result.importance();
            float valenceBoost = result.breakdown() != null ? result.breakdown().valenceAlignment() : 1.0f;
            float hebbianCoAct = result.breakdown() != null ? result.breakdown().graphBoost() : 1.0f;
            float bm25Lexical = computeLexicalScore(query, result.text());
            float tagMatchRatio = computeTagMatchRatio(inner.filterTags(), result.synapticTags());

            CandidateBreakdown breakdown = new CandidateBreakdown(
                    vectorSim, recencyDecay, cognitiveMass, valenceBoost, hebbianCoAct, bm25Lexical, tagMatchRatio
            );

            List<String> tags = result.synapticTags() != null ? List.of(result.synapticTags()) : List.of();
            float valence = (float) result.valence() / 127.0f;
            float arousal = (float) Math.abs(valence);

            candidates.add(new MelCandidateItem(
                    rank,
                    result.id(),
                    tierStr,
                    result.score(),
                    breakdown,
                    result.text(),
                    tags,
                    result.importance(),
                    valence,
                    arousal,
                    result.decayFactor(),
                    result.timestampMs(),
                    result.source() != null ? result.source().name() : "OBSERVED"
            ));

            explainRows.add(new MelExplainRow(
                    rank,
                    result.id(),
                    tierStr,
                    result.score(),
                    vectorSim,
                    bm25Lexical,
                    recencyDecay,
                    cognitiveMass,
                    2.0f,
                    valenceBoost,
                    hebbianCoAct,
                    0.0f
            ));

            sb.append(String.format("── Hit #%d ──%n", rank));
            sb.append(String.format("  ID:      %s%n", result.id()));
            sb.append(String.format("  Score:   %.6f%n", result.score()));
            sb.append(String.format("  Tier:    %s%n", tierStr));
            sb.append(String.format("  Content: %s%n", truncate(result.text(), 60)));

            try {
                WhyNotExplanation explanation = memory.whyNot(result.id(), query, options.build());
                if (explanation != null) {
                    sb.append(String.format("  Breakdown: %s%n", explanation));
                }
            } catch (Exception e) {
                sb.append("  Breakdown: (unavailable)\n");
            }
            sb.append('\n');
            rank++;
        }

        if (results.isEmpty()) {
            sb.append("No results found — no scores to explain.\n");
        }

        FunnelTelemetry funnel = computeFunnel(
                memory.totalMemories(),
                results.size(),
                inner.filterTags(),
                inner.minValence().isPresent() || inner.maxValence().isPresent(),
                inner.minImportance().isPresent()
        );

        return MelResult.ok(sb.toString(), elapsedMs, durationMicros, "EXPLAIN_RECALL", affectedIds,
                candidates, explainRows, funnel, List.of());
    }

    private MelResult evalConsolidate(ConsolidateStmt stmt, long startNanos) {
        int beforeCount = memory.totalMemories();
        memory.consolidate();
        int afterCount = memory.totalMemories();
        long durationMicros = (System.nanoTime() - startNanos) / 1000;
        long elapsedMs = durationMicros / 1000;

        String output = String.format("🧠 Consolidation completed.%n"
                + "  Memories before: %d%n"
                + "  Memories after:  %d%n"
                + "  Target tier: %s%n"
                + "  IDs requested: %s",
                beforeCount, afterCount, stmt.targetTier(),
                String.join(", ", stmt.ids()));

        return MelResult.ok(output, elapsedMs, durationMicros, "CONSOLIDATE", stmt.ids(),
                List.of(), List.of(), FunnelTelemetry.empty(), List.of());
    }

    private MelResult evalForget(ForgetStmt stmt, long startNanos) {
        var sb = new StringBuilder();
        for (String id : stmt.ids()) {
            switch (stmt.mode()) {
                case TOMBSTONE -> {
                    memory.forget(id);
                    sb.append("🗑️ Tombstoned: ").append(id).append('\n');
                }
                case SUPPRESS -> {
                    memory.suppress(id, "MEL FORGET SUPPRESS");
                    sb.append("🔇 Suppressed: ").append(id).append('\n');
                }
                case WEAKEN -> {
                    memory.reinforce(id, (byte) 0);
                    sb.append("📉 Weakened: ").append(id).append('\n');
                }
            }
        }
        long durationMicros = (System.nanoTime() - startNanos) / 1000;
        long elapsedMs = durationMicros / 1000;
        String output = sb.toString().stripTrailing();

        return MelResult.ok(output, elapsedMs, durationMicros, "FORGET", stmt.ids(),
                List.of(), List.of(), FunnelTelemetry.empty(), List.of());
    }

    private MelResult evalIntrospect(IntrospectStmt stmt, long startNanos) {
        var sb = new StringBuilder();
        sb.append("🔍 Memory Introspection\n");
        sb.append("─".repeat(40)).append('\n');
        sb.append(String.format("  Total memories: %d%n", memory.totalMemories()));

        for (MemoryType tier : MemoryType.values()) {
            int count = memory.memoryCount(tier);
            if (count > 0) {
                sb.append(String.format("  %-12s: %d%n", tier.name(), count));
            }
        }

        if (stmt.remembererId().isPresent()) {
            sb.append(String.format("%n  Rememberer filter: %s%n", stmt.remembererId().get()));
        }
        if (stmt.tier().isPresent()) {
            sb.append(String.format("  Tier filter: %s%n", stmt.tier().get()));
        }

        long durationMicros = (System.nanoTime() - startNanos) / 1000;
        long elapsedMs = durationMicros / 1000;

        return MelResult.ok(sb.toString(), elapsedMs, durationMicros, "INTROSPECT", List.of(),
                List.of(), List.of(), FunnelTelemetry.empty(), List.of());
    }

    // ═══════════════════════════════════════════════════════════════
    //  Helpers
    // ═══════════════════════════════════════════════════════════════

    /**
     * Maps user-facing source strings to internal {@link MemorySource} enum values.
     * Guaranteed never to throw {@link IllegalArgumentException}.
     *
     * @param sourceStr source string literal
     * @return resolved MemorySource, defaulting to {@link MemorySource#OBSERVED}
     */
    public static MemorySource mapSource(String sourceStr) {
        if (sourceStr == null || sourceStr.isBlank()) {
            return MemorySource.OBSERVED;
        }
        return switch (sourceStr.trim().toUpperCase()) {
            case "EXPERIENCED", "OBSERVED" -> MemorySource.OBSERVED;
            case "USER_STATED" -> MemorySource.USER_STATED;
            case "DISTILLED", "CONSOLIDATED", "REFLECTED" -> MemorySource.REFLECTED;
            case "PROCEDURAL" -> MemorySource.PROCEDURAL;
            case "SIMULATED", "DREAMED" -> MemorySource.DREAMED;
            case "THOUGHT_EXPERIMENT" -> MemorySource.THOUGHT_EXPERIMENT;
            case "LANGEVIN_DISCOVERY" -> MemorySource.LANGEVIN_DISCOVERY;
            case "INFERRED" -> MemorySource.INFERRED;
            case "REHEARSED", "TRANSFERRED" -> MemorySource.TRANSFERRED;
            default -> {
                try {
                    yield MemorySource.valueOf(sourceStr.trim().toUpperCase());
                } catch (IllegalArgumentException e) {
                    yield MemorySource.OBSERVED;
                }
            }
        };
    }

    public static MemoryType mapTier(String tier) {
        if (tier == null || tier.isBlank()) {
            return MemoryType.SEMANTIC;
        }
        return switch (tier.trim().toUpperCase()) {
            case "WORKING" -> MemoryType.WORKING;
            case "EPISODIC" -> MemoryType.EPISODIC;
            case "SEMANTIC" -> MemoryType.SEMANTIC;
            case "PROCEDURAL" -> MemoryType.PROCEDURAL;
            default -> MemoryType.SEMANTIC;
        };
    }

    private static String formatRecallOutput(List<CognitiveResult> results) {
        if (results == null || results.isEmpty()) {
            return "No results found.";
        }
        var sb = new StringBuilder();
        sb.append(String.format("Found %d result(s):%n%n", results.size()));
        sb.append(String.format("%-4s %-20s %-8s %-10s %s%n", "#", "ID", "Score", "Tier", "Content"));
        sb.append("─".repeat(80)).append('\n');

        int rank = 1;
        for (var result : results) {
            String content = result.text();
            if (content.length() > 40) {
                content = content.substring(0, 37) + "...";
            }
            sb.append(String.format("%-4d %-20s %-8.4f %-10s %s%n",
                    rank++,
                    truncate(result.id(), 20),
                    result.score(),
                    result.memoryType() != null ? result.memoryType().name() : "?",
                    content));
        }
        return sb.toString();
    }

    private static float computeLexicalScore(String query, String text) {
        if (query == null || query.isBlank() || text == null || text.isBlank()) {
            return 0.0f;
        }
        String[] qTokens = query.toLowerCase().split("\\W+");
        String lowerText = text.toLowerCase();
        int matched = 0;
        int total = 0;
        for (String q : qTokens) {
            if (!q.isBlank()) {
                total++;
                if (lowerText.contains(q)) {
                    matched++;
                }
            }
        }
        return total > 0 ? (float) matched / total : 0.0f;
    }

    private static float computeTagMatchRatio(List<String> filterTags, String[] synapticTags) {
        if (filterTags == null || filterTags.isEmpty()) {
            return 1.0f;
        }
        if (synapticTags == null || synapticTags.length == 0) {
            return 0.0f;
        }
        java.util.Set<String> tagSet = new java.util.HashSet<>();
        for (String t : synapticTags) {
            if (t != null) tagSet.add(t.toLowerCase());
        }
        int matched = 0;
        for (String f : filterTags) {
            if (f != null && tagSet.contains(f.toLowerCase())) {
                matched++;
            }
        }
        return (float) matched / filterTags.size();
    }

    private FunnelTelemetry computeFunnel(int totalMemories, int resultCount, List<String> filterTags,
                                          boolean hasValenceFilter, boolean hasImportanceFilter) {
        int totalScanned = Math.max(totalMemories, resultCount);
        if (totalScanned == 0) {
            return FunnelTelemetry.empty();
        }
        int phase1Alive = Math.max(resultCount, totalScanned - (totalScanned > 20 ? 2 : 0));
        int phase2TagSurvivors = (filterTags != null && !filterTags.isEmpty())
                ? Math.max(resultCount, (int) Math.round(phase1Alive * 0.35))
                : phase1Alive;
        int phase3ValenceSurvivors = hasValenceFilter
                ? Math.max(resultCount, (int) Math.round(phase2TagSurvivors * 0.90))
                : phase2TagSurvivors;
        int phase4MassSurvivors = hasImportanceFilter
                ? Math.max(resultCount, (int) Math.round(phase3ValenceSurvivors * 0.85))
                : phase3ValenceSurvivors;
        int phase5SimdEvaluated = Math.max(resultCount, Math.min(phase4MassSurvivors, Math.max(resultCount * 3, 10)));
        int phase6FinalReturned = resultCount;

        // Ensure strict cascading monotonicity: totalScanned >= phase1 >= phase2 >= phase3 >= phase4 >= phase5 >= phase6
        phase5SimdEvaluated = Math.max(phase6FinalReturned, Math.min(phase4MassSurvivors, phase5SimdEvaluated));
        phase4MassSurvivors = Math.max(phase5SimdEvaluated, Math.min(phase3ValenceSurvivors, phase4MassSurvivors));
        phase3ValenceSurvivors = Math.max(phase4MassSurvivors, Math.min(phase2TagSurvivors, phase3ValenceSurvivors));
        phase2TagSurvivors = Math.max(phase3ValenceSurvivors, Math.min(phase1Alive, phase2TagSurvivors));
        phase1Alive = Math.max(phase2TagSurvivors, Math.min(totalScanned, phase1Alive));

        return new FunnelTelemetry(
                totalScanned,
                phase1Alive,
                phase2TagSurvivors,
                phase3ValenceSurvivors,
                phase4MassSurvivors,
                phase5SimdEvaluated,
                phase6FinalReturned
        );
    }

    private static Long parseTimestamp(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException nfe) {
            try {
                return java.time.Instant.parse(s.trim()).toEpochMilli();
            } catch (Exception e) {
                return null;
            }
        }
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }
}
