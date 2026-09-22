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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.spectrayan.spector.kernel.api.MemorySource;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.model.WhyNotExplanation;
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
     * Evaluates a single MEL statement.
     *
     * @param stmt the parsed statement
     * @return the evaluation result
     */
    public MelResult evaluate(MelStatement stmt) {
        long start = System.nanoTime();
        try {
            String output = switch (stmt) {
                case RememberStmt s -> evalRemember(s);
                case RecallStmt s -> evalRecall(s);
                case ConsolidateStmt s -> evalConsolidate(s);
                case ForgetStmt s -> evalForget(s);
                case ExplainRecallStmt s -> evalExplainRecall(s);
                case IntrospectStmt s -> evalIntrospect(s);
            };
            long elapsed = (System.nanoTime() - start) / 1_000_000;
            return MelResult.ok(output, elapsed);
        } catch (Exception e) {
            long elapsed = (System.nanoTime() - start) / 1_000_000;
            log.debug("MEL evaluation failed", e);
            return MelResult.error(e.getMessage(), elapsed);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Statement Evaluators
    // ═══════════════════════════════════════════════════════════════

    private String evalRemember(RememberStmt stmt) {
        var type = mapTier(stmt.tier());
        var tags = stmt.tags().toArray(String[]::new);

        // Use simplest overload: remember(text, type, source, tags...)
        var source = stmt.source()
                .map(s -> MemorySource.valueOf(s))
                .orElse(MemorySource.OBSERVED);

        String id = memory.remember(stmt.payloadValue(), type, source, tags);

        return "✅ Remembered as " + id + " into " + stmt.tier() + " tier"
                + (tags.length > 0 ? " with tags " + String.join(", ", tags) : "");
    }

    private String evalRecall(RecallStmt stmt) {
        String query = stmt.query().orElse("");
        int topK = stmt.topK();

        var options = RecallOptions.builder()
                .topK(topK);

        stmt.minImportance().ifPresent(v -> options.minImportance((float) v));

        var results = memory.recall(query, options.build());

        if (results.isEmpty()) {
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

    private String evalConsolidate(ConsolidateStmt stmt) {
        int beforeCount = memory.totalMemories();
        memory.consolidate();
        int afterCount = memory.totalMemories();

        return String.format("🧠 Consolidation completed.%n"
                + "  Memories before: %d%n"
                + "  Memories after:  %d%n"
                + "  Target tier: %s%n"
                + "  IDs requested: %s",
                beforeCount, afterCount, stmt.targetTier(),
                String.join(", ", stmt.ids()));
    }

    private String evalForget(ForgetStmt stmt) {
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
                    // Weaken lowers D without affecting S — approximate with zero-valence reinforce
                    memory.reinforce(id, (byte) 0);
                    sb.append("📉 Weakened: ").append(id).append('\n');
                }
            }
        }
        return sb.toString().stripTrailing();
    }

    private String evalExplainRecall(ExplainRecallStmt stmt) {
        RecallStmt inner = stmt.inner();
        String query = inner.query().orElse("");
        int topK = inner.topK();

        var options = RecallOptions.builder().topK(topK);
        inner.minImportance().ifPresent(v -> options.minImportance((float) v));

        var results = memory.recall(query, options.build());

        var sb = new StringBuilder();
        sb.append(String.format("EXPLAIN RECALL '%s' TOP %d%n%n", query, topK));

        if (results.isEmpty()) {
            sb.append("No results found — no scores to explain.\n");
            return sb.toString();
        }

        int rank = 1;
        for (var result : results) {
            sb.append(String.format("── Hit #%d ──%n", rank++));
            sb.append(String.format("  ID:      %s%n", result.id()));
            sb.append(String.format("  Score:   %.6f%n", result.score()));
            sb.append(String.format("  Tier:    %s%n", result.memoryType() != null ? result.memoryType().name() : "?"));
            sb.append(String.format("  Content: %s%n", truncate(result.text(), 60)));

            // Glass-box breakdown via whyNot (shows per-signal scores)
            try {
                WhyNotExplanation explanation = memory.whyNot(result.id(), query, options.build());
                if (explanation != null) {
                    sb.append(String.format("  Breakdown: %s%n", explanation));
                }
            } catch (Exception e) {
                sb.append(String.format("  Breakdown: (unavailable)%n"));
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private String evalIntrospect(IntrospectStmt stmt) {
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

        // If a topic/rememberer was specified, do an introspect query
        if (stmt.remembererId().isPresent()) {
            sb.append(String.format("%n  Rememberer filter: %s%n", stmt.remembererId().get()));
        }
        if (stmt.tier().isPresent()) {
            sb.append(String.format("  Tier filter: %s%n", stmt.tier().get()));
        }

        return sb.toString();
    }

    // ═══════════════════════════════════════════════════════════════
    //  Helpers
    // ═══════════════════════════════════════════════════════════════

    private static MemoryType mapTier(String tier) {
        return switch (tier.toUpperCase()) {
            case "WORKING" -> MemoryType.WORKING;
            case "EPISODIC" -> MemoryType.EPISODIC;
            case "SEMANTIC" -> MemoryType.SEMANTIC;
            case "PROCEDURAL" -> MemoryType.PROCEDURAL;
            default -> MemoryType.SEMANTIC;
        };
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }
}
