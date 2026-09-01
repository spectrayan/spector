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
package com.spectrayan.spector.memory.reflect.relay;

import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.core.similarity.VectorOps;
import com.spectrayan.spector.memory.cortex.EpisodicLogMemory;
import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.id.TsidGenerator;
import com.spectrayan.spector.memory.kernel.layout.CognitiveRecordLayout.CognitiveHeader;
import com.spectrayan.spector.memory.kernel.layout.SynapticHeaderConstants;
import com.spectrayan.spector.memory.kernel.layout.EpisodicFieldAccessor;
import com.spectrayan.spector.memory.model.IngestionContext;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.neurodivergent.IngestionHints;
import com.spectrayan.spector.config.SpectorPropertyConstants;
import com.spectrayan.spector.provider.generation.GenerationOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * REM Sleep Conversation Turn Gist Extraction Relay (ADR-0006).
 *
 * <p>Consolidates variable-length conversation turns in {@link EpisodicLogMemory} into permanent
 * semantic facts via template-driven LLM synthesis or high-salience fallback, and ingests them
 * through {@link com.spectrayan.spector.memory.pathway.RememberPathway}.</p>
 */
public final class EpisodicLogConsolidationRelay implements SynapticRelay<ReflectSignal> {

    private static final Logger log = LoggerFactory.getLogger(EpisodicLogConsolidationRelay.class);
    private static final TsidGenerator TSID = new TsidGenerator();
    private static final ObjectMapper MAPPER = new ObjectMapper();

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

    @Override
    public boolean transmit(final ReflectSignal signal) {
        if (signal.partitionManager() == null) {
            log.info("EpisodicLogConsolidationRelay: partitionManager is null");
            return true;
        }

        var handles = signal.partitionManager().snapshot();
        log.info("EpisodicLogConsolidationRelay: snapshot has {} partition handles", handles.size());
        for (var handle : handles) {
            boolean isLogMode = handle.router() != null && handle.router().isEpisodicLogMode();
            log.info("Handle seq={} router isLogMode={}", handle.seq(), isLogMode);
            if (isLogMode) {
                var logStore = handle.router().episodicLog();
                if (logStore != null) {
                    processLogStore(logStore, signal);
                }
            }
        }
        return true;
    }

    private void processLogStore(final EpisodicLogMemory logStore, final ReflectSignal signal) {
        List<Long> unconsolidatedOffsets = logStore.unconsolidatedTurnOffsets();
        log.info("EpisodicLogConsolidationRelay: found {} unconsolidated offsets in logStore", unconsolidatedOffsets.size());
        List<EpisodicFieldAccessor.EpisodicRecord> turns = logStore.readTurns(unconsolidatedOffsets, true);
        if (turns.isEmpty()) return;
        log.info("EpisodicLogConsolidationRelay: read {} turns for {} unconsolidated offsets", turns.size(), unconsolidatedOffsets.size());
        Map<Long, List<EpisodicFieldAccessor.EpisodicRecord>> sessionTurns = new HashMap<>();
        Map<EpisodicFieldAccessor.EpisodicRecord, Long> turnToOffset = new HashMap<>();
        for (int i = 0; i < turns.size(); i++) {
            var turn = turns.get(i);
            long offset = unconsolidatedOffsets.get(i);
            sessionTurns.computeIfAbsent(turn.sessionId(), k -> new ArrayList<>()).add(turn);
            turnToOffset.put(turn, offset);
        }

        log.info("EpisodicLogConsolidationRelay: grouped into {} distinct sessions, starting parallel consolidation...", sessionTurns.size());
        sessionTurns.entrySet().parallelStream().forEach(entry -> {
            List<EpisodicFieldAccessor.EpisodicRecord> sessionList = entry.getValue();
            if (sessionList.isEmpty()) return;

            List<String> turnTexts = new ArrayList<>();
            for (var turn : sessionList) {
                String text = extractTurnText(turn);
                if (text != null && !text.isBlank()) {
                    turnTexts.add(turn.role() + ": " + text);
                }
            }

            if (turnTexts.isEmpty()) return;

            long sessionTimestampMs = sessionList.get(0).timestampMs();
            List<ConsolidatedFact> synthesizedFacts = distillStructuredFacts(turnTexts, sessionTimestampMs, signal);
            if (synthesizedFacts.isEmpty()) return;

            for (ConsolidatedFact fact : synthesizedFacts) {
                String memoryId = "rem-log-" + TSID.generate();
                float[] vector = null;
                if (signal.embeddingProvider() != null) {
                    try {
                        vector = signal.embeddingProvider().embed(fact.text()).vector();
                    } catch (Exception e) {
                        log.warn("Failed to embed synthesized reflection fact: {}", e.getMessage());
                    }
                }

                if (signal.rememberPathway() != null) {
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
                    tagSet.add("session-" + Long.toHexString(entry.getKey()));
                    String[] allTags = tagSet.toArray(String[]::new);

                    float exactNorm = vector != null ? VectorOps.magnitude(vector) : 1.0f;
                    byte semanticFlags = SynapticHeaderConstants.withMemoryType(
                            SynapticHeaderConstants.FLAG_CONSOLIDATED, MemoryType.SEMANTIC.ordinal());
                    CognitiveHeader header = new CognitiveHeader(
                            sessionTimestampMs, 0L, exactNorm, 1.0f, 1,
                            (short) 0, (byte) 0, semanticFlags, (byte) 0, 1.0f
                    );

                    signal.rememberPathway().ingestCognitiveWithHeader(
                            memoryId,
                            fact.text(),
                            vector,
                            MemoryType.SEMANTIC,
                            allTags,
                            MemorySource.REFLECTED,
                            header
                    );
                    signal.addConsolidated(1);
                }
            }

            // Mark source turns consolidated
            for (var turn : sessionList) {
                Long offset = turnToOffset.get(turn);
                if (offset != null) {
                    logStore.markConsolidated(offset);
                }
            }
            signal.addLogTurnsConsolidated(sessionList.size());
        });
        log.info("EpisodicLogConsolidationRelay: completed session distillation, consolidated {} facts across {} sessions",
                signal.totalConsolidated(), sessionTurns.size());
    }

    private List<ConsolidatedFact> distillStructuredFacts(List<String> turnTexts, long sessionTimestampMs, ReflectSignal signal) {
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
                Map<String, Object> model = Map.of(
                        "memoryCount", turnTexts.size(),
                        "sessionDate", sessionDateStr,
                        "memories", turnTexts
                );
                String prompt = signal.templateEngine().render("prompts/reflection-synthesis", model);
                String response = signal.textGenerator().generate(prompt, REFLECTION_GENERATION_OPTIONS);

                if (response != null && !response.isBlank()) {
                    List<ConsolidatedFact> parsedFacts = parseJsonResponse(response);
                    if (!parsedFacts.isEmpty()) {
                        return parsedFacts;
                    }
                    return parseLineResponse(response);
                }
            } catch (Exception e) {
                log.warn("LLM template-based reflection synthesis failed, using fallback: {}", e.getMessage());
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

    private String extractTurnText(EpisodicFieldAccessor.EpisodicRecord turn) {
        if (turn.body() == null || turn.body().length == 0) return "";
        try {
            return new String(turn.body(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }
}
