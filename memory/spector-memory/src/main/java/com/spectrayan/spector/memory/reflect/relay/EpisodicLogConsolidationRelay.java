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
import com.spectrayan.spector.memory.kernel.layout.EpisodicFieldAccessor;
import com.spectrayan.spector.memory.kernel.layout.SynapticHeaderConstants;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.provider.generation.GenerationOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REM Sleep Conversation Turn Gist Extraction Relay (ADR-0006).
 *
 * <p>Consolidates variable-length conversation turns in {@link EpisodicLogMemory} into permanent
 * semantic facts via template-driven LLM synthesis or high-salience fallback, and ingests them
 * through {@link com.spectrayan.spector.memory.RememberPathway}.</p>
 */
public final class EpisodicLogConsolidationRelay implements SynapticRelay<ReflectSignal> {

    private static final Logger log = LoggerFactory.getLogger(EpisodicLogConsolidationRelay.class);
    private static final TsidGenerator TSID = new TsidGenerator();

    @Override
    public boolean transmit(final ReflectSignal signal) {
        if (signal.partitionManager() == null) {
            return true;
        }

        var handles = signal.partitionManager().snapshot();
        for (var handle : handles) {
            if (handle.router() != null && handle.router().isEpisodicLogMode()) {
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
        if (unconsolidatedOffsets.isEmpty()) return;

        List<EpisodicFieldAccessor.EpisodicRecord> turns = logStore.readTurns(unconsolidatedOffsets, true);
        if (turns.isEmpty()) return;

        Map<Long, List<EpisodicFieldAccessor.EpisodicRecord>> sessionTurns = new HashMap<>();
        Map<EpisodicFieldAccessor.EpisodicRecord, Long> turnToOffset = new HashMap<>();
        for (int i = 0; i < turns.size(); i++) {
            var turn = turns.get(i);
            long offset = unconsolidatedOffsets.get(i);
            sessionTurns.computeIfAbsent(turn.sessionId(), k -> new ArrayList<>()).add(turn);
            turnToOffset.put(turn, offset);
        }

        for (Map.Entry<Long, List<EpisodicFieldAccessor.EpisodicRecord>> entry : sessionTurns.entrySet()) {
            List<EpisodicFieldAccessor.EpisodicRecord> sessionList = entry.getValue();
            if (sessionList.isEmpty()) continue;

            List<String> turnTexts = new ArrayList<>();
            for (var turn : sessionList) {
                String text = extractTurnText(turn);
                if (text != null && !text.isBlank()) {
                    turnTexts.add(turn.role() + ": " + text);
                }
            }

            if (turnTexts.isEmpty()) continue;

            List<String> synthesizedFacts = distillFacts(turnTexts, signal);
            if (synthesizedFacts.isEmpty()) continue;

            String[] tags = new String[]{"conversation-reflection", "session-" + Long.toHexString(entry.getKey())};
            for (String factText : synthesizedFacts) {
                String memoryId = "rem-log-" + TSID.generate();
                float[] vector = null;
                if (signal.embeddingProvider() != null) {
                    try {
                        vector = signal.embeddingProvider().embed(factText).vector();
                    } catch (Exception e) {
                        log.warn("Failed to embed synthesized reflection fact: {}", e.getMessage());
                    }
                }

                if (signal.rememberPathway() != null) {
                    float exactNorm = vector != null ? VectorOps.magnitude(vector) : 1.0f;
                    byte semanticFlags = SynapticHeaderConstants.withMemoryType(
                            SynapticHeaderConstants.FLAG_CONSOLIDATED, MemoryType.SEMANTIC.ordinal());
                    CognitiveHeader header = new CognitiveHeader(
                            System.currentTimeMillis(), 0L, exactNorm, 1.0f, 1,
                            (short) 0, (byte) 0, semanticFlags, (byte) 0, 1.0f
                    );
                    signal.rememberPathway().ingestCognitiveWithHeader(
                            memoryId, factText, vector, MemoryType.SEMANTIC, tags, MemorySource.REFLECTED, header
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
        }
    }

    private List<String> distillFacts(List<String> turnTexts, ReflectSignal signal) {
        if (signal.textGenerator() != null && signal.templateEngine() != null) {
            try {
                Map<String, Object> model = Map.of(
                        "memoryCount", turnTexts.size(),
                        "memories", turnTexts
                );
                String prompt = signal.templateEngine().render("prompts/reflection-synthesis", model);
                String response = signal.textGenerator().generate(prompt, GenerationOptions.CONCISE);

                if (response != null && !response.isBlank()) {
                    List<String> facts = new ArrayList<>();
                    String[] lines = response.split("\n");
                    for (String line : lines) {
                        String clean = line.strip();
                        if (clean.startsWith("- ") || clean.startsWith("* ")) {
                            clean = clean.substring(2).strip();
                        } else if (clean.matches("^\\d+\\.\\s+.*")) {
                            clean = clean.replaceFirst("^\\d+\\.\\s+", "").strip();
                        }
                        if (!clean.isBlank() && !clean.equalsIgnoreCase("Factual summary:")
                                && !clean.equalsIgnoreCase("Distilled Semantic Facts:")) {
                            facts.add(clean);
                        }
                    }
                    if (!facts.isEmpty()) {
                        return facts;
                    }
                    return List.of(response.strip());
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
        return List.of(joined);
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
