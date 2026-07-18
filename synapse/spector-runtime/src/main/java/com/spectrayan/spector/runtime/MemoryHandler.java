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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.IngestionContext;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.model.ReflectReport;
import com.spectrayan.spector.memory.model.SourceModality;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.metamemory.MemoryInsight;
import com.spectrayan.spector.memory.neurodivergent.IngestionHints;
import com.spectrayan.spector.memory.prospective.Reminder;

/**
 * Facade over {@link SpectorMemory} for the full cognitive memory API.
 *
 * <h3>Design Rationale: Direct Facade vs. Strategy Pattern</h3>
 * <p>Unlike {@link SearchHandler} and {@link IngestionHandler} which route
 * operations to either the engine or memory subsystem based on
 * {@link com.spectrayan.spector.config.SpectorMode}, the {@code MemoryHandler}
 * <strong>always</strong> delegates to {@link SpectorMemory}. There is no
 * engine-mode alternative for cognitive operations like {@code remember()},
 * {@code suppress()}, or {@code markResolved()}.</p>
 *
 * <p>For this reason, a full Strategy/Factory pattern (e.g., separate
 * {@code SearchStrategy} implementations per mode) was considered but deferred:
 * <ul>
 *   <li>Only 2 modes exist ({@code SEARCH}, {@code MEMORY}), making if/else
 *       branching in the dual-mode handlers simple and explicit.</li>
 *   <li>The roadmap includes "both modes simultaneously" which would require
 *       composite strategies, invalidating a strict either-or pattern.</li>
 *   <li>Memory operations are inherently single-target — no mode dispatch needed.</li>
 * </ul>
 * If mode branching becomes complex (3+ modes, per-operation routing), this class
 * and the existing handlers should be refactored to a Strategy + Factory pattern.</p>
 *
 * <p>Obtained via {@code runtime.memory()}. Returns {@code null} if memory is not enabled.</p>
 *
 * @see SpectorMemory
 * @see SearchHandler
 * @see IngestionHandler
 */
public final class MemoryHandler {

    private static final Logger log = LoggerFactory.getLogger(MemoryHandler.class);

    private final SpectorMemory memory;

    MemoryHandler(SpectorMemory memory) {
        this.memory = memory;
    }

    // ══════════════════════════════════════════════════════════════
    // CORE — remember / recall / forget / reflect
    // ══════════════════════════════════════════════════════════════

    /**
     * Stores a memory with full cognitive metadata.
     *
     * @param id     unique memory identifier
     * @param text   memory content
     * @param type   tier (WORKING, EPISODIC, SEMANTIC, PROCEDURAL)
     * @param source provenance
     * @param hints  optional ICNU + valence + arousal (null for defaults)
     * @param tags   synaptic tags for Bloom filter encoding
     * @return future that completes when the memory is persisted
     */
    public CompletableFuture<Void> remember(String id, String text, MemoryType type,
                                             MemorySource source, IngestionHints hints,
                                             String... tags) {
        log.debug("[Memory] remember id={}, tier={}, source={}, hints={}",
                id, type, source, hints != null ? "ICNU" : "none");
        return memory.remember(id, text, type, source, hints, tags);
    }

    /**
     * Convenience overload without cognitive hints.
     */
    public CompletableFuture<Void> remember(String id, String text, MemoryType type,
                                             MemorySource source, String... tags) {
        return memory.remember(id, text, type, source, tags);
    }

    /**
     * Stores a memory with full IngestionContext (metadata, hints, entities, etc.).
     *
     * <p>This is the richest overload — carries multimodal metadata (source modality,
     * asset URIs), ICNU hints, pre-extracted entities, and temporal links.
     * Preferred for programmatic multimodal ingestion.</p>
     *
     * @param id      unique memory identifier
     * @param text    memory content (or extracted caption/transcript for multimodal)
     * @param type    tier (WORKING, EPISODIC, SEMANTIC, PROCEDURAL)
     * @param source  provenance
     * @param context consolidated cognitive metadata
     * @param tags    synaptic tags for Bloom filter encoding
     * @return future that completes when the memory is persisted
     */
    public CompletableFuture<Void> remember(String id, String text, MemoryType type,
                                             MemorySource source, IngestionContext context,
                                             String... tags) {
        log.debug("[Memory] remember id={}, tier={}, source={}, context={}",
                id, type, source, context.hasMetadata() ? "multimodal" : "text-only");
        return memory.remember(id, text, type, source, context, tags);
    }

    // ── Auto-ID convenience ──

    /**
     * Stores a memory with auto-generated ID.
     *
     * @return future that completes with the generated memory ID
     */
    public CompletableFuture<String> rememberAutoId(String text, MemoryType type,
                                                     MemorySource source, String... tags) {
        return memory.remember(text, type, source, tags);
    }

    /**
     * Stores a memory with auto-generated ID and cognitive hints.
     *
     * @return future that completes with the generated memory ID
     */
    public CompletableFuture<String> rememberAutoId(String text, MemoryType type,
                                                     MemorySource source,
                                                     IngestionHints hints,
                                                     String... tags) {
        return memory.remember(text, type, source, hints, tags);
    }

    /**
     * Stores a memory with auto-generated ID and full cognitive context.
     *
     * <p>The richest auto-ID API — ideal for multimodal memories where the
     * caller provides a caption/transcript plus metadata about the original
     * source (image path, VLM model, etc.).</p>
     *
     * @return future that completes with the generated memory ID
     */
    public CompletableFuture<String> rememberAutoId(String text, MemoryType type,
                                                     MemorySource source,
                                                     IngestionContext context,
                                                     String... tags) {
        log.debug("[Memory] rememberAutoId tier={}, source={}, modality={}",
                type, source, context.sourceModality());
        return memory.remember(text, type, source, context, tags);
    }

    /**
     * Ingests a file as a memory with auto-generated ID.
     *
     * <p>Convenience method that wraps the file path into an {@link IngestionContext}
     * with the {@code attachments} metadata key. The pipeline auto-detects MIME type
     * and routes to the appropriate SensoryExtractor.</p>
     *
     * @param filePath local file path to ingest
     * @param text     optional text description (null = use filename)
     * @param type     cognitive tier
     * @param source   provenance
     * @param tags     synaptic tags
     * @return future that completes with the generated memory ID
     */
    public CompletableFuture<String> rememberFile(java.nio.file.Path filePath,
                                                    String text,
                                                    MemoryType type,
                                                    MemorySource source,
                                                    String... tags) {
        log.debug("[Memory] rememberFile path={}, tier={}, source={}", filePath, type, source);
        return memory.rememberFile(filePath, text, type, source, tags);
    }

    /**
     * Performs cognitive recall across all memory tiers.
     *
     * @param queryText natural language query
     * @param options   recall configuration (topK, profile, filters)
     * @return scored cognitive results
     */
    public List<CognitiveResult> recall(String queryText, RecallOptions options) {
        return memory.recall(queryText, options);
    }

    /**
     * Convenience recall with default options.
     */
    public List<CognitiveResult> recall(String queryText, int topK) {
        return memory.recall(queryText, RecallOptions.builder().topK(topK).build());
    }

    /**
     * Tombstones a memory (logical deletion).
     */
    public void forget(String id) {
        memory.forget(id);
        log.debug("[Memory] forgot id={}", id);
    }

    /**
     * Triggers a synchronous reflection (sleep consolidation) cycle.
     */
    public ReflectReport reflect() {
        return memory.reflect();
    }

    // ══════════════════════════════════════════════════════════════
    // EXTENDED — reinforce / suppress / resolve / introspect
    // ══════════════════════════════════════════════════════════════

    /**
     * Reports outcome feedback (positive/negative) for a recalled memory.
     *
     * @param memoryId the memory to reinforce
     * @param valence  -128 (very negative) to +127 (very positive)
     */
    public void reinforce(String memoryId, byte valence) {
        memory.reinforce(memoryId, valence);
        log.debug("[Memory] reinforced id={}, valence={}", memoryId, valence);
    }

    /**
     * Suppresses a memory from future recall.
     */
    public void suppress(String memoryId, String reason) {
        if (reason != null && !reason.isBlank()) {
            memory.suppress(memoryId, reason);
        } else {
            memory.suppress(memoryId);
        }
        log.debug("[Memory] suppressed id={}, reason={}", memoryId, reason);
    }

    /**
     * Removes a suppression, allowing the memory to be recalled again.
     */
    public void unsuppress(String memoryId) {
        memory.unsuppress(memoryId);
        log.debug("[Memory] unsuppressed id={}", memoryId);
    }

    /**
     * Marks a memory as resolved (Zeigarnik Effect).
     * Resolved memories return to normal time-decay and gradually fade.
     */
    public void markResolved(String memoryId) {
        memory.markResolved(memoryId);
        log.debug("[Memory] resolved id={}", memoryId);
    }

    /**
     * Marks a memory as unresolved (Zeigarnik Effect).
     * Unresolved memories resist time-decay and float to the top of recall.
     */
    public void markUnresolved(String memoryId) {
        memory.markUnresolved(memoryId);
        log.debug("[Memory] unresolved id={}", memoryId);
    }

    /**
     * Introspects the agent's knowledge about a topic (metamemory).
     */
    public MemoryInsight introspect(String topic) {
        return memory.introspect(topic);
    }

    // ══════════════════════════════════════════════════════════════
    // PROSPECTIVE / SCRATCHPAD / STATS
    // ══════════════════════════════════════════════════════════════

    /**
     * Schedules a prospective reminder.
     *
     * @param text  reminder text
     * @param delay time until the reminder fires
     * @param tags  contextual tags
     * @return the scheduled reminder
     */
    public Reminder scheduleReminder(String text, Duration delay, String... tags) {
        return memory.scheduleReminder(text, delay, tags);
    }

    /**
     * Stores ephemeral text in working memory scratchpad.
     */
    public CompletableFuture<Void> scratchpad(String text) {
        return memory.scratchpad(text);
    }

    /**
     * Returns the total number of memories across all tiers.
     */
    public int totalMemories() {
        return memory.totalMemories();
    }

    /**
     * Returns the number of memories in a specific tier.
     */
    public int memoryCount(MemoryType type) {
        return memory.memoryCount(type);
    }

    /**
     * Explicitly decays importance of old memories.
     *
     * @param olderThan memories older than this duration are decayed
     * @param factor    decay multiplier (0.0–1.0)
     * @return number of memories decayed
     */
    public int decay(Duration olderThan, float factor) {
        return memory.admin().decay(olderThan, factor);
    }

    /**
     * Returns the underlying SpectorMemory for direct subsystem access.
     * Prefer using handler methods when possible.
     */
    public SpectorMemory raw() {
        return memory;
    }
}
