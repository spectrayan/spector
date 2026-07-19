/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-synapse/LICENSE
 *
 * Change Date: July 6, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.synapse.observability;

import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.model.CognitiveRecord;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.model.ScoreBreakdown;
import com.spectrayan.spector.memory.cortex.MemorySource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

/**
 * REST controller for exposing memory observability and glass-box metrics.
 */
@RestController
@RequestMapping("/api/v1/observability")
public class ObservabilityController {

    private static final Logger log = LoggerFactory.getLogger(ObservabilityController.class);

    private final ObjectProvider<SpectorMemory> memoryProvider;

    public ObservabilityController(ObjectProvider<SpectorMemory> memoryProvider) {
        this.memoryProvider = memoryProvider;
        log.info("ObservabilityController initialized");
    }

    /**
     * Returns aggregate memory statistics.
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> stats() {
        SpectorMemory memory = memoryProvider.getIfAvailable();
        if (memory == null) {
            return ResponseEntity.status(503).body(Map.of("error", "Memory engine not available"));
        }

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalMemories", memory.totalMemories());

        var index = memory.admin().index();
        if (index != null) {
            stats.put("indexedMemories", index.size());
        } else {
            stats.put("indexedMemories", 0);
        }

        var tierCounts = new LinkedHashMap<String, Integer>();
        tierCounts.put("WORKING", memory.memoryCount(MemoryType.WORKING));
        tierCounts.put("EPISODIC", memory.memoryCount(MemoryType.EPISODIC));
        tierCounts.put("SEMANTIC", memory.memoryCount(MemoryType.SEMANTIC));
        tierCounts.put("PROCEDURAL", memory.memoryCount(MemoryType.PROCEDURAL));
        stats.put("tierDistribution", tierCounts);

        return ResponseEntity.ok(stats);
    }

    /**
     * Returns chronological memory events for timeline visualization.
     */
    @GetMapping("/timeline")
    public ResponseEntity<Map<String, Object>> timeline(@RequestParam(required = false) String from,
                                                         @RequestParam(required = false) String to,
                                                         @RequestParam(defaultValue = "100") int limit) {
        SpectorMemory memory = memoryProvider.getIfAvailable();
        if (memory == null) {
            return ResponseEntity.status(503).body(Map.of("error", "Memory engine not available"));
        }

        List<CognitiveRecord> records = memory.admin().listAll();
        records.sort((a, b) -> Long.compare(b.timestampMs(), a.timestampMs())); // desc

        int effectiveLimit = Math.min(limit, 1000);
        List<Map<String, Object>> eventList = new ArrayList<>();

        for (CognitiveRecord rec : records) {
            if (eventList.size() >= effectiveLimit) break;
            
            Instant recTime = Instant.ofEpochMilli(rec.timestampMs());
            if (from != null && recTime.toString().compareTo(from) < 0) continue;
            if (to != null && recTime.toString().compareTo(to) > 0) continue;

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("eventType", "CREATED");
            entry.put("memoryId", rec.id());
            entry.put("namespace", "default");
            entry.put("timestamp", recTime.toString());
            
            Map<String, Object> metadata = new LinkedHashMap<>();
            String rawText = rec.text() != null ? rec.text() : "";
            metadata.put("text", rawText.length() > 200 ? rawText.substring(0, 197) + "..." : rawText);
            entry.put("metadata", metadata);

            eventList.add(entry);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("events", eventList);
        response.put("totalEvents", eventList.size());

        return ResponseEntity.ok(response);
    }

    /**
     * Returns memory age distribution for histogram visualization.
     */
    @GetMapping("/age-distribution")
    public ResponseEntity<Map<String, Object>> ageDistribution() {
        SpectorMemory memory = memoryProvider.getIfAvailable();
        if (memory == null) {
            return ResponseEntity.status(503).body(Map.of("error", "Memory engine not available"));
        }

        List<CognitiveRecord> records = memory.admin().listAll();

        long now = System.currentTimeMillis();
        int[] bucketCounts = new int[6]; // <1h, 1-24h, 1-7d, 7-30d, 30-90d, >90d
        Instant oldest = Instant.MAX;
        Instant newest = Instant.MIN;

        for (CognitiveRecord rec : records) {
            Instant recTime = Instant.ofEpochMilli(rec.timestampMs());
            if (recTime.isBefore(oldest)) oldest = recTime;
            if (recTime.isAfter(newest)) newest = recTime;

            long ageMs = now - rec.timestampMs();
            long ageHours = ageMs / (1000L * 60 * 60);
            long ageDays = ageHours / 24;

            if (ageHours < 1)       bucketCounts[0]++;
            else if (ageHours < 24) bucketCounts[1]++;
            else if (ageDays < 7)   bucketCounts[2]++;
            else if (ageDays < 30)  bucketCounts[3]++;
            else if (ageDays < 90)  bucketCounts[4]++;
            else                    bucketCounts[5]++;
        }

        String[] labels = {"< 1 hour", "1h - 24h", "1d - 7d", "7d - 30d", "30d - 90d", "> 90d"};
        List<Map<String, Object>> buckets = new ArrayList<>();
        int total = 0;
        for (int i = 0; i < 6; i++) {
            Map<String, Object> bucket = new LinkedHashMap<>();
            bucket.put("label", labels[i]);
            bucket.put("count", bucketCounts[i]);
            buckets.add(bucket);
            total += bucketCounts[i];
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("buckets", buckets);
        response.put("totalMemories", total);
        response.put("oldestMemory", oldest == Instant.MAX ? null : oldest.toString());
        response.put("newestMemory", newest == Instant.MIN ? null : newest.toString());

        return ResponseEntity.ok(response);
    }

    /**
     * Recall memories with full cognitive scoring trace.
     */
    @PostMapping("/traced-recall")
    public ResponseEntity<Map<String, Object>> tracedRecall(@RequestBody TracedRecallRequest request) {
        SpectorMemory memory = memoryProvider.getIfAvailable();
        if (memory == null) {
            return ResponseEntity.status(503).body(Map.of("error", "Memory engine not available"));
        }

        if (request == null || request.query() == null || request.query().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "query is required"));
        }

        int topK = request.topK() > 0 ? request.topK() : 10;
        var options = RecallOptions.builder()
                .topK(topK)
                .enableTrace(true)
                .build();

        long startNanos = System.nanoTime();
        List<CognitiveResult> results = memory.recall(request.query(), options);
        long latencyMicros = (System.nanoTime() - startNanos) / 1_000;

        List<Map<String, Object>> tracedResults = new ArrayList<>();
        for (CognitiveResult cr : results) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", cr.id());
            entry.put("text", cr.text());
            entry.put("score", cr.score());
            entry.put("memoryType", cr.memoryType() != null ? cr.memoryType().name() : null);
            entry.put("importance", cr.importance());
            entry.put("ageDays", cr.ageDays());
            entry.put("recallCount", cr.agentRecallCount());
            entry.put("valence", cr.valence());
            entry.put("retrievalMode", cr.retrievalMode() != null ? cr.retrievalMode().name() : "STANDARD");

            if (cr.hasBreakdown()) {
                ScoreBreakdown bd = cr.breakdown();
                Map<String, Object> breakdown = new LinkedHashMap<>();
                breakdown.put("similarity", bd.similarity());
                breakdown.put("importanceDecay", bd.importanceDecay());
                breakdown.put("tagBoostFactor", bd.tagBoostFactor());
                breakdown.put("habituationPenalty", bd.habituationPenalty());
                breakdown.put("graphBoost", bd.graphBoost());
                breakdown.put("valenceAlignment", bd.valenceAlignment());
                breakdown.put("finalScore", bd.finalScore());
                entry.put("breakdown", breakdown);
            }

            tracedResults.add(entry);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("query", request.query());
        response.put("results", tracedResults);
        response.put("totalResults", tracedResults.size());
        response.put("latencyMicros", latencyMicros);
        response.put("traceEnabled", true);

        return ResponseEntity.ok(response);
    }

    public record TracedRecallRequest(String query, int topK) {
        public TracedRecallRequest {
            if (topK <= 0) topK = 10;
        }
    }
}
