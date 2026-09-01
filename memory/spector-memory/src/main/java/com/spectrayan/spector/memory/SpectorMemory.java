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
package com.spectrayan.spector.memory;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

import com.spectrayan.spector.core.quantization.ScalarQuantizer;
import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.cortex.CognitiveMemoryRouter;
import com.spectrayan.spector.memory.habituation.HabituationPenalty;
import com.spectrayan.spector.memory.hebbian.CoActivationRecordMemory;
import com.spectrayan.spector.memory.hebbian.HebbianGraphBase;
import com.spectrayan.spector.memory.id.MemoryIdGenerator;
import com.spectrayan.spector.memory.index.MemoryIndex;
import com.spectrayan.spector.memory.inhibition.SuppressionSet;
import com.spectrayan.spector.memory.metamemory.MemoryInsight;
import com.spectrayan.spector.memory.model.CognitiveProfile;
import com.spectrayan.spector.memory.model.CognitiveRecord;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.ImportanceResult;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.IngestionContext;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.model.ReflectReport;
import com.spectrayan.spector.memory.model.WhyNotExplanation;
import com.spectrayan.spector.memory.model.FactHistory;
import com.spectrayan.spector.memory.model.GraphRecallOptions;
import com.spectrayan.spector.memory.model.GraphTraversalResult;
import com.spectrayan.spector.memory.graph.CognitiveGraphFacade;
import com.spectrayan.spector.memory.neurodivergent.LateralEvaluator;
import com.spectrayan.spector.memory.pathway.RememberPathway;
import com.spectrayan.spector.memory.prospective.ProspectiveScheduler;
import com.spectrayan.spector.memory.prospective.Reminder;
import com.spectrayan.spector.memory.sync.MemoryWal;
import com.spectrayan.spector.memory.temporal.TemporalChainMemory;
import com.spectrayan.spector.memory.temporal.TemporalFact;
import com.spectrayan.spector.memory.model.ConversationRole;
import com.spectrayan.spector.memory.model.SourceModality;
import com.spectrayan.spector.memory.kernel.layout.EpisodicFieldAccessor;
import com.spectrayan.spector.memory.api.MemoryAdminView;
import com.spectrayan.spector.memory.api.MemoryRemember;
import com.spectrayan.spector.memory.api.MemoryRecall;
import com.spectrayan.spector.memory.api.MemoryReflection;
import com.spectrayan.spector.memory.session.EpisodicSessionIndex;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Primary interface for the Spector Cognitive Memory system.
 *
 * <p>Provides the full API surface for a Zero-GC cognitive backbone:
 * remember, recall, forget, reinforce, reflect, suppress, introspect,
 * prospective scheduling, working memory scratchpad, and subsystem access.</p>
 *
 * <p>Implementations include {@link DefaultSpectorMemory} (the standard
 * implementation) and metered decorators for observability.</p>
 *
 * <h3>Core API</h3>
 * <ul>
 *   <li>{@link #remember} — Ingest a memory</li>
 *   <li>{@link #recall} — Fused cognitive scoring across tiers</li>
 *   <li>{@link #forget} — Tombstone a memory</li>
 *   <li>{@link #reflect} — Trigger sleep consolidation</li>
 *   <li>{@link #reinforce} — Outcome-driven valence update</li>
 *   <li>{@link #suppress} — Session-level recall suppression</li>
 *   <li>{@link #introspect} — Metamemory self-analysis</li>
 *   <li>{@link #scheduleReminder} — Prospective memory</li>
 *   <li>{@link #scratchpad} — Working memory shorthand</li>
 * </ul>
 *
 * @see DefaultSpectorMemory
 */
public interface SpectorMemory extends MemoryRemember, MemoryRecall, MemoryReflection, MemoryAdminView, AutoCloseable {

    // ══════════════════════════════════════════════════════════════
    // INGESTION TARGET
    // ══════════════════════════════════════════════════════════════

    /** Returns the cognitive ingestion target for use with the unified IngestionPipeline. */
    RememberPathway target();

    /** Returns the namespace ID of this memory. */
    default String namespaceId() { return "default"; }

    /**
     * Acquires an active lease on this memory engine, preventing eviction while held.
     *
     * @return AutoCloseable handle that releases the lease upon close
     */
    default AutoCloseable acquireLease() {
        return () -> {};
    }

    // ══════════════════════════════════════════════════════════════
    // CORE API — remember / recall / forget / reflect
    // ══════════════════════════════════════════════════════════════

    /** Ingests a new memory. */
    void remember(String id, String text, MemoryType type,
                                      MemorySource source, String... tags);

    /**
     * Ingests a new memory with cognitive hints (ICNU + valence + arousal).
     *
     * <p>The hints allow the caller (typically an LLM) to provide subjective
     * importance signals — Interest, Challenge, Urgency — which are fused with
     * Spector's native Novelty signal to compute final importance. Emotional
     * valence and arousal modulate decay rates and recall ranking.</p>
     *
     * @param id     unique memory identifier
     * @param text   memory content
     * @param type   cognitive tier (WORKING, EPISODIC, SEMANTIC, PROCEDURAL)
     * @param source provenance (USER_STATED, OBSERVED, INFERRED, PROCEDURAL)
     * @param hints  ICNU + emotional context (null for novelty-only importance)
     * @param tags   synaptic tag strings for Bloom filter encoding
     * @see com.spectrayan.spector.memory.neurodivergent.IngestionHints
     */
    void remember(String id, String text, MemoryType type,
                                      MemorySource source,
                                      com.spectrayan.spector.memory.neurodivergent.IngestionHints hints,
                                      String... tags);

    /**
     * Ingests a new memory with full cognitive context.
     *
     * <p>The {@link IngestionContext} consolidates all LLM-provided metadata:
     * ICNU hints, pre-extracted entities, Hebbian edge hints, and temporal
     * chain links. This enables a single-call ingestion with complete
     * cognitive context — ideal for MCP tool integration.</p>
     *
     * @param id      unique memory identifier
     * @param text    memory content
     * @param type    cognitive tier (WORKING, EPISODIC, SEMANTIC, PROCEDURAL)
     * @param source  provenance (USER_STATED, OBSERVED, INFERRED, PROCEDURAL)
     * @param context consolidated cognitive metadata (entities, edges, links, hints)
     * @param tags    synaptic tag strings for Bloom filter encoding
     * @see IngestionContext
     */
    void remember(String id, String text, MemoryType type,
                                      MemorySource source,
                                      IngestionContext context,
                                      String... tags);

    /** Convenience overload with default source. */
    void remember(String id, String text, MemoryType type,
                                      String... tags);

    // ══════════════════════════════════════════════════════════════
    // AUTO-ID INGESTION — ID generated automatically
    // ══════════════════════════════════════════════════════════════

    /**
     * Ingests a memory with an auto-generated ID.
     *
     * <p>The ID is generated using the configured {@link MemoryIdGenerator}
     * (default: TSID — 13-char time-sorted identifier). This is the preferred
     * API for LLM/MCP callers who don't need to manage IDs manually.</p>
     *
     * @param text   the memory content
     * @param type   cognitive tier
     * @param source provenance source
     * @param tags   synaptic tag strings
     * @return the generated ID
     */
    String remember(String text, MemoryType type,
                                       MemorySource source, String... tags);

    /**
     * Ingests a memory with auto-generated ID and cognitive hints.
     *
     * @param text   the memory content
     * @param type   cognitive tier
     * @param source provenance source
     * @param hints  ICNU + emotional context (null for novelty-only)
     * @param tags   synaptic tag strings
     * @return the generated ID
     */
    String remember(String text, MemoryType type,
                                       MemorySource source,
                                       com.spectrayan.spector.memory.neurodivergent.IngestionHints hints,
                                       String... tags);

    /**
     * Ingests a memory with auto-generated ID and full cognitive context.
     *
     * <p>This is the richest auto-ID overload — carries metadata (source modality,
     * asset URIs), ICNU hints, entities, Hebbian edges, and temporal links in
     * a single {@link IngestionContext}. Preferred for multimodal ingestion.</p>
     *
     * @param text    the memory content (or extracted caption/transcript)
     * @param type    cognitive tier
     * @param source  provenance source
     * @param context consolidated cognitive metadata (metadata, hints, entities, etc.)
     * @param tags    synaptic tag strings
     * @return the generated ID
     */
    String remember(String text, MemoryType type,
                                       MemorySource source,
                                       IngestionContext context,
                                       String... tags);

    /**
     * Ingests a file as a memory with auto-generated ID.
     *
     * <p>Convenience method that builds an {@link IngestionContext} with the file path
     * in the {@code attachments} metadata key. The pipeline auto-detects MIME type,
     * routes to the appropriate {@code SensoryExtractor}, and stores extracted
     * content as sub-memories linked to the parent.</p>
     *
     * <p>If {@code text} is null or blank, the extracted caption/transcript
     * becomes the memory text. If {@code text} is provided, it serves as the
     * semantic anchor with the file as an attachment.</p>
     *
     * @param filePath local file path to ingest
     * @param text     optional text description (null = use extracted text)
     * @param type     cognitive tier
     * @param source   provenance source
     * @param tags     synaptic tag strings
     * @return the generated ID
     */
    default String rememberFile(java.nio.file.Path filePath,
                                                    String text,
                                                    MemoryType type,
                                                    MemorySource source,
                                                    String... tags) {
        String effectiveText = (text != null && !text.isBlank()) ? text : filePath.getFileName().toString();
        IngestionContext context = IngestionContext.builder()
                .metadata(com.spectrayan.spector.memory.model.SourceModality.ATTACHMENTS_KEY,
                        filePath.toAbsolutePath().toString())
                .build();
        return remember(effectiveText, type, source, context, tags);
    }

    /** Performs fused cognitive scoring across all relevant memory tiers. */
    List<CognitiveResult> recall(String queryText, RecallOptions options);

    /** Convenience recall using a CognitiveProfile preset. */
    List<CognitiveResult> recall(String queryText, CognitiveProfile profile);

    /** Convenience overload with default options. */
    List<CognitiveResult> recall(String queryText);

    /** Tombstones a memory by ID (logical deletion). */
    void forget(String id);

    /** Triggers a synchronous reflection (sleep consolidation) cycle. */
    ReflectReport reflect();

    /**
     * Executes the Express (6th Canonical Cognitive Pathway) for persona-aligned synthesis,
     * vocal prosody modulation, and stylometric validation.
     *
     * @param signal the express signal carrying query text and cognitive candidates
     * @return the express report
     */
    default com.spectrayan.spector.memory.express.relay.ExpressReport express(
            com.spectrayan.spector.memory.express.relay.ExpressSignal signal) {
        return com.spectrayan.spector.memory.express.relay.ExpressReport.empty();
    }

    /**
     * Triggers a Default Mode Network (DMN) spontaneous mind-wandering and continuity snapshot cycle.
     *
     * @return resulting wander report
     */
    default com.spectrayan.spector.memory.wander.relay.WanderReport wander() {
        return com.spectrayan.spector.memory.wander.relay.WanderReport.empty();
    }

    /**
     * Triggers a generative dream or deliberate thought experiment cycle (#679).
     *
     * @param mode dreaming mode (REM, DAYDREAM, THOUGHT_EXPERIMENT)
     * @return resulting dream report
     */
    default com.spectrayan.spector.memory.dream.relay.DreamReport dream(com.spectrayan.spector.memory.dream.relay.DreamMode mode) {
        return com.spectrayan.spector.memory.dream.relay.DreamReport.empty();
    }

    /**
     * Triggers a standard REM generative dream cycle (#679).
     *
     * @return resulting dream report
     */
    default com.spectrayan.spector.memory.dream.relay.DreamReport dream() {
        return dream(com.spectrayan.spector.memory.dream.relay.DreamMode.REM);
    }

    /**
     * Evaluates candidate cognitive policies by minimizing Expected Free Energy G(π) across
     * the active multi-soul context hierarchy (AgentSoul, UserSoul, TenantSoul, OrgUnitSoul).
     *
     * <h3>Biological Analog: Prefrontal Decision Circuit (dlPFC + ACC)</h3>
     * <p>Selects the optimal cognitive policy balancing epistemic exploration (uncertainty reduction)
     * versus pragmatic exploitation (goal-directed action) using Boltzmann softmax selection
     * with precision γ modulated by homeostatic arousal and dominance.</p>
     *
     * @param signal the decide signal carrying candidate policies and soul context
     * @return the resulting decision report with selected policy and ranked alternatives
     */
    default com.spectrayan.spector.memory.decide.relay.DecideReport decide(
            com.spectrayan.spector.memory.decide.relay.DecideSignal signal) {
        return com.spectrayan.spector.memory.decide.relay.DecideReport.empty();
    }

    /**
     * Retrieves the longitudinal identity and consciousness continuity trajectory history.
     *
     * @param limit maximum number of snapshots to return (newest first)
     * @return list of snapshots
     */
    default java.util.List<com.spectrayan.spector.memory.aisme.continuity.IdentityTrajectorySnapshot> continuityHistory(int limit) {
        return java.util.List.of();
    }

    /**
     * Computes the cumulative generative prior drift across recorded history.
     *
     * @return maximum recorded drift from baseline
     */
    default float calculateLongitudinalDrift() {
        return 0.0f;
    }

    /** Triggers a manual memory consolidation process. */
    void consolidate();

    /** Updates the chunking configuration at runtime. */
    default void updateChunkConfig(com.spectrayan.spector.commons.chunker.ChunkConfig config) {}


    // ══════════════════════════════════════════════════════════════
    // IMPORTANCE ESTIMATION — pre-ingestion computation
    // ══════════════════════════════════════════════════════════════

    /**
     * Computes importance for a prospective memory <em>without</em> ingesting it.
     *
     * <p>This is a <b>read-only, side-effect-free</b> operation. It embeds the text,
     * computes novelty against the existing store, and fuses with optional ICNU
     * hints to produce a full importance estimate. The LLM can use this to:
     * <ul>
     *   <li>Preview what importance a memory would receive before committing</li>
     *   <li>Adjust ICNU hints (Interest, Challenge, Urgency) based on the novelty signal</li>
     *   <li>Detect near-duplicates via the nearest memory ID</li>
     *   <li>Understand how the active cognitive profile affects scoring</li>
     * </ul>
     *
     * <h3>MCP Workflow</h3>
     * <pre>{@code
     *   // 1. LLM asks for importance estimate
     *   var est = memory.estimateImportance("The database crashed", hints);
     *   // → novelty=0.82, fused=7.8, nearest="mem-42"
     *
     *   // 2. LLM decides to proceed (or skip if near-duplicate)
     *   memory.remember("db-crash", "The database crashed...", ...);
     * }</pre>
     *
     * @param text  the memory text to evaluate
     * @param hints optional ICNU hints (null = novelty-only estimate)
     * @return importance estimate with novelty, fusion, nearest memory, and profile weights
     */
    ImportanceResult estimateImportance(String text,
                                          com.spectrayan.spector.memory.neurodivergent.IngestionHints hints);

    /**
     * Convenience overload — estimates importance with novelty-only (no ICNU hints).
     */
    default ImportanceResult estimateImportance(String text) {
        return estimateImportance(text, null);
    }

    // ══════════════════════════════════════════════════════════════
    // EXTENDED API — reinforce / suppress / introspect
    // ══════════════════════════════════════════════════════════════

    /** Reports an outcome (positive/negative) for a previously recalled memory. */
    void reinforce(String memoryId, byte valence);

    /**
     * Reinforces a memory with optional ICNU hints for importance re-fusion.
     *
     * <p>When {@code updatedHints} is provided, the memory's importance is re-fused
     * using the updated ICNU weights (Interest, Challenge, Novelty, Urgency).
     * When {@code updatedHints} is null, importance is auto-adjusted based on the
     * memory's graph position (degree centrality in the Hebbian graph).</p>
     *
     * @param memoryId     the memory ID to reinforce
     * @param valence      positive/negative outcome (-128 to +127)
     * @param updatedHints optional ICNU hints for re-fusion (null = auto-compute from graph)
     */
    default void reinforce(String memoryId, byte valence,
                           com.spectrayan.spector.memory.neurodivergent.IngestionHints updatedHints) {
        reinforce(memoryId, valence); // default: delegate to simple reinforce
    }

    /** Suppresses a memory from future recall with a reason. */
    void suppress(String memoryId, String reason);

    /** Suppresses a memory from future recall. */
    void suppress(String memoryId);

    /** Removes a suppression, allowing recall again. */
    void unsuppress(String memoryId);

    /**
     * Marks a memory as resolved (Zeigarnik Effect).
     * Resolved memories return to normal time-decay and gradually fade.
     */
    void markResolved(String memoryId);

    /**
     * Marks a memory as unresolved (Zeigarnik Effect).
     * Unresolved memories resist time-decay and float to the top of recall.
     */
    void markUnresolved(String memoryId);

    /** Introspects the agent's knowledge about a topic (metamemory). */
    MemoryInsight introspect(String topic);

    /**
     * Explains why a specific memory was NOT returned for a given query.
     *
     * <p>Evaluates the memory against the full scoring pipeline (in OBSERVE mode,
     * so no state mutations) and identifies the exact reason it was excluded:
     * not found, tombstoned, suppressed, outranked, or pre-filtered.</p>
     *
     * @param memoryId  the memory ID to investigate
     * @param queryText the query it was expected to match
     * @param options   recall options (profile, filters, etc.) — recallMode is forced to OBSERVE
     * @return a diagnostic explanation of why the memory was not retrieved
     */
    WhyNotExplanation whyNot(String memoryId, String queryText, RecallOptions options);

    // ══════════════════════════════════════════════════════════════
    // INSPECT — Full Cognitive X-Ray
    // ══════════════════════════════════════════════════════════════

    /**
     * Returns the complete cognitive snapshot for a memory — the "X-ray" view.
     *
     * <p>Combines data from three subsystems into a single {@link CognitiveRecord}:</p>
     * <ul>
     *   <li><b>MemoryIndex</b>: text, source, tags, physical location</li>
     *   <li><b>CognitiveHeader</b> (64-byte off-heap): importance, valence, arousal,
     *       recall count, storage strength, synaptic tags, flags</li>
     *   <li><b>Vector payload</b>: quantized INT8 bytes</li>
     * </ul>
     *
     * @param id the memory ID to inspect
     * @return the full cognitive record, or null if the memory is not found
     */
    CognitiveRecord inspect(String id);

    // ══════════════════════════════════════════════════════════════
    // BROWSE — Tag-Based Iteration
    // ══════════════════════════════════════════════════════════════

    /**
     * Returns all memories matching the given tags (without vector search).
     *
     * <p>This is a metadata-only scan — it does not embed a query or compute
     * similarity scores. Useful for browsing, auditing, and bulk operations
     * ("show me all memories tagged 'payments'").</p>
     *
     * <p>If multiple tags are provided, a memory must contain <b>all</b> of them
     * (AND semantics).</p>
     *
     * @param tags one or more tag strings to match
     * @return list of matching cognitive records (without vectors — use inspect() for full detail)
     */
    List<CognitiveRecord> browse(String... tags);

    // ══════════════════════════════════════════════════════════════
    // EPISODIC CONVERSATION API  (ADR-0006)
    // ══════════════════════════════════════════════════════════════

    /**
     * Appends a conversation turn to the episodic log.
     *
     * <p><b>Lightweight path:</b> bypasses the full cognitive pipeline
     * (embedding, HNSW, BM25, etc.). Writes directly to the log-structured
     * mmap region.</p>
     *
     * @param role        conversation role
     * @param sequenceId  monotonic turn counter per session
     * @param timestampMs epoch milliseconds
     * @param sessionId   8B TSID hash identifying the session
     * @param body        raw CBOR body bytes
     * @param modelId     LLM model registry ID
     * @param tokenIn     input token count
     * @param tokenOut    output token count
     * @param latencyMs   response latency in ms
     * @param userId      user/tenant 8B TSID hash
     * @param soulVersion agent soul configuration version
     * @param modality    source modality
     * @return the byte offset of the written record
     */
    default long rememberEpisodic(ConversationRole role, int sequenceId,
                                   long timestampMs, long sessionId,
                                   byte[] body, short modelId,
                                   int tokenIn, int tokenOut,
                                   int latencyMs, long userId,
                                   short soulVersion, SourceModality modality) {
        throw new UnsupportedOperationException("Episodic log not supported by this implementation");
    }

    /**
     * Reads paginated conversation turns for a session.
     *
     * @param sessionId 8B TSID hash
     * @param offset    zero-based start index
     * @param limit     maximum number of turns
     * @return list of decoded episodic records
     */
    default List<EpisodicFieldAccessor.EpisodicRecord> browseEpisodic(long sessionId, int offset, int limit) {
        return List.of();
    }

    /**
     * Returns the last N turns for a session (for LLM context assembly).
     *
     * @param sessionId 8B TSID hash
     * @param count     number of recent turns
     * @return list of decoded episodic records
     */
    default List<EpisodicFieldAccessor.EpisodicRecord> tailEpisodic(long sessionId, int count) {
        return List.of();
    }

    /**
     * Returns the session index for external query.
     */
    default EpisodicSessionIndex episodicSessionIndex() {
        return null;
    }

    // ══════════════════════════════════════════════════════════════
    // EXPORT — Bulk Memory Export
    // ══════════════════════════════════════════════════════════════

    /**
     * Exports all memories as a JSON array string.
     *
     * <p>Each memory is serialized as a {@link CognitiveRecord#toJson()} object.
     * This is suitable for backup, migration, audit, and debugging.</p>
     *
     * <p>For large memory stores, consider using {@link #browse(String...)} with
     * tag filters to export subsets.</p>
     *
     * @return JSON array string containing all memory records
     */
    String exportJson();

    // ══════════════════════════════════════════════════════════════
    // PROSPECTIVE / SCRATCHPAD / STATS
    // ══════════════════════════════════════════════════════════════

    /** Schedules a reminder at a specific instant. */
    Reminder scheduleReminder(String text, Instant triggerAt, String... tags);

    /** Schedules a reminder after a delay. */
    Reminder scheduleReminder(String text, Duration delay, String... tags);

    /** Returns the namespace-scoped background task scheduler and audit manager. */
    default com.spectrayan.spector.memory.scheduler.MemoryScheduler scheduler() {
        return admin().scheduler();
    }

    /** Stores ephemeral text in working memory. */
    void scratchpad(String text);

    /** Returns the total number of memories across all tiers. */
    int totalMemories();

    /** Returns the number of memories in a specific tier. */
    int memoryCount(MemoryType type);

    // ══════════════════════════════════════════════════════════════
    // SALIENCE PROFILE — runtime personality & interest configuration
    // ══════════════════════════════════════════════════════════════

    /**
     * Sets the active salience profile (thread-safe hot-swap).
     *
     * <p>The salience profile controls importance modulation based on user
     * interests, disinterests, and persona context. Changes take effect
     * immediately for subsequent ingestions and recall queries.</p>
     *
     * @param profile the salience profile to activate (null resets to NEUTRAL)
     * @see com.spectrayan.spector.memory.model.SalienceProfile
     */
    void setSalienceProfile(com.spectrayan.spector.memory.model.SalienceProfile profile);

    /**
     * Sets the current soul version for encoding state stamping during ingestion.
     *
     * <p>The soul version is monotonically increasing and stamped into the
     * synaptic header at ingestion time. This enables detection of stale memories
     * whose importance was computed under an older soul configuration.</p>
     *
     * @param version the current soul version
     */
    void setSoulVersion(short version);

    /**
     * Applies the resolved request-time identity state (primary soul, soul stack,
     * and overlayed salience profile) to this memory instance for subsequent operations (ADR-0029 §2.5, §23).
     *
     * @param primarySoul the primary soul context (user or agent)
     * @param soulStack the ordered soul hierarchy [TenantSoul, OrgUnitSoul..., UserSoul]
     * @param salience the effective salience profile overlayed with namespace bias
     */
    default void applyIdentity(
            com.spectrayan.spector.memory.model.SoulContext primarySoul,
            java.util.List<com.spectrayan.spector.memory.model.SoulContext> soulStack,
            com.spectrayan.spector.memory.model.SalienceProfile salience) {
        if (salience != null) {
            setSalienceProfile(salience);
        }
        if (primarySoul != null) {
            setSoulVersion(primarySoul.soulVersion());
        }
    }

    /**
     * Returns the currently active salience profile.
     *
     * @return the effective salience profile (never null — NEUTRAL if unset)
     */
    com.spectrayan.spector.memory.model.SalienceProfile salienceProfile();

    /**
     * Computes the topic boost a memory text would receive under the current
     * salience profile <em>without</em> ingesting.
     *
     * <p>This is a read-only, side-effect-free preview operation. The text is
     * embedded and compared against all interest/disinterest domains in the
     * active profile.</p>
     *
     * @param text the memory text to evaluate
     * @return the multiplicative topic boost (1.0 = no effect)
     */
    float computeTopicBoost(String text);

    /**
     * Computes the self-relevance boost a memory text would receive under the
     * current salience profile's persona context <em>without</em> ingesting.
     *
     * <p>The text embedding is compared against persona embeddings (occupation,
     * education, values, aspirations, cultural identity). The boost is capped
     * at ±15% per {@link com.spectrayan.spector.memory.model.PersonalityModifiers}.</p>
     *
     * @param text the memory text to evaluate
     * @return the multiplicative self-relevance boost in [0.85, 1.15]
     */
    float computeSelfRelevanceBoost(String text);

    // ══════════════════════════════════════════════════════════════
    // ADMIN INTERFACE
    // ══════════════════════════════════════════════════════════════

    /**
     * Returns the administrative interface for accessing internal subsystems.
     *
     * <p>Typical SDK consumers should not need this — it provides access to
     * WAL, tier router, Hebbian graph, quantizer, and other internal
     * components for operational monitoring, tuning, and advanced integrations.</p>
     *
     * @return the admin interface (never null)
     * @since 1.0.0
     */
    SpectorMemoryAdmin admin();

    /**
     * Assert a temporal fact about an entity relationship.
     * @param subject the subject entity name
     * @param predicate the relationship type
     * @param object the object entity name
     * @param validFrom epoch seconds when fact becomes valid
     * @param validTo epoch seconds when fact expires (Long.MAX_VALUE for open-ended)
     * @param confidence confidence score [0.0, 1.0]
     * @return monotonic fact ID
     */
    int assertFact(String subject, String predicate, String object,
                   long validFrom, long validTo, float confidence);

    /**
     * Assert a temporal fact with automatic supersession.
     *
     * <p>When {@code allowCoexisting} is {@code false} (the recommended default),
     * any existing active fact with the same (subject, predicate) pair is automatically
     * retracted and linked via {@code retractsFactId}. This creates a supersession chain
     * preserving the full history of how facts evolved.</p>
     *
     * <p>When {@code allowCoexisting} is {@code true}, the new fact is appended
     * alongside existing facts — useful for multi-valued predicates like
     * {@code speaks_language} or {@code has_skill}.</p>
     *
     * @param subject         the subject entity name
     * @param predicate       the relationship type
     * @param object          the object entity name
     * @param validFrom       epoch seconds when fact becomes valid
     * @param validTo         epoch seconds when fact expires (Long.MAX_VALUE for open-ended)
     * @param confidence      confidence score [0.0, 1.0]
     * @param allowCoexisting if true, skip auto-retraction (multi-valued predicates)
     * @return monotonic fact ID
     * @since 1.2.0
     */
    int assertFact(String subject, String predicate, String object,
                   long validFrom, long validTo, float confidence,
                   boolean allowCoexisting);

    /**
     * Retract a previously asserted fact.
     * @param factId the fact ID to retract
     * @return the retraction record's fact ID
     */
    int retractFact(int factId);

    /**
     * Query temporal facts about an entity valid at the given instant.
     * @param entityName the entity name to query
     * @param asOf the point in time to query facts for
     * @return list of valid temporal facts
     */
    List<TemporalFact> factsAbout(String entityName, Instant asOf);

    /**
     * Returns the full supersession chain for a (subject, predicate) pair.
     *
     * <p>Includes the currently active fact plus all historical versions,
     * ordered newest-first by transaction time. Use this to understand how
     * a fact evolved over time or to surface conflicting evidence.</p>
     *
     * @param subject   the entity name
     * @param predicate the relationship type
     * @return fact history with active and superseded snapshots
     * @since 1.2.0
     */
    FactHistory factHistory(String subject, String predicate);

    /**
     * Multi-hop graph traversal across temporal facts and entity hyperedges.
     *
     * <p>Discovers relational paths between entities with grounding memory
     * context. Supports temporal point-in-time filtering via
     * {@link GraphRecallOptions#asOf()} and superseded fact inclusion.</p>
     *
     * <p>Default implementation delegates to
     * {@link CognitiveGraphFacade#graphRecall(GraphRecallOptions, java.util.function.Function)}
     * via the admin interface.</p>
     *
     * @param options graph traversal configuration
     * @return structured traversal result
     * @since 1.2.0
     */
    default GraphTraversalResult graphRecall(GraphRecallOptions options) {
        return admin().graph().graphRecall(options, this::inspect);
    }

    /** Closes the memory system and persists data. */
    @Override
    void close();
}

