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
package com.spectrayan.spector.synapse.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;
import com.spectrayan.spector.mel.MelEvaluator;
import com.spectrayan.spector.mel.MelParser;
import com.spectrayan.spector.mel.MelResult;
import com.spectrayan.spector.mel.MelResult.FunnelPhase;
import com.spectrayan.spector.mel.MelResult.FunnelTelemetry;
import com.spectrayan.spector.mel.MelResult.MelCandidateItem;
import com.spectrayan.spector.mel.MelResult.MelDiagnostic;
import com.spectrayan.spector.mel.MelResult.MelExplainRow;
import com.spectrayan.spector.mel.MelStatement;
import com.spectrayan.spector.mel.MelStatement.ConsolidateStmt;
import com.spectrayan.spector.mel.MelStatement.ExplainRecallStmt;
import com.spectrayan.spector.mel.MelStatement.ForgetStmt;
import com.spectrayan.spector.mel.MelStatement.IntrospectStmt;
import com.spectrayan.spector.mel.MelStatement.RecallStmt;
import com.spectrayan.spector.mel.MelStatement.RememberStmt;
import com.spectrayan.spector.synapse.catalog.GrantRole;
import com.spectrayan.spector.synapse.memory.MemoryBinding;
import com.spectrayan.spector.synapse.memory.MemoryRequestBinder;

/**
 * REST controller for the MEL (Memory Engine Language) Studio workbench.
 *
 * <p>Provides endpoints to execute MEL statements and multi-statement scripts,
 * perform live syntax validation for editor squiggles, and discover language
 * schema keywords, clause templates, and snippet library items.</p>
 *
 * <p>Enforces tenant and namespace isolation via {@link MemoryRequestBinder} and
 * role-based access control via {@link GrantRole}. Virtual-thread safe using
 * {@link ReentrantLock} instead of synchronized blocks.</p>
 */
@RestController
@RequestMapping("/api/v1/mel")
@Tag(name = "MEL Studio", description = "Memory Engine Language execution, validation, and schema workbench")
public class MelController {

    private static final Logger log = LoggerFactory.getLogger(MelController.class);

    private final MemoryRequestBinder binder;
    private final ReentrantLock lock = new ReentrantLock();

    @Autowired
    public MelController(MemoryRequestBinder binder) {
        this.binder = Objects.requireNonNull(binder, "binder must not be null");
    }

    // ═══════════════════════════════════════════════════════════════
    //  API Endpoints
    // ═══════════════════════════════════════════════════════════════

    /**
     * Executes a single statement or multi-statement MEL script against the caller's bound namespace.
     */
    @PostMapping(value = "/execute", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Execute MEL script", description = "Executes one or more MEL statements bound to the caller's namespace")
    public ResponseEntity<MelExecuteResponse> execute(
            @RequestBody MelExecuteRequest request,
            Authentication auth
    ) {
        long startNanos = System.nanoTime();
        if (request == null || request.script() == null || request.script().isBlank()) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_NULL, "script");
        }

        String targetNamespace = (request.namespace() != null && !request.namespace().isBlank())
                ? request.namespace().trim()
                : null;

        Authentication effectiveAuth = (auth != null) ? auth : SecurityContextHolder.getContext().getAuthentication();

        List<MelStatement> statements;
        try {
            statements = MelParser.parseScript(request.script());
        } catch (MelParser.MelParseException e) {
            long durationMicros = (System.nanoTime() - startNanos) / 1000;
            MelDiagnostic diag = new MelDiagnostic(e.line(), e.column(), e.getMessage(), "ERROR");
            return ResponseEntity.ok(MelExecuteResponse.fromError(
                    e.getMessage(),
                    durationMicros,
                    targetNamespace != null ? targetNamespace : "default",
                    List.of(diag)
            ));
        }

        MemoryBinding binding = binder.bind(effectiveAuth, Optional.ofNullable(targetNamespace));
        try {
            GrantRole callerRole = (binding.context() != null && binding.context().role() != null)
                    ? binding.context().role()
                    : GrantRole.OWNER;

            boolean hasMutatingStatements = statements.stream().anyMatch(s ->
                    s instanceof RememberStmt || s instanceof ConsolidateStmt || s instanceof ForgetStmt
            );

            if (hasMutatingStatements) {
                if (!callerRole.isAtLeast(GrantRole.WRITER)) {
                    log.warn("[MelController] Denying mutating MEL statement: account '{}' has role '{}' in namespace '{}' (requires WRITER)",
                            binding.accountId(), callerRole, binding.namespaceId());
                    throw new AccessDeniedException("Write permission (GrantRole.WRITER) required for mutating MEL statements.");
                }
            } else {
                if (!callerRole.isAtLeast(GrantRole.READER)) {
                    log.warn("[MelController] Denying MEL statement: account '{}' has role '{}' in namespace '{}' (requires READER)",
                            binding.accountId(), callerRole, binding.namespaceId());
                    throw new AccessDeniedException("Read permission (GrantRole.READER) required for MEL execution.");
                }
            }

            lock.lock();
            try {
                MelEvaluator evaluator = new MelEvaluator(binding.memory());
                List<MelResult> statementResults = new ArrayList<>();
                for (MelStatement stmt : statements) {
                    statementResults.add(evaluator.evaluate(stmt));
                }

                long totalDurationMicros = (System.nanoTime() - startNanos) / 1000;
                MelExecuteResponse response = MelExecuteResponse.fromResults(
                        statementResults,
                        totalDurationMicros,
                        binding.slug() != null ? binding.slug() : binding.namespaceId()
                );
                return ResponseEntity.ok(response);
            } finally {
                lock.unlock();
            }
        } finally {
            binder.unbind(binding);
        }
    }

    /**
     * Performs a dry-run parse check on a MEL script, returning statement summaries and exact diagnostics.
     */
    @PostMapping(value = "/validate", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Validate MEL script syntax", description = "Validates MEL script syntax and returns exact line/column diagnostics for editor squiggles")
    public ResponseEntity<MelValidateResponse> validate(@RequestBody MelValidateRequest request) {
        if (request == null || request.script() == null || request.script().isBlank()) {
            return ResponseEntity.ok(new MelValidateResponse(true, 0, List.of(), List.of()));
        }

        try {
            List<MelStatement> statements = MelParser.parseScript(request.script());
            List<MelStatementSummary> summaries = new ArrayList<>();
            for (MelStatement stmt : statements) {
                String type = resolveStatementType(stmt);
                String summary = summarizeStatement(stmt);
                summaries.add(new MelStatementSummary(type, summary, 1, 1));
            }
            return ResponseEntity.ok(new MelValidateResponse(true, summaries.size(), summaries, List.of()));
        } catch (MelParser.MelParseException e) {
            MelDiagnostic diag = new MelDiagnostic(e.line(), e.column(), e.getMessage(), "ERROR");
            return ResponseEntity.ok(new MelValidateResponse(false, 0, List.of(), List.of(diag)));
        } catch (Exception e) {
            MelDiagnostic diag = new MelDiagnostic(1, 1, e.getMessage(), "ERROR");
            return ResponseEntity.ok(new MelValidateResponse(false, 0, List.of(), List.of(diag)));
        }
    }

    /**
     * Returns the MEL language keywords, tiers, sources, clause templates, and snippet library.
     */
    @GetMapping(value = "/schema", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get MEL language schema", description = "Returns MEL language keywords, tiers, sources, clause templates, and snippet library definitions")
    public ResponseEntity<MelSchemaResponse> schema() {
        return ResponseEntity.ok(MelSchemaResponse.standard());
    }

    // ═══════════════════════════════════════════════════════════════
    //  Internal Helpers
    // ═══════════════════════════════════════════════════════════════

    private static String resolveStatementType(MelStatement stmt) {
        return switch (stmt) {
            case RememberStmt ignored -> "REMEMBER";
            case RecallStmt ignored -> "RECALL";
            case ConsolidateStmt ignored -> "CONSOLIDATE";
            case ForgetStmt ignored -> "FORGET";
            case ExplainRecallStmt ignored -> "EXPLAIN_RECALL";
            case IntrospectStmt ignored -> "INTROSPECT";
        };
    }

    private static String summarizeStatement(MelStatement stmt) {
        return switch (stmt) {
            case RememberStmt s -> "REMEMBER " + s.payloadType() + " INTO " + s.tier();
            case RecallStmt s -> "RECALL " + (s.query().isPresent() ? "'" + s.query().get() + "' " : "") + "TOP " + s.topK();
            case ConsolidateStmt s -> "CONSOLIDATE " + String.join(", ", s.ids()) + " INTO " + s.targetTier();
            case ForgetStmt s -> "FORGET " + String.join(", ", s.ids()) + " " + s.mode();
            case ExplainRecallStmt s -> "EXPLAIN " + summarizeStatement(s.inner());
            case IntrospectStmt ignored -> "INTROSPECT";
        };
    }

    // ═══════════════════════════════════════════════════════════════
    //  Request & Response DTOs
    // ═══════════════════════════════════════════════════════════════

    public record MelExecuteRequest(
            String script,
            String namespace
    ) {}

    public record MelStatementExecutionResult(
            @JsonProperty("statement_type") String statementType,
            String status,
            String output,
            @JsonProperty("elapsed_ms") long elapsedMs,
            @JsonProperty("duration_micros") long durationMicros,
            @JsonProperty("affected_ids") List<String> affectedIds,
            List<MelCandidateItem> candidates,
            @JsonProperty("explain_rows") List<MelExplainRow> explainRows,
            FunnelTelemetry funnel,
            @JsonProperty("funnel_phases") List<FunnelPhase> funnelPhases,
            List<MelDiagnostic> diagnostics
    ) {}

    public record MelExecuteResponse(
            String status,
            String output,
            @JsonProperty("combined_output") String combinedOutput,
            @JsonProperty("elapsed_ms") long elapsedMs,
            @JsonProperty("total_elapsed_ms") long totalElapsedMs,
            @JsonProperty("duration_micros") long durationMicros,
            String namespace,
            @JsonProperty("statement_type") String statementType,
            @JsonProperty("affected_ids") List<String> affectedIds,
            List<MelCandidateItem> candidates,
            @JsonProperty("explain_rows") List<MelExplainRow> explainRows,
            FunnelTelemetry funnel,
            @JsonProperty("funnel_phases") List<FunnelPhase> funnelPhases,
            List<MelDiagnostic> diagnostics,
            List<MelStatementExecutionResult> statements
    ) {
        public static MelExecuteResponse fromError(
                String message,
                long durationMicros,
                String namespace,
                List<MelDiagnostic> diagnostics
        ) {
            long elapsed = durationMicros / 1000;
            return new MelExecuteResponse(
                    "ERROR",
                    "",
                    "",
                    elapsed,
                    elapsed,
                    durationMicros,
                    namespace,
                    "ERROR",
                    List.of(),
                    List.of(),
                    List.of(),
                    FunnelTelemetry.empty(),
                    List.of(),
                    diagnostics,
                    List.of()
            );
        }

        public static MelExecuteResponse fromResults(
                List<MelResult> results,
                long totalDurationMicros,
                String namespace
        ) {
            StringBuilder combinedOutput = new StringBuilder();
            List<MelStatementExecutionResult> stmtList = new ArrayList<>();
            List<String> allAffected = new ArrayList<>();
            List<MelCandidateItem> allCandidates = new ArrayList<>();
            List<MelExplainRow> allExplainRows = new ArrayList<>();
            FunnelTelemetry activeFunnel = FunnelTelemetry.empty();
            List<MelDiagnostic> allDiagnostics = new ArrayList<>();
            String primaryStatementType = "SCRIPT";
            boolean allOk = true;

            for (MelResult r : results) {
                if (!r.isOk()) {
                    allOk = false;
                }
                if (results.size() == 1) {
                    primaryStatementType = r.statementType();
                }
                if (!r.output().isBlank()) {
                    if (!combinedOutput.isEmpty()) {
                        combinedOutput.append("\n\n");
                    }
                    combinedOutput.append(r.output());
                }
                allAffected.addAll(r.affectedIds());
                if (!r.candidates().isEmpty()) {
                    allCandidates.addAll(r.candidates());
                }
                if (!r.explainRows().isEmpty()) {
                    allExplainRows.addAll(r.explainRows());
                }
                if (r.funnel().totalScanned() > 0 || r.funnel().phase6FinalReturned() > 0) {
                    activeFunnel = r.funnel();
                }
                allDiagnostics.addAll(r.diagnostics());

                stmtList.add(new MelStatementExecutionResult(
                        r.statementType(),
                        r.status().name(),
                        r.output(),
                        r.elapsedMs(),
                        r.durationMicros(),
                        r.affectedIds(),
                        r.candidates(),
                        r.explainRows(),
                        r.funnel(),
                        r.funnel().toPhases(),
                        r.diagnostics()
                ));
            }

            long totalElapsedMs = totalDurationMicros / 1000;
            String status = allOk ? "OK" : "ERROR";

            return new MelExecuteResponse(
                    status,
                    combinedOutput.toString(),
                    combinedOutput.toString(),
                    totalElapsedMs,
                    totalElapsedMs,
                    totalDurationMicros,
                    namespace,
                    primaryStatementType,
                    allAffected,
                    allCandidates,
                    allExplainRows,
                    activeFunnel,
                    activeFunnel.toPhases(),
                    allDiagnostics,
                    stmtList
            );
        }
    }

    public record MelValidateRequest(String script) {}

    public record MelStatementSummary(
            String type,
            String summary,
            int line,
            int column
    ) {}

    public record MelValidateResponse(
            boolean valid,
            @JsonProperty("statement_count") int statementCount,
            List<MelStatementSummary> statements,
            List<MelDiagnostic> diagnostics
    ) {}

    public record MelClauseTemplate(
            String clause,
            String syntax,
            String example
    ) {}

    public record MelSnippetTemplate(
            String id,
            String label,
            String verb,
            String category,
            String description,
            String template
    ) {
        @JsonProperty("code")
        public String code() {
            return template;
        }
    }

    public record MelSchemaResponse(
            List<String> keywords,
            List<String> tiers,
            List<String> sources,
            @JsonProperty("clause_templates") List<MelClauseTemplate> clauseTemplates,
            List<MelSnippetTemplate> snippets,
            @JsonProperty("snippet_library") List<MelSnippetTemplate> snippetLibrary
    ) {
        public static MelSchemaResponse standard() {
            List<String> keywords = List.of(
                    "REMEMBER", "RECALL", "EXPLAIN", "CONSOLIDATE", "FORGET", "INTROSPECT",
                    "WHERE", "TIER", "TIERS", "TAGS", "TOP", "IMPORTANCE", "VALENCE",
                    "AROUSAL", "SOURCE", "INTO", "ID", "TEXT", "BYTES", "RULE", "WEAKEN",
                    "SUPPRESS", "TOMBSTONE", "CONTAIN", "CONTAINS", "BETWEEN", "AND"
            );

            List<String> tiers = List.of("WORKING", "EPISODIC", "SEMANTIC", "PROCEDURAL");

            List<String> sources = List.of(
                    "EXPERIENCED", "DISTILLED", "SIMULATED", "REHEARSED", "OBSERVED",
                    "USER_STATED", "REFLECTED", "DREAMED", "TRANSFERRED", "INFERRED",
                    "PROCEDURAL", "THOUGHT_EXPERIMENT", "LANGEVIN_DISCOVERY"
            );

            List<MelClauseTemplate> clauseTemplates = List.of(
                    new MelClauseTemplate("TIERS", "TIERS <tier_list>", "TIERS EPISODIC, SEMANTIC"),
                    new MelClauseTemplate("TAGS", "TAGS CONTAIN ['<tag>']", "TAGS CONTAIN ['infra']"),
                    new MelClauseTemplate("IMPORTANCE", "IMPORTANCE >= <value>", "IMPORTANCE >= 0.5"),
                    new MelClauseTemplate("VALENCE", "VALENCE BETWEEN <min> AND <max>", "VALENCE BETWEEN -0.5 AND 0.8"),
                    new MelClauseTemplate("TOP", "TOP <k>", "TOP 10"),
                    new MelClauseTemplate("SOURCE", "SOURCE <source>", "SOURCE EXPERIENCED")
            );

            List<MelSnippetTemplate> snippets = List.of(
                    new MelSnippetTemplate(
                            "explain-recall",
                            "EXPLAIN RECALL (Glass-Box)",
                            "EXPLAIN RECALL",
                            "Retrieval",
                            "6-phase funnel & per-candidate fused score breakdown",
                            "EXPLAIN RECALL '{{cue}}'\n  TIERS EPISODIC, SEMANTIC\n  IMPORTANCE >= 0.4\n  TOP 5;"
                    ),
                    new MelSnippetTemplate(
                            "recall-filtered",
                            "RECALL (Filtered Scan)",
                            "RECALL",
                            "Retrieval",
                            "Multi-tier cognitive retrieval with tag & valence gates",
                            "RECALL '{{cue}}'\n  TIERS WORKING, EPISODIC, SEMANTIC\n  TAGS CONTAIN ['{{tag}}']\n  IMPORTANCE >= 0.5\n  VALENCE BETWEEN -0.8 AND 0.9\n  TOP 10;"
                    ),
                    new MelSnippetTemplate(
                            "remember-episodic",
                            "REMEMBER (Ingest Engram)",
                            "REMEMBER",
                            "Ingestion & Plasticity",
                            "Store an experienced episodic trace with valence & tags",
                            "REMEMBER TEXT '{{text}}'\n  INTO EPISODIC\n  ID '{{id}}'\n  TAGS ['{{tag}}']\n  IMPORTANCE 0.85\n  VALENCE -0.45\n  AROUSAL 0.80\n  SOURCE EXPERIENCED;"
                    ),
                    new MelSnippetTemplate(
                            "remember-semantic",
                            "REMEMBER (Semantic Fact)",
                            "REMEMBER",
                            "Ingestion & Plasticity",
                            "Store a permanent semantic fact with importance and tags",
                            "REMEMBER TEXT '{{text}}'\n  INTO SEMANTIC\n  ID '{{id}}'\n  TAGS ['{{tag}}']\n  IMPORTANCE 0.9\n  SOURCE DISTILLED;"
                    ),
                    new MelSnippetTemplate(
                            "consolidate-semantic",
                            "CONSOLIDATE (Episodic -> Semantic)",
                            "CONSOLIDATE",
                            "Consolidation & Lifecycle",
                            "Distill episodic traces into durable semantic knowledge",
                            "CONSOLIDATE '{{id}}'\n  INTO SEMANTIC\n  AS TEXT '{{distilled_text}}';"
                    ),
                    new MelSnippetTemplate(
                            "forget-by-id",
                            "FORGET (WEAKEN / SUPPRESS / TOMBSTONE)",
                            "FORGET",
                            "Consolidation & Lifecycle",
                            "Apply biological forgetting or soft tombstone",
                            "FORGET '{{id}}' TOMBSTONE;"
                    ),
                    new MelSnippetTemplate(
                            "introspect-engine",
                            "INTROSPECT (Engine Telemetry)",
                            "INTROSPECT",
                            "Consolidation & Lifecycle",
                            "Inspect tier capacity and cognitive memory telemetry",
                            "INTROSPECT;"
                    ),
                    new MelSnippetTemplate(
                            "full-lifecycle-script",
                            "Full Lifecycle Script",
                            "SCRIPT",
                            "Full Script",
                            "Multi-statement ingestion, retrieval, and consolidation workflow",
                            "REMEMBER TEXT 'Cluster replica lag detected on node-2' INTO EPISODIC ID 'ep-lag-01' TAGS ['infra', 'lag'] IMPORTANCE 0.8 SOURCE EXPERIENCED;\nRECALL 'replica lag' TIERS EPISODIC TOP 5;\nCONSOLIDATE 'ep-lag-01' INTO SEMANTIC AS TEXT 'Replication lag mitigated by increasing socket buffers';"
                    )
            );

            return new MelSchemaResponse(keywords, tiers, sources, clauseTemplates, snippets, snippets);
        }
    }
}
