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
package com.spectrayan.spector.synapse.observability;

import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.model.CognitiveRecord;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.model.ScoreBreakdown;
import com.spectrayan.spector.synapse.observability.dto.ObservabilityDto.AgeBucketDto;
import com.spectrayan.spector.synapse.observability.dto.ObservabilityDto.AgeDistributionResponse;
import com.spectrayan.spector.synapse.observability.dto.ObservabilityDto.ObservabilityStatsResponse;
import com.spectrayan.spector.synapse.observability.dto.ObservabilityDto.ScoreBreakdownDto;
import com.spectrayan.spector.synapse.observability.dto.ObservabilityDto.TimelineEventDto;
import com.spectrayan.spector.synapse.observability.dto.ObservabilityDto.TimelineResponse;
import com.spectrayan.spector.synapse.observability.dto.ObservabilityDto.TracedRecallItemDto;
import com.spectrayan.spector.synapse.observability.dto.ObservabilityDto.TracedRecallRequest;
import com.spectrayan.spector.synapse.observability.dto.ObservabilityDto.TracedRecallResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

/**
 * REST controller for exposing content-free memory observability and glass-box metrics.
 *
 * <p>Invariant: Under ADR-0083 and the content-free admin contract, no endpoint in this
 * controller may return raw memory content (text, vectors, tags, or unvetted metadata).</p>
 */
@RestController
@RequestMapping("/api/v1/observability")
@PreAuthorize("hasAnyRole('admin', 'super-admin', 'ADMIN', 'SUPER_ADMIN')")
public class ObservabilityController {

    private static final Logger log = LoggerFactory.getLogger(ObservabilityController.class);

    private final ObjectProvider<SpectorMemory> memoryProvider;
    private final ObjectProvider<com.spectrayan.spector.synapse.platform.events.TelemetryBroadcasterService> broadcasterProvider;

    public ObservabilityController(
            ObjectProvider<SpectorMemory> memoryProvider,
            ObjectProvider<com.spectrayan.spector.synapse.platform.events.TelemetryBroadcasterService> broadcasterProvider) {
        this.memoryProvider = memoryProvider;
        this.broadcasterProvider = broadcasterProvider;
        log.info("ObservabilityController initialized");
    }

    /**
     * Returns aggregate memory statistics.
     */
    @GetMapping("/stats")
    public ResponseEntity<ObservabilityStatsResponse> stats() {
        SpectorMemory memory = memoryProvider.getIfAvailable();
        if (memory == null) {
            return ResponseEntity.status(503).build();
        }

        var index = memory.admin().index();
        int indexedMemories = index != null ? index.size() : 0;

        var tierCounts = new LinkedHashMap<String, Integer>();
        tierCounts.put("WORKING", memory.memoryCount(MemoryType.WORKING));
        tierCounts.put("EPISODIC", memory.memoryCount(MemoryType.EPISODIC));
        tierCounts.put("SEMANTIC", memory.memoryCount(MemoryType.SEMANTIC));
        tierCounts.put("PROCEDURAL", memory.memoryCount(MemoryType.PROCEDURAL));

        return ResponseEntity.ok(new ObservabilityStatsResponse(
                memory.totalMemories(),
                indexedMemories,
                tierCounts
        ));
    }

    /**
     * Returns recent rolling ops/sec metrics for live time-series charts.
     */
    @GetMapping("/metrics/live")
    public ResponseEntity<List<Map<String, Object>>> liveMetrics() {
        var broadcaster = broadcasterProvider.getIfAvailable();
        if (broadcaster == null) {
            return ResponseEntity.ok(Collections.emptyList());
        }
        return ResponseEntity.ok(broadcaster.getLiveMetricsHistory());
    }

    /**
     * Returns chronological memory events for timeline visualization (content-free).
     */
    @GetMapping("/timeline")
    public ResponseEntity<TimelineResponse> timeline(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "100") int limit) {
        SpectorMemory memory = memoryProvider.getIfAvailable();
        if (memory == null) {
            return ResponseEntity.status(503).build();
        }

        List<CognitiveRecord> records = memory.admin().listAll();
        records.sort((a, b) -> Long.compare(b.timestampMs(), a.timestampMs())); // desc

        int effectiveLimit = Math.min(limit, 1000);
        List<TimelineEventDto> eventList = new ArrayList<>();

        String defaultNs = memory.namespaceId() != null && !memory.namespaceId().isBlank()
                ? memory.namespaceId()
                : "default";

        for (CognitiveRecord rec : records) {
            if (eventList.size() >= effectiveLimit) break;

            Instant recTime = Instant.ofEpochMilli(rec.timestampMs());
            if (from != null && recTime.toString().compareTo(from) < 0) continue;
            if (to != null && recTime.toString().compareTo(to) > 0) continue;

            String tier = rec.memoryType() != null ? rec.memoryType().name() : "WORKING";

            eventList.add(new TimelineEventDto(
                    "CREATED",
                    rec.id(),
                    defaultNs,
                    recTime.toString(),
                    tier,
                    rec.importance(),
                    rec.valence(),
                    rec.agentRecallCount()
            ));
        }

        return ResponseEntity.ok(new TimelineResponse(eventList, eventList.size()));
    }

    /**
     * Returns memory age distribution for histogram visualization.
     */
    @GetMapping("/age-distribution")
    public ResponseEntity<AgeDistributionResponse> ageDistribution() {
        SpectorMemory memory = memoryProvider.getIfAvailable();
        if (memory == null) {
            return ResponseEntity.status(503).build();
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
        List<AgeBucketDto> buckets = new ArrayList<>();
        int total = 0;
        for (int i = 0; i < 6; i++) {
            buckets.add(new AgeBucketDto(labels[i], bucketCounts[i]));
            total += bucketCounts[i];
        }

        return ResponseEntity.ok(new AgeDistributionResponse(
                buckets,
                total,
                oldest == Instant.MAX ? null : oldest.toString(),
                newest == Instant.MIN ? null : newest.toString()
        ));
    }

    /**
     * Recall memories with full cognitive scoring trace (strictly content-free).
     */
    @PostMapping("/traced-recall")
    public ResponseEntity<TracedRecallResponse> tracedRecall(@RequestBody TracedRecallRequest request) {
        SpectorMemory memory = memoryProvider.getIfAvailable();
        if (memory == null) {
            return ResponseEntity.status(503).build();
        }

        if (request == null || request.query() == null || request.query().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        int topK = request.topK() > 0 ? request.topK() : 10;
        var options = RecallOptions.builder()
                .topK(topK)
                .enableTrace(true)
                .build();

        long startNanos = System.nanoTime();
        List<CognitiveResult> results = memory.recall(request.query(), options);
        long latencyMicros = (System.nanoTime() - startNanos) / 1_000;

        List<TracedRecallItemDto> tracedResults = new ArrayList<>();
        for (CognitiveResult cr : results) {
            ScoreBreakdownDto breakdownDto = null;
            if (cr.hasBreakdown()) {
                ScoreBreakdown bd = cr.breakdown();
                breakdownDto = new ScoreBreakdownDto(
                        bd.similarity(),
                        bd.importanceDecay(),
                        bd.tagBoostFactor(),
                        bd.habituationPenalty(),
                        bd.graphBoost(),
                        bd.valenceAlignment(),
                        bd.finalScore()
                );
            }

            tracedResults.add(new TracedRecallItemDto(
                    cr.id(),
                    cr.score(),
                    cr.memoryType() != null ? cr.memoryType().name() : null,
                    cr.importance(),
                    cr.ageDays(),
                    cr.agentRecallCount(),
                    cr.valence(),
                    cr.retrievalMode() != null ? cr.retrievalMode().name() : "STANDARD",
                    breakdownDto
            ));
        }

        return ResponseEntity.ok(new TracedRecallResponse(
                request.query(),
                tracedResults,
                tracedResults.size(),
                latencyMicros,
                true
        ));
    }
}
