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
package com.spectrayan.spector.memory.pathway.reflect.relay;

import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.core.similarity.VectorOps;
import com.spectrayan.spector.memory.cortex.EpisodicMemory;
import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.cortex.ProvenanceEdge;
import com.spectrayan.spector.memory.kernel.id.TsidGenerator;
import com.spectrayan.spector.memory.kernel.layout.EncodingHeader;
import com.spectrayan.spector.memory.kernel.layout.EncodingHeaderFields;
import com.spectrayan.spector.memory.model.EpisodeRecord;
import com.spectrayan.spector.memory.model.RememberContext;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.neuromod.neurodivergent.RememberHints;
import com.spectrayan.spector.config.SpectorPropertyConstants;
import com.spectrayan.spector.provider.generation.GenerationOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import com.spectrayan.spector.memory.pathway.reflect.ReflectCheckpoint;
import com.spectrayan.spector.memory.pathway.reflect.ReflectFilter;
import com.spectrayan.spector.memory.pathway.reflect.ReflectSweepSpec;
import com.spectrayan.spector.memory.pathway.reflect.ReflectSweepStatus;
import com.spectrayan.spector.memory.pathway.reflect.SessionSweepResult;
import com.spectrayan.spector.memory.pathway.reflect.SessionWorkItem;
import com.spectrayan.spector.memory.pathway.reflect.spi.ReflectBackpressurePolicy;
import com.spectrayan.spector.memory.pathway.reflect.spi.ReflectCheckpointStore;
import com.spectrayan.spector.memory.pathway.reflect.spi.local.NoopBackpressurePolicy;
import com.spectrayan.spector.memory.session.EpisodicSessionIndex;

/**
 * REM Sleep Conversation Turn Gist Extraction Relay (ADR-0006).
 *
 * <p>Consolidates variable-length conversation turns in {@link EpisodicMemory} into permanent
 * semantic facts via template-driven LLM synthesis or high-salience fallback, and ingests them
 * through {@link com.spectrayan.spector.memory.pathway.remember.RememberPathway}.</p>
 */
public final class EpisodicLogConsolidationRelay implements SynapticRelay<ReflectSignal> {

    private static final Logger log = LoggerFactory.getLogger(EpisodicLogConsolidationRelay.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final int MAX_PRIOR_CONTEXT_TURNS =
            SpectorPropertyConstants.DEFAULT_CONSOLIDATION_REFLECTION_MAX_PRIOR_CONTEXT_TURNS;

    private static final GenerationOptions REFLECTION_GENERATION_OPTIONS = GenerationOptions.builder()
            .temperature(SpectorPropertyConstants.DEFAULT_CONSOLIDATION_REFLECTION_TEMPERATURE)
            .maxTokens(SpectorPropertyConstants.DEFAULT_CONSOLIDATION_REFLECTION_MAX_TOKENS)
            .topP(SpectorPropertyConstants.DEFAULT_CONSOLIDATION_REFLECTION_TOP_P)
            .build();

    public record ConsolidatedFact(
            String text,
            List<String> synapticTags,
            byte valence,
            byte arousal,
            float interest,
            float challenge,
            float urgency
    ) {}

    /**
     * Per-turn offset pairing — avoids HashMap key collision (Bug #2).
     */
    private record TurnWithOffset(EpisodeRecord turn, long offset) {}

    /**
     * Identifies and returns all eligible unconsolidated sessions within a partition,
     * ordered deterministically by timestamp then session ID.
     */
    public List<SessionWorkItem> listEligibleSessions(
            final EpisodicMemory logStore,
            final ReflectSweepSpec spec,
            final ReflectCheckpoint checkpoint,
            final EpisodicSessionIndex index,
            final int partitionSeq) {
        if (logStore == null) {
            return List.of();
        }

        List<Long> unconsolidatedOffsets = logStore.unconsolidatedTurnOffsets();
        if (unconsolidatedOffsets.isEmpty()) {
            return List.of();
        }

        List<EpisodeRecord> turns = logStore.readTurns(unconsolidatedOffsets, true);
        if (turns.isEmpty()) {
            return List.of();
        }

        Map<Long, List<TurnWithOffset>> sessionTurns = new HashMap<>();
        for (int i = 0; i < turns.size(); i++) {
            var turn = turns.get(i);
            long offset = unconsolidatedOffsets.get(i);
            sessionTurns.computeIfAbsent(turn.sessionId(), k -> new ArrayList<>())
                    .add(new TurnWithOffset(turn, offset));
        }

        ReflectFilter filter = (spec != null && spec.filter() != null)
                ? spec.filter()
                : ReflectFilter.unconsolidated();

        List<SessionWorkItem> workItems = new ArrayList<>();
        for (var entry : sessionTurns.entrySet()) {
            long sessionId = entry.getKey();
            List<TurnWithOffset> turnPairs = entry.getValue();
            if (turnPairs == null || turnPairs.isEmpty()) {
                continue;
            }

            long sessionTimestampMs = turnPairs.stream()
                    .map(TurnWithOffset::turn)
                    .mapToLong(EpisodeRecord::timestampMs)
                    .max()
                    .orElse(0L);

            if (filter != null && !filter.matches(sessionId, sessionTimestampMs, partitionSeq)) {
                continue;
            }

            List<Long> offsets = new ArrayList<>(turnPairs.size());
            for (TurnWithOffset pair : turnPairs) {
                offsets.add(pair.offset());
            }

            workItems.add(new SessionWorkItem(partitionSeq, sessionId, offsets, sessionTimestampMs));
        }

        // Stable deterministic ordering by (timestampMs, sessionId)
        workItems.sort(Comparator.comparingLong(SessionWorkItem::timestampMs)
                .thenComparingLong(SessionWorkItem::sessionId));

        return workItems;
    }

    /**
     * Executes the unit-of-work for a single session: distillation, crash-safe marking,
     * semantic fact ingestion with affect metadata, and provenance tracking.
     */
    public SessionSweepResult processSession(
            final SessionWorkItem workItem,
            final EpisodicMemory logStore,
            final ReflectSignal signal) {
        if (workItem == null || workItem.offsets().isEmpty() || logStore == null || signal == null) {
            return SessionSweepResult.success(workItem != null ? workItem.sessionId() : 0L, 0, 0);
        }

        long sessionId = workItem.sessionId();
        int partitionSeq = workItem.partitionSeq();
        List<Long> offsets = workItem.offsets();

        int maxTurns = (signal.sweepSpec() != null)
                ? signal.sweepSpec().maxTurnsPerSession()
                : ReflectSweepSpec.DEFAULT_MAX_TURNS_PER_SESSION;
        if (maxTurns > 0 && offsets.size() > maxTurns) {
            offsets = offsets.subList(offsets.size() - maxTurns, offsets.size());
        }

        try {
            List<EpisodeRecord> turns = logStore.readTurns(offsets, true);
            if (turns.isEmpty()) {
                return SessionSweepResult.success(sessionId, 0, 0);
            }

            List<String> turnTexts = new ArrayList<>();
            for (EpisodeRecord turn : turns) {
                String text = extractTurnText(turn);
                if (text != null && !text.isBlank()) {
                    turnTexts.add(turn.role() + ": " + text);
                }
            }

            if (turnTexts.isEmpty()) {
                return SessionSweepResult.success(sessionId, 0, 0);
            }

            long sessionTimestampMs = workItem.timestampMs();
            Set<Long> currentTurnOffsets = new HashSet<>(offsets);

            List<String> priorContext = new ArrayList<>();
            if (MAX_PRIOR_CONTEXT_TURNS > 0) {
                List<Long> windowOffsets = null;
                if (signal.episodicSessionIndex() != null) {
                    List<Long> allSessionOffsets = signal.episodicSessionIndex().getSessionTurns(sessionId);
                    if (allSessionOffsets != null && !allSessionOffsets.isEmpty()) {
                        List<Long> earlierOffsets = new ArrayList<>();
                        for (Long off : allSessionOffsets) {
                            if (!currentTurnOffsets.contains(off)) {
                                earlierOffsets.add(off);
                            }
                        }
                        if (!earlierOffsets.isEmpty()) {
                            int start = Math.max(0, earlierOffsets.size() - MAX_PRIOR_CONTEXT_TURNS);
                            windowOffsets = earlierOffsets.subList(start, earlierOffsets.size());
                        }
                    }
                } else if (logStore != null) {
                    // Fallback: directly scan episodic slab when EpisodicSessionIndex is unavailable (#751)
                    List<Long> candidateOffsets = logStore.lastConsolidatedTurnOffsets(sessionId, MAX_PRIOR_CONTEXT_TURNS);
                    if (candidateOffsets != null && !candidateOffsets.isEmpty()) {
                        List<Long> earlierOffsets = new ArrayList<>();
                        for (Long off : candidateOffsets) {
                            if (!currentTurnOffsets.contains(off)) {
                                earlierOffsets.add(off);
                            }
                        }
                        windowOffsets = earlierOffsets;
                    }
                }

                if (windowOffsets != null && !windowOffsets.isEmpty()) {
                    List<EpisodeRecord> priorRecords = logStore.readTurns(windowOffsets, true);
                    for (var priorRecord : priorRecords) {
                        if (EncodingHeaderFields.isConsolidated(priorRecord.flags())) {
                            String text = extractTurnText(priorRecord);
                            if (text != null && !text.isBlank()) {
                                priorContext.add(priorRecord.role() + ": " + text);
                            }
                        }
                    }
                }
            }

            List<ConsolidatedFact> synthesizedFacts = distillStructuredFacts(turnTexts, priorContext, sessionTimestampMs, signal);
            if (synthesizedFacts.isEmpty()) {
                return SessionSweepResult.success(sessionId, 0, 0);
            }

            // Bug #5 fix: Mark turns consolidated BEFORE ingesting facts to prevent duplicate facts on crash/retry.
            for (Long off : offsets) {
                logStore.markConsolidated(off);
            }
            signal.addLogTurnsConsolidated(offsets.size());

            // Compute pass number for this session (supports multi-pass consolidation)
            short passNumber = 1;
            if (signal.provenanceMemory() != null) {
                passNumber = (short) signal.provenanceMemory().nextPassNumber(sessionId);
            }

            int firstSeq = Integer.MAX_VALUE, lastSeq = Integer.MIN_VALUE;
            int firstOffsetHint = Integer.MAX_VALUE, lastOffsetHint = Integer.MIN_VALUE;
            for (int i = 0; i < turns.size(); i++) {
                int seq = turns.get(i).sequenceId();
                int off = (int) offsets.get(i).longValue();
                if (seq < firstSeq) { firstSeq = seq; firstOffsetHint = off; }
                if (seq > lastSeq) { lastSeq = seq; lastOffsetHint = off; }
            }
            short turnCount = (short) offsets.size();

            int factsIngested = 0;
            if (signal.rememberPathway() != null) {
                for (int fi = 0; fi < synthesizedFacts.size(); fi++) {
                    ConsolidatedFact fact = synthesizedFacts.get(fi);
                    String memoryId = signal.idGenerator().generate();
                    float[] vector = null;
                    if (signal.embeddingProvider() != null) {
                        try {
                            vector = signal.embeddingProvider().embed(fact.text()).vector();
                        } catch (Exception e) {
                            log.warn("Failed to embed synthesized reflection fact: {}", e.getMessage());
                        }
                    }

                    Set<String> tagSet = new LinkedHashSet<>();
                    if (fact.synapticTags() != null) {
                        for (String t : fact.synapticTags()) {
                            String cleanT = t.replaceAll("[^a-zA-Z0-9_-]", "-").toLowerCase(Locale.ROOT);
                            if (!cleanT.isBlank()) {
                                tagSet.add(cleanT);
                            }
                        }
                    }
                    tagSet.add("conversation-reflection");
                    tagSet.add("session-" + Long.toHexString(sessionId));
                    String[] allTags = tagSet.toArray(String[]::new);

                    float exactNorm = vector != null ? VectorOps.magnitude(vector) : 1.0f;
                    byte semanticFlags = EncodingHeaderFields.withMemoryType(
                            EncodingHeaderFields.FLAG_CONSOLIDATED, MemoryType.SEMANTIC.ordinal());

                    EncodingHeader header = new EncodingHeader(
                            sessionTimestampMs, 0L, exactNorm, fact.interest(), 1,
                            (short) 0, fact.arousal(), semanticFlags, fact.valence(), 1.0f
                    );

                    boolean ingested = signal.rememberPathway().ingestCognitiveWithHeader(
                            memoryId,
                            fact.text(),
                            vector,
                            MemoryType.SEMANTIC,
                            allTags,
                            MemorySource.REFLECTED,
                            header
                    );

                    if (ingested) {
                        signal.addConsolidated(1);
                        factsIngested++;

                        if (signal.provenanceMemory() != null) {
                            try {
                                long targetTsid = TsidGenerator.decodeCrockford(memoryId);
                                short contentHashHi = computeContentHashHi(fact.text());

                                ProvenanceEdge edge = new ProvenanceEdge(
                                        sessionId, targetTsid, passNumber,
                                        (byte) fi, (byte) synthesizedFacts.size(),
                                        partitionSeq, firstSeq, lastSeq,
                                        firstOffsetHint, lastOffsetHint,
                                        turnCount, contentHashHi,
                                        System.currentTimeMillis()
                                );
                                signal.provenanceMemory().append(edge);
                            } catch (Exception e) {
                                log.warn("Failed to write provenance edge for memory {}: {}", memoryId, e.getMessage());
                            }
                        }
                    }
                }
            }

            return SessionSweepResult.success(sessionId, factsIngested, offsets.size());
        } catch (Exception e) {
            log.warn("Unexpected error consolidating session {}: {}", sessionId, e.getMessage());
            if (signal.sweepSpec() != null && signal.sweepSpec().backpressure() != null) {
                signal.sweepSpec().backpressure().onProviderFailure(e);
            }
            return SessionSweepResult.failure(sessionId, e);
        }
    }

    @Override
    public boolean transmit(final ReflectSignal signal) {
        if (signal.partitionManager() == null) {
            log.info("EpisodicLogConsolidationRelay: partitionManager is null");
            return true;
        }

        ReflectSweepSpec spec = signal.sweepSpec() != null ? signal.sweepSpec() : ReflectSweepSpec.fullCycle();
        ReflectCheckpointStore checkpointStore = signal.checkpointStore();
        ReflectCheckpoint checkpoint = signal.checkpoint();

        if (checkpoint == null && checkpointStore != null) {
            checkpoint = checkpointStore.load(spec.sweepId()).orElse(ReflectCheckpoint.initial(spec.sweepId()));
            signal.setCheckpoint(checkpoint);
        }

        ReflectBackpressurePolicy backpressure = spec.backpressure() != null
                ? spec.backpressure()
                : NoopBackpressurePolicy.INSTANCE;

        int sessionLimit = spec.sessionLimit();
        Duration timeBudget = spec.timeBudget();
        long deadlineMs = (timeBudget != null) ? System.currentTimeMillis() + timeBudget.toMillis() : Long.MAX_VALUE;

        var handles = signal.partitionManager().snapshot();
        log.info("EpisodicLogConsolidationRelay: snapshot has {} partition handles (sweep='{}', limit={})",
                handles.size(), spec.sweepId(), sessionLimit);

        int totalSessionsProcessed = 0;
        int totalFactsIngested = 0;
        int totalTurnsMarked = 0;
        boolean aborted = false;

        for (var handle : handles) {
            if (handle.router() == null || handle.router().episodic() == null) {
                continue;
            }

            var episodicStore = handle.router().episodic();
            List<SessionWorkItem> eligibleSessions = listEligibleSessions(
                    episodicStore, spec, checkpoint, signal.episodicSessionIndex(), handle.seq());

            log.info("EpisodicLogConsolidationRelay: partition #{} has {} eligible sessions",
                    handle.seq(), eligibleSessions.size());

            int remainingBacklog = eligibleSessions.size();

            for (SessionWorkItem item : eligibleSessions) {
                if (sessionLimit > 0 && totalSessionsProcessed >= sessionLimit) {
                    log.info("EpisodicLogConsolidationRelay: reached sessionLimit ({}), pausing sweep", sessionLimit);
                    break;
                }

                if (System.currentTimeMillis() >= deadlineMs) {
                    log.info("EpisodicLogConsolidationRelay: reached timeBudget ({}), pausing sweep", timeBudget);
                    aborted = true;
                    break;
                }

                if (backpressure.shouldAbortSweep()) {
                    log.warn("EpisodicLogConsolidationRelay: backpressure policy signaled abort");
                    aborted = true;
                    break;
                }

                try {
                    backpressure.beforeSession(item);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.warn("EpisodicLogConsolidationRelay: interrupted during backpressure wait");
                    aborted = true;
                    break;
                }

                SessionSweepResult result = processSession(item, episodicStore, signal);
                if (result.success()) {
                    totalSessionsProcessed++;
                    totalFactsIngested += result.factsConsolidated();
                    totalTurnsMarked += result.turnsMarked();
                    remainingBacklog--;

                    long lastOffset = item.offsets().isEmpty() ? 0L : item.offsets().get(item.offsets().size() - 1);
                    checkpoint = new ReflectCheckpoint(
                            spec.sweepId(),
                            handle.seq(),
                            item.sessionId(),
                            lastOffset,
                            totalSessionsProcessed,
                            totalFactsIngested,
                            totalTurnsMarked,
                            remainingBacklog,
                            Instant.now(),
                            ReflectSweepStatus.RUNNING
                    );
                    signal.setCheckpoint(checkpoint);

                    if (checkpointStore != null) {
                        try {
                            checkpointStore.save(checkpoint);
                        } catch (Exception e) {
                            log.warn("Failed saving checkpoint for session {}: {}", item.sessionId(), e.getMessage());
                        }
                    }
                }
            }

            if (aborted || (sessionLimit > 0 && totalSessionsProcessed >= sessionLimit)) {
                break;
            }
        }

        ReflectSweepStatus finalStatus = aborted
                ? ReflectSweepStatus.PAUSED
                : ((sessionLimit > 0 && totalSessionsProcessed >= sessionLimit)
                ? ReflectSweepStatus.PAUSED
                : ReflectSweepStatus.COMPLETE);

        if (checkpoint != null) {
            checkpoint = new ReflectCheckpoint(
                    checkpoint.sweepId(),
                    checkpoint.partitionSeq(),
                    checkpoint.lastCompletedSessionId(),
                    checkpoint.lastCompletedTurnOffset(),
                    checkpoint.sessionsCompleted(),
                    checkpoint.factsIngested(),
                    checkpoint.turnsMarked(),
                    checkpoint.backlogRemaining(),
                    Instant.now(),
                    finalStatus
            );
            signal.setCheckpoint(checkpoint);
            if (checkpointStore != null) {
                checkpointStore.save(checkpoint);
            }
        }

        log.info("EpisodicLogConsolidationRelay: sweep '{}' completed — status={}, sessions={}, facts={}, turns={}",
                spec.sweepId(), finalStatus, totalSessionsProcessed, totalFactsIngested, totalTurnsMarked);

        return true;
    }

    private List<ConsolidatedFact> distillStructuredFacts(List<String> turnTexts, List<String> priorContext, long sessionTimestampMs, ReflectSignal signal) {
        String sessionDateStr;
        if (sessionTimestampMs > 0) {
            sessionDateStr = DateTimeFormatter.ofPattern("dd MMMM yyyy")
                    .withZone(ZoneOffset.UTC)
                    .format(Instant.ofEpochMilli(sessionTimestampMs));
        } else {
            sessionDateStr = "Unknown Date";
        }

        if (signal.textGenerator() != null && signal.templateEngine() != null) {
            try {
                Map<String, Object> model = new HashMap<>();
                model.put("memoryCount", turnTexts.size());
                model.put("sessionDate", sessionDateStr);
                model.put("memories", turnTexts);
                boolean hasPrior = priorContext != null && !priorContext.isEmpty();
                model.put("hasPriorContext", hasPrior);
                model.put("priorContext", hasPrior ? priorContext : List.of());
                model.put("priorContextCount", hasPrior ? priorContext.size() : 0);

                String prompt = signal.templateEngine().render("prompts/reflection-synthesis", model);
                String response = signal.textGenerator().generate(prompt, REFLECTION_GENERATION_OPTIONS);

                if (response != null && !response.isBlank()) {
                    List<ConsolidatedFact> parsedFacts = parseJsonResponse(response);
                    if (!parsedFacts.isEmpty()) {
                        return parsedFacts;
                    }
                    List<ConsolidatedFact> lineFacts = parseLineResponse(response);
                    if (!lineFacts.isEmpty()) {
                        return lineFacts;
                    }
                }
            } catch (Exception e) {
                log.warn("Reflection distillation call failed: {}", e.getMessage());
                if (signal.sweepSpec() != null && signal.sweepSpec().backpressure() != null) {
                    signal.sweepSpec().backpressure().onProviderFailure(e);
                }
            }
        }

        // Fallback: concatenate excerpt
        String joined = String.join("\n", turnTexts);
        if (joined.length() > 500) {
            joined = joined.substring(0, 500);
        }
        return List.of(new ConsolidatedFact(joined, List.of("general-conversation"), (byte) 0, (byte) 0, 0.5f, 0.0f, 0.0f));
    }

    private List<ConsolidatedFact> parseJsonResponse(String rawResponse) {
        try {
            String cleaned = rawResponse.trim();
            if (cleaned.startsWith("```json")) {
                cleaned = cleaned.substring(7);
            } else if (cleaned.startsWith("```")) {
                cleaned = cleaned.substring(3);
            }
            if (cleaned.endsWith("```")) {
                cleaned = cleaned.substring(0, cleaned.length() - 3);
            }
            cleaned = cleaned.trim();

            JsonNode root = MAPPER.readTree(cleaned);
            JsonNode factsNode = root.has("facts") ? root.get("facts") : (root.isArray() ? root : null);
            if (factsNode != null && factsNode.isArray()) {
                List<ConsolidatedFact> list = new ArrayList<>();
                for (JsonNode node : factsNode) {
                    String text = node.has("text") ? node.get("text").asText("").trim() : "";
                    if (text.isBlank() || text.length() < 10) continue;

                    List<String> tags = new ArrayList<>();
                    if (node.has("synapticTags") && node.get("synapticTags").isArray()) {
                        for (JsonNode tNode : node.get("synapticTags")) {
                            String t = tNode.asText("").trim().toLowerCase(Locale.ROOT);
                            if (!t.isBlank()) tags.add(t);
                        }
                    }

                    int valInt = node.has("valence") ? node.get("valence").asInt(0) : 0;
                    byte valence = (byte) Math.clamp(valInt, -128, 127);

                    int arInt = node.has("arousal") ? node.get("arousal").asInt(0) : 0;
                    byte arousal = (byte) Math.clamp(arInt, 0, 255);

                    float interest = node.has("interest") ? (float) Math.clamp(node.get("interest").asDouble(0.5), 0.0, 1.0) : 0.5f;
                    float challenge = node.has("challenge") ? (float) Math.clamp(node.get("challenge").asDouble(0.0), 0.0, 1.0) : 0.0f;
                    float urgency = node.has("urgency") ? (float) Math.clamp(node.get("urgency").asDouble(0.0), 0.0, 1.0) : 0.0f;

                    list.add(new ConsolidatedFact(text, tags, valence, arousal, interest, challenge, urgency));
                }
                return list;
            }
        } catch (Exception e) {
            log.debug("Failed to parse JSON reflection response: {}", e.getMessage());
        }
        return List.of();
    }

    private List<ConsolidatedFact> parseLineResponse(String response) {
        List<ConsolidatedFact> facts = new ArrayList<>();
        String[] lines = response.split("\n");
        for (String line : lines) {
            String clean = line.strip();
            if (clean.startsWith("- ") || clean.startsWith("* ")) {
                clean = clean.substring(2).strip();
            } else if (clean.matches("^\\d+\\.\\s+.*")) {
                clean = clean.replaceFirst("^\\d+\\.\\s+", "").strip();
            }
            if (!clean.isBlank() && !clean.equalsIgnoreCase("Factual summary:")
                    && !clean.equalsIgnoreCase("Distilled Semantic Facts:")
                    && !clean.equalsIgnoreCase("Extracted Facts:")
                    && !clean.startsWith("###")
                    && !clean.startsWith("{") && !clean.startsWith("}")
                    && !clean.startsWith("[") && !clean.startsWith("]")
                    && !clean.startsWith("\"synapticTags\"")
                    && !clean.startsWith("\"text\"")
                    && !clean.startsWith("\"valence\"")
                    && !clean.startsWith("\"arousal\"")
                    && !clean.startsWith("\"importance\"")
                    && !clean.startsWith("\"interest\"")
                    && !clean.startsWith("\"challenge\"")
                    && !clean.startsWith("\"urgency\"")
                    && !clean.startsWith("```")) {
                facts.add(new ConsolidatedFact(clean, List.of("conversation-reflection"), (byte) 0, (byte) 0, 0.5f, 0.0f, 0.0f));
            }
        }
        return facts;
    }

    private String extractTurnText(EpisodeRecord turn) {
        if (turn.body() == null || turn.body().length == 0) return "";
        try {
            return new String(turn.body(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Computes the upper 16 bits of a CRC32C hash of the fact text.
     *
     * <p>Used as a compact deduplication aid in provenance records —
     * two facts with different content will almost certainly have
     * different {@code contentHashHi} values.</p>
     */
    private static short computeContentHashHi(String text) {
        if (text == null || text.isEmpty()) return 0;
        java.util.zip.CRC32C crc = new java.util.zip.CRC32C();
        crc.update(text.getBytes(StandardCharsets.UTF_8));
        return (short) ((crc.getValue() >>> 16) & 0xFFFF);
    }
}
