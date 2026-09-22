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
import java.util.OptionalDouble;
import java.util.Optional;

/**
 * Sealed AST (Abstract Syntax Tree) for MEL statements.
 *
 * <p>Phase 1 covers the 4 required MF-001 operations plus
 * {@code EXPLAIN RECALL} (diagnostic) and {@code INTROSPECT} (optional).
 * Each permit is an immutable record holding the parsed fields.</p>
 */
public sealed interface MelStatement
        permits MelStatement.RememberStmt,
                MelStatement.RecallStmt,
                MelStatement.ConsolidateStmt,
                MelStatement.ForgetStmt,
                MelStatement.ExplainRecallStmt,
                MelStatement.IntrospectStmt {

    // ═══════════════════════════════════════════════════════════════
    //  Required Statements (MF-001)
    // ═══════════════════════════════════════════════════════════════

    /**
     * {@code REMEMBER payload INTO tier [clauses...];}
     */
    record RememberStmt(
            PayloadType payloadType,
            String payloadValue,
            String tier,
            Optional<String> id,
            List<String> tags,
            OptionalDouble importance,
            OptionalDouble valence,
            OptionalDouble arousal,
            Optional<String> source,
            Optional<Boolean> pin,
            Optional<Boolean> unresolved
    ) implements MelStatement {}

    /**
     * {@code RECALL [query] [clauses...];}
     */
    record RecallStmt(
            Optional<String> query,
            List<String> filterTags,
            OptionalDouble minValence,
            OptionalDouble maxValence,
            Optional<String> timeFrom,
            Optional<String> timeTo,
            OptionalDouble minImportance,
            List<String> tiers,
            int topK,
            Optional<Boolean> allowSimulated,
            Optional<String> context
    ) implements MelStatement {}

    /**
     * {@code CONSOLIDATE id_list INTO (SEMANTIC | PROCEDURAL) [AS payload];}
     */
    record ConsolidateStmt(
            List<String> ids,
            String targetTier,
            Optional<PayloadType> asPayloadType,
            Optional<String> asPayloadValue
    ) implements MelStatement {}

    /**
     * {@code FORGET id_list (WEAKEN | SUPPRESS | TOMBSTONE);}
     */
    record ForgetStmt(
            List<String> ids,
            ForgetMode mode
    ) implements MelStatement {}

    // ═══════════════════════════════════════════════════════════════
    //  Diagnostic Statement
    // ═══════════════════════════════════════════════════════════════

    /**
     * {@code EXPLAIN RECALL [query] [clauses...];}
     * Wraps a {@link RecallStmt} with diagnostic explain output.
     */
    record ExplainRecallStmt(RecallStmt inner) implements MelStatement {}

    // ═══════════════════════════════════════════════════════════════
    //  Optional Statement
    // ═══════════════════════════════════════════════════════════════

    /**
     * {@code INTROSPECT [REMEMBERER id] [TIER tier];}
     */
    record IntrospectStmt(
            Optional<String> remembererId,
            Optional<String> tier
    ) implements MelStatement {}

    // ═══════════════════════════════════════════════════════════════
    //  Supporting Enums
    // ═══════════════════════════════════════════════════════════════

    enum PayloadType { TEXT, BYTES, RULE }

    enum ForgetMode { WEAKEN, SUPPRESS, TOMBSTONE }
}
