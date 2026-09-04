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
package com.spectrayan.spector.memory.pathway.reflect.daemon;

import com.spectrayan.spector.commons.concurrent.ConcurrentTasks;
import com.spectrayan.spector.commons.concurrent.ConcurrentExecutionException;
import com.spectrayan.spector.memory.persist.PartitionManager;
import com.spectrayan.spector.memory.cortex.EpisodicMemory;
import com.spectrayan.spector.memory.cortex.index.MemoryIndex;
import com.spectrayan.spector.memory.model.EpisodeRecord;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.ReflectReport;
import com.spectrayan.spector.memory.cortex.CentroidRouter;
import com.spectrayan.spector.memory.kernel.layout.EngramLayout;
import com.spectrayan.spector.memory.kernel.layout.EncodingHeader;
import com.spectrayan.spector.memory.kernel.layout.EncodingHeaderFields;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.generation.LlmProvider;
import com.spectrayan.spector.provider.generation.GenerationOptions;
import com.spectrayan.spector.core.similarity.VectorOps;
import com.spectrayan.spector.memory.pathway.remember.RememberPathway;
import com.spectrayan.spector.memory.cortex.MemorySource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Background Virtual Thread that runs the two-phase sleep consolidation cycle.
 *
 * <h3>Biological Analog: Hippocampal Replay During Sleep</h3>
 * <p>During sleep, the hippocampus replays episodic memories to the neocortex
 * for consolidation. Dense clusters of related episodes are compressed into
 * permanent semantic facts. Weak, isolated memories are pruned.</p>
 *
 * <h3>Two-Phase Sleep Cycle  --  NREM + REM Mapping</h3>
 *
 * <table border="1">
 *   <tr><th>Spector Phase</th><th>Sleep Stage</th><th>Neuroscience Mechanism</th><th>Implementation</th></tr>
 *   <tr>
 *     <td><b>Deep Sleep</b></td>
 *     <td>NREM Stage 3-4 (Slow-Wave Sleep)</td>
 *     <td><b>Synaptic Homeostasis Hypothesis (SHY)</b>  --  Tononi &amp; Cirelli, 2003.
 *         During waking hours, synapses are strengthened by learning (LTP).
 *         During SWS, global synaptic downscaling occurs: weak synapses are
 *         pruned while strong ones are preserved. This prevents saturation
 *         and frees capacity for new learning.</td>
 *     <td>Scan episodic partitions, tombstone records where
 *         {@code decayed_importance &lt; threshold}. Trigger compaction
 *         when tombstone ratio exceeds 30%. This is the digital analog
 *         of synaptic downscaling.</td>
 *   </tr>
 *   <tr>
 *     <td><b>REM Sleep</b></td>
 *     <td>REM (Rapid Eye Movement)</td>
 *     <td><b>Memory Consolidation &amp; Schema Integration.</b> During REM,
 *         the hippocampus replays episodic traces while the neocortex
 *         integrates them into existing knowledge schemas. Related episodes
 *         are generalized into semantic facts (gist extraction). This is
 *         why "sleeping on it" helps problem-solving  --  REM finds patterns
 *         across disparate episodes.</td>
 *     <td>Cluster episodic memories by IVF centroid proximity. Dense
 *         clusters ( >= 5 episodes) are synthesized into semantic facts via
 *         LLM summarization or highest-importance promotion. Source
 *         episodes are tombstoned (unless {@code pinSourceEpisodes=true}
 *         for lossless consolidation).</td>
 *   </tr>
 * </table>
 *
 * <h3>Circadian Timing</h3>
 * <p>Real brains consolidate on a ~90-minute ultradian cycle during sleep.
 * The {@link CircadianPolicy} controls when the ReflectDaemon runs: either
 * on a fixed interval (e.g., every 30 minutes of wall-clock time) or
 * event-driven (when episodic partition fill reaches a threshold). This
 * mimics the sleep pressure accumulation mechanism (Process S).</p>
 *
 * <h3>V3: IVF Centroid Clustering + LLM Synthesis</h3>
 * <ul>
 *   <li>Groups non-consolidated episodic records by {@code centroid_id}</li>
 *   <li>Processes clusters  >=  {@code minClusterSize} (default: 5)</li>
 *   <li>Extracts common synaptic tags via bitmap AND</li>
 *   <li>When {@code LlmProvider} is available:
 *       sends cluster texts to LLM for factual summarization</li>
 *   <li>When no LLM: falls back to highest-importance selection</li>
 * @deprecated As of 1.3.0, replaced by {@link com.spectrayan.spector.memory.pathway.reflect.ReflectPathway} and its composable relays.
 */
@Deprecated(since = "1.3.0", forRemoval = true)
public final class ReflectDaemon {

    private static final Logger log = LoggerFactory.getLogger(ReflectDaemon.class);

    /** Default minimum cluster size for REM consolidation. */
    private static final int DEFAULT_MIN_CLUSTER_SIZE = com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_REFLECT_MIN_CLUSTER_SIZE;

    private final CircadianPolicy policy;
    private final AtomicBoolean running = new AtomicBoolean(false);

    //  Optional providers (null = graceful fallback to basic behavior) 
    private final CentroidRouter centroidRouter;
    private final LlmProvider textGenerator;
    private final EmbeddingProvider embeddingProvider;
    private final int minClusterSize;

    //  Neurodivergent: Lossless Consolidation 
    private final boolean pinSourceEpisodes;
    private final int pinnedQuota;
    private int pinnedCount = 0; // tracks pinned records across cycles

    /**
     * Creates a ReflectDaemon with full V3 capabilities.
     *
     * @param policy             circadian policy for trigger configuration
     * @param centroidRouter     centroid router for IVF clustering (null = basic fallback)
     * @param textGenerator      LLM for cluster synthesis (null = promote highest importance)
     * @param embeddingProvider  embedding provider for synthesized text (null = skip embedding)
     * @param minClusterSize     minimum cluster size for consolidation (default: 5)
     */
    public ReflectDaemon(CircadianPolicy policy, CentroidRouter centroidRouter,
                          LlmProvider textGenerator, EmbeddingProvider embeddingProvider,
                          int minClusterSize, boolean pinSourceEpisodes, int pinnedQuota) {
        this.policy = policy;
        this.centroidRouter = centroidRouter;
        this.textGenerator = textGenerator;
        this.embeddingProvider = embeddingProvider;
        this.minClusterSize = minClusterSize;
        this.pinSourceEpisodes = pinSourceEpisodes;
        this.pinnedQuota = pinnedQuota;
    }

    /**
     * Creates a ReflectDaemon with full V3 capabilities (no lossless consolidation).
     */
    public ReflectDaemon(CircadianPolicy policy, CentroidRouter centroidRouter,
                          LlmProvider textGenerator, EmbeddingProvider embeddingProvider,
                          int minClusterSize) {
        this(policy, centroidRouter, textGenerator, embeddingProvider,
                minClusterSize, false, 10_000);
    }

    /**
     * Creates a ReflectDaemon with optional V3 providers and default cluster size.
     */
    public ReflectDaemon(CircadianPolicy policy, CentroidRouter centroidRouter,
                          LlmProvider textGenerator, EmbeddingProvider embeddingProvider) {
        this(policy, centroidRouter, textGenerator, embeddingProvider, DEFAULT_MIN_CLUSTER_SIZE);
    }

    /**
     * Creates a ReflectDaemon with basic behavior (no clustering, no LLM).
     */
    public ReflectDaemon(CircadianPolicy policy) {
        this(policy, null, null, null, DEFAULT_MIN_CLUSTER_SIZE);
    }

    /**
     * Creates a ReflectDaemon with default policy.
     */
    public ReflectDaemon() {
        this(CircadianPolicy.DEFAULT);
    }

    /**
     * Runs a single synchronous reflection cycle on a single episodic memory store.
     *
     * @param episodicStore the episodic memory store to scan
     * @param rememberPathway the cognitive ingestion target to promote into
     * @param textLookup optional function to resolve text by offset
     * @return report summarizing what was done
     */
    public ReflectReport runCycle(EpisodicMemory episodicStore,
                                  RememberPathway rememberPathway,
                                  java.util.function.Function<Long, String> textLookup) {
        if (episodicStore == null) {
            return ReflectReport.EMPTY;
        }

        if (!running.compareAndSet(false, true)) {
            log.warn("Reflection cycle already in progress -- skipping");
            return ReflectReport.EMPTY;
        }

        Instant start = Instant.now();
        try {
            int totalConsolidated = reflectEpisodic(episodicStore, rememberPathway);
            Duration elapsed = Duration.between(start, Instant.now());
            return new ReflectReport(totalConsolidated, 0, 0, 0, elapsed);
        } finally {
            running.set(false);
        }
    }

    /**
     * Backward-compatible overload for single episodic memory store without text lookup.
     */
    public ReflectReport runCycle(EpisodicMemory episodicStore, RememberPathway rememberPathway) {
        return runCycle(episodicStore, rememberPathway, null);
    }
    /**
     * Runs a single synchronous reflection cycle across all frozen and active partitions (#446).
     *
     * @param partitionManager the partition manager providing frozen and active partition handles
     * @param rememberPathway the cognitive ingestion target to promote into (active partition)
     * @param index memory index for text lookup
     * @return report summarizing what was done
     */
    public ReflectReport runCycle(PartitionManager partitionManager,
                                   RememberPathway rememberPathway,
                                   MemoryIndex index) {
        if (partitionManager == null) {
            return ReflectReport.EMPTY;
        }

        if (!running.compareAndSet(false, true)) {
            log.warn("Reflection cycle already in progress  --  skipping");
            return ReflectReport.EMPTY;
        }

        Instant start = Instant.now();
        int totalTombstoned = 0;
        int totalCompacted = 0;
        int totalConsolidated = 0;

        try {
            var handles = partitionManager.snapshot();

            // 1. Reflect episodic conversation turns across all partitions
            for (var handle : handles) {
                if (handle.router() != null) {
                    var episodicStore = handle.router().episodic();
                    if (episodicStore != null) {
                        int consolidatedTurns = reflectEpisodic(episodicStore, rememberPathway);
                        totalConsolidated += consolidatedTurns;
                    }
                }
            }

            Duration elapsed = Duration.between(start, Instant.now());
            log.info("Reflection complete across {} partitions: consolidated={}, tombstoned={}, compacted={}, duration={}ms",
                    handles.size(), totalConsolidated, totalTombstoned, totalCompacted, elapsed.toMillis());

            return new ReflectReport(totalConsolidated, totalTombstoned, totalCompacted, 0, elapsed);
        } finally {
            running.set(false);
        }
    }

    private int reflectEpisodic(EpisodicMemory logStore, RememberPathway rememberPathway) {
        if (logStore == null || rememberPathway == null) return 0;
        List<Long> unconsolidatedOffsets = logStore.unconsolidatedTurnOffsets();
        if (unconsolidatedOffsets.isEmpty()) return 0;

        List<EpisodeRecord> turns = logStore.readTurns(unconsolidatedOffsets, true);
        if (turns.isEmpty()) return 0;

        Map<Long, List<EpisodeRecord>> sessionTurns = new HashMap<>();
        Map<EpisodeRecord, Long> turnToOffset = new HashMap<>();
        for (int i = 0; i < turns.size(); i++) {
            var turn = turns.get(i);
            long offset = unconsolidatedOffsets.get(i);
            sessionTurns.computeIfAbsent(turn.sessionId(), k -> new ArrayList<>()).add(turn);
            turnToOffset.put(turn, offset);
        }

        int totalPromoted = 0;
        for (Map.Entry<Long, List<EpisodeRecord>> entry : sessionTurns.entrySet()) {
            List<EpisodeRecord> sessionList = entry.getValue();
            if (sessionList.isEmpty()) continue;

            List<String> turnTexts = new ArrayList<>();
            for (var turn : sessionList) {
                String text = extractTurnText(turn);
                if (text != null && !text.isBlank()) {
                    turnTexts.add(turn.role() + ": " + text);
                }
            }

            if (turnTexts.isEmpty()) continue;

            PromotedFact promoted = null;
            if (textGenerator != null && embeddingProvider != null) {
                promoted = synthesizeWithLlm(turnTexts, 0L, 1.0f);
            }

            if (promoted == null && !turnTexts.isEmpty()) {
                String joinedText = String.join("\n", turnTexts);
                if (joinedText.length() > 500) {
                    joinedText = joinedText.substring(0, 500);
                }
                float[] vec = embeddingProvider != null ? embeddingProvider.embed(joinedText).vector() : new float[rememberPathway.quantizer().dimensions()];
                EncodingHeader header = new EncodingHeader(
                        System.currentTimeMillis(),
                        0L,
                        1.0f,
                        1.0f,
                        1,
                        (short) 0,
                        (byte) 0,
                        EncodingHeaderFields.withMemoryType(EncodingHeaderFields.FLAG_CONSOLIDATED, MemoryType.SEMANTIC.ordinal()),
                        (byte) 0,
                        1.0f
                );
                promoted = new PromotedFact(joinedText, vec, header);
            }

            if (promoted != null && promoted.header() != null) {
                String newId = "rem-log-" + new com.spectrayan.spector.memory.kernel.id.TsidGenerator().generate();
                rememberPathway.ingestCognitiveWithHeader(
                        newId,
                        promoted.text(),
                        promoted.vector(),
                        MemoryType.SEMANTIC,
                        new String[]{"conversation-reflection", "session-" + Long.toHexString(entry.getKey())},
                        MemorySource.REFLECTED,
                        promoted.header()
                );
                totalPromoted++;

                for (var turn : sessionList) {
                    Long off = turnToOffset.get(turn);
                    if (off != null) {
                        logStore.markConsolidated(off);
                    }
                }
            }
        }

        return totalPromoted;
    }

    private String extractTurnText(EpisodeRecord turn) {
        if (turn.body() == null || turn.body().length == 0) return "";
        try {
            return new String(turn.body(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    private record PromotedFact(String text, float[] vector, EncodingHeader header) {}
    private PromotedFact synthesizeWithLlm(List<String> clusterTexts, long commonTags,
                                                float maxImportance) {
        try {
            // Build prompt
            String memoriesText = clusterTexts.stream()
                    .limit(10) // cap at 10 memories to avoid token overflow
                    .collect(Collectors.joining("\n- ", "- ", ""));

            String prompt = String.format(
                    "Summarize these %d related episodic memories into a single factual statement. " +
                    "Be concise and factual.\n\nMemories:\n%s\n\nFactual summary:",
                    clusterTexts.size(), memoriesText);

            String synthesized = textGenerator.generate(prompt, GenerationOptions.CONCISE);

            if (synthesized == null || synthesized.isBlank()) {
                log.warn("REM: LLM returned empty synthesis  --  falling back to selection");
                return null;
            }

            log.debug("REM: LLM synthesized: '{}'", synthesized.substring(0, Math.min(100, synthesized.length())));

            // Build semantic header for the synthesized fact
            // Embed synthesized text to compute exactNorm (if embedding provider available)
            float exactNorm = 1.0f;
            float[] vec = null;
            if (embeddingProvider != null) {
                try {
                    vec = embeddingProvider.embed(synthesized).vector();
                    exactNorm = VectorOps.magnitude(vec);
                } catch (Exception e) {
                    log.warn("REM: Failed to embed synthesized text: {}", e.getMessage());
                }
            }

            byte semanticFlags = EncodingHeaderFields.withMemoryType(
                    EncodingHeaderFields.FLAG_CONSOLIDATED,
                    MemoryType.SEMANTIC.ordinal());

            EncodingHeader header = new EncodingHeader(
                    System.currentTimeMillis(), commonTags, exactNorm, maxImportance,
                    0, (short) 0, (byte) 0, semanticFlags);
            
            return new PromotedFact(synthesized, vec, header);

        } catch (Exception e) {
            log.warn("REM: LLM synthesis failed: {}  --  falling back to selection", e.getMessage());
            return null;
        }
    }

    /**
     * Returns whether a reflection cycle is currently running.
     */
    public boolean isRunning() {
        return running.get();
    }
}
