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
package com.spectrayan.spector.metrics;

import com.spectrayan.spector.core.quantization.ScalarQuantizer;
import com.spectrayan.spector.memory.*;
import com.spectrayan.spector.memory.model.*;
import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.cortex.TierRouter;
import com.spectrayan.spector.memory.habituation.HabituationPenalty;
import com.spectrayan.spector.memory.hebbian.CoActivationTracker;
import com.spectrayan.spector.memory.index.MemoryIndex;
import com.spectrayan.spector.memory.inhibition.SuppressionSet;
import com.spectrayan.spector.memory.metamemory.MemoryInsight;
import com.spectrayan.spector.memory.neurodivergent.LateralEvaluator;
import com.spectrayan.spector.memory.pipeline.CognitiveIngestionTarget;
import com.spectrayan.spector.memory.pipeline.RecallPipeline;
import com.spectrayan.spector.memory.prospective.ProspectiveScheduler;
import com.spectrayan.spector.memory.prospective.Reminder;
import com.spectrayan.spector.memory.sync.MemoryWal;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link MeteredSpectorMemory}.
 */
class MeteredSpectorMemoryTest {

    @Test
    void recallRecordsMetrics() {
        MeterRegistry registry = new SimpleMeterRegistry();
        SpectorMemory stub = new DummySpectorMemory() {
            @Override
            public List<CognitiveResult> recall(String queryText) {
                return new ArrayList<>();
            }
        };

        MeteredSpectorMemory metered = new MeteredSpectorMemory(stub, registry);
        List<CognitiveResult> results = metered.recall("hello");

        assertThat(results).isNotNull();
        assertThat(registry.get(MeteredSpectorMemory.METRIC_RECALL_TOTAL).counter().count()).isEqualTo(1.0);
        assertThat(registry.get(MeteredSpectorMemory.METRIC_RECALL_DURATION).timer().count()).isEqualTo(1L);
    }

    @Test
    void rememberRecordsMetrics() {
        MeterRegistry registry = new SimpleMeterRegistry();
        SpectorMemory stub = new DummySpectorMemory();

        MeteredSpectorMemory metered = new MeteredSpectorMemory(stub, registry);
        metered.remember("id-1", "content-1", MemoryType.EPISODIC, MemorySource.USER_STATED, "tag");

        assertThat(registry.get(MeteredSpectorMemory.METRIC_REMEMBER_TOTAL).counter().count()).isEqualTo(1.0);
    }

    @Test
    void observabilityMetricsRegistered() {
        MeterRegistry registry = new SimpleMeterRegistry();
        SpectorMemory stub = new DummySpectorMemory();

        new MeteredSpectorMemory(stub, registry);

        assertThat(registry.find("spector.memory.page.faults").tag("type", "soft").gauge()).isNotNull();
        assertThat(registry.find("spector.memory.page.faults").tag("type", "hard").gauge()).isNotNull();
        assertThat(registry.find("spector.memory.pinned.bytes").gauge()).isNotNull();
    }

    @Test
    void testAllDelegationMethods() throws Exception {
        MeterRegistry registry = new SimpleMeterRegistry();
        ReflectReport reportMock = new ReflectReport(10, 5, 0, 0, Duration.ofMillis(100));
        SpectorMemory stub = new DummySpectorMemory() {
            @Override public ReflectReport reflect() { return reportMock; }
        };

        MeteredSpectorMemory metered = new MeteredSpectorMemory(stub, registry);

        assertThat(metered.unwrap()).isSameAs(stub);
        metered.target();

        metered.remember("id-1", "text-1", MemoryType.EPISODIC, MemorySource.USER_STATED, "tag");
        metered.remember("id-2", "text-2", MemoryType.EPISODIC, MemorySource.USER_STATED, (com.spectrayan.spector.memory.neurodivergent.IngestionHints) null, "tag");
        metered.remember("id-3", "text-3", MemoryType.EPISODIC, "tag");
        metered.remember("id-4", "text-4", MemoryType.EPISODIC, MemorySource.USER_STATED, (IngestionContext) null, "tag");
        metered.remember("text-5", MemoryType.EPISODIC, MemorySource.USER_STATED, "tag");
        metered.remember("text-6", MemoryType.EPISODIC, MemorySource.USER_STATED, (com.spectrayan.spector.memory.neurodivergent.IngestionHints) null, "tag");

        metered.recall("query", (RecallOptions) null);
        metered.recall("query", (CognitiveProfile) null);
        metered.forget("id-1");

        assertThat(metered.reflect()).isSameAs(reportMock);

        metered.reinforce("id-1", (byte) 1);
        metered.suppress("id-1", "reason");
        metered.suppress("id-1");
        metered.unsuppress("id-1");
        metered.markResolved("id-1");
        metered.markUnresolved("id-1");
        metered.introspect("topic");
        metered.whyNot("id-1", "query", null);
        metered.inspect("id-1");

        metered.scheduleReminder("text", Instant.now());
        metered.scheduleReminder("text", Duration.ofMinutes(1));
        metered.scratchpad("text");

        assertThat(metered.totalMemories()).isEqualTo(0);
        assertThat(metered.memoryCount(MemoryType.EPISODIC)).isEqualTo(0);
        assertThat(metered.decay(Duration.ofDays(1), 0.5f)).isEqualTo(0);

        metered.browse("tag");
        metered.exportJson();
        metered.estimateImportance("text", null);

        metered.admin();
        metered.coActivation();
        metered.wal();
        metered.prospective();
        metered.suppression();
        metered.habituation();
        metered.quantizer();
        metered.cognitiveTarget();
        metered.recallPipeline();
        metered.tierRouter();
        metered.index();
        metered.lateralEvaluator();
        metered.hebbianGraph();
        metered.temporalChain();
        metered.entityGraph();
        metered.close();
    }

    static class DummySpectorMemory implements SpectorMemory {
        @Override public CognitiveIngestionTarget target() { return null; }
        @Override public CompletableFuture<Void> remember(String id, String text, MemoryType type, MemorySource source, String... tags) { return CompletableFuture.completedFuture(null); }
        @Override public CompletableFuture<Void> remember(String id, String text, MemoryType type, MemorySource source, com.spectrayan.spector.memory.neurodivergent.IngestionHints hints, String... tags) { return CompletableFuture.completedFuture(null); }
        @Override public CompletableFuture<Void> remember(String id, String text, MemoryType type, MemorySource source, IngestionContext context, String... tags) { return CompletableFuture.completedFuture(null); }
        @Override public CompletableFuture<Void> remember(String id, String text, MemoryType type, String... tags) { return CompletableFuture.completedFuture(null); }
        @Override public CompletableFuture<String> remember(String text, MemoryType type, MemorySource source, String... tags) { return CompletableFuture.completedFuture("auto-id"); }
        @Override public CompletableFuture<String> remember(String text, MemoryType type, MemorySource source, com.spectrayan.spector.memory.neurodivergent.IngestionHints hints, String... tags) { return CompletableFuture.completedFuture("auto-id"); }
        @Override public CompletableFuture<String> remember(String text, MemoryType type, MemorySource source, IngestionContext context, String... tags) { return CompletableFuture.completedFuture("auto-id"); }
        @Override public List<CognitiveResult> recall(String queryText, RecallOptions options) { return null; }
        @Override public List<CognitiveResult> recall(String queryText, CognitiveProfile profile) { return null; }
        @Override public List<CognitiveResult> recall(String queryText) { return null; }
        @Override public void forget(String id) {}
        @Override public ReflectReport reflect() { return null; }
        @Override public void reinforce(String memoryId, byte valence) {}
        @Override public void suppress(String memoryId, String reason) {}
        @Override public void suppress(String memoryId) {}
        @Override public void unsuppress(String memoryId) {}
        @Override public void markResolved(String memoryId) {}
        @Override public void markUnresolved(String memoryId) {}
        @Override public MemoryInsight introspect(String topic) { return null; }
        @Override public Reminder scheduleReminder(String text, Instant triggerAt, String... tags) { return null; }
        @Override public Reminder scheduleReminder(String text, Duration delay, String... tags) { return null; }
        @Override public CompletableFuture<Void> scratchpad(String text) { return CompletableFuture.completedFuture(null); }
        @Override public int totalMemories() { return 0; }
        @Override public int memoryCount(MemoryType type) { return 0; }
        @Override public int decay(Duration olderThan, float factor) { return 0; }
        @Override public CoActivationTracker coActivation() { return null; }
        @Override public MemoryWal wal() { return null; }
        @Override public ProspectiveScheduler prospective() { return null; }
        @Override public SuppressionSet suppression() { return null; }
        @Override public HabituationPenalty habituation() { return null; }
        @Override public ScalarQuantizer quantizer() { return null; }
        @Override public CognitiveIngestionTarget cognitiveTarget() { return null; }
        @Override public RecallPipeline recallPipeline() { return null; }
        @Override public TierRouter tierRouter() { return null; }
        @Override public MemoryIndex index() { return null; }
        @Override public LateralEvaluator lateralEvaluator() { return null; }
        @Override public com.spectrayan.spector.memory.graph.EntityGraph entityGraph() { return null; }
        @Override public com.spectrayan.spector.memory.hebbian.HebbianGraph hebbianGraph() { return null; }
        @Override public com.spectrayan.spector.memory.temporal.TemporalChain temporalChain() { return null; }
        @Override public com.spectrayan.spector.memory.model.WhyNotExplanation whyNot(String memoryId, String queryText, RecallOptions options) { return null; }
        @Override public com.spectrayan.spector.memory.SpectorMemoryAdmin admin() { return null; }
        @Override public com.spectrayan.spector.memory.model.ImportanceEstimate estimateImportance(String text, com.spectrayan.spector.memory.neurodivergent.IngestionHints hints) { return null; }
        @Override public com.spectrayan.spector.memory.model.CognitiveRecord inspect(String id) { return null; }
        @Override public java.util.List<com.spectrayan.spector.memory.model.CognitiveRecord> browse(String... tags) { return java.util.List.of(); }
        @Override public String exportJson() { return "[]"; }
        @Override public void setSalienceProfile(SalienceProfile profile) {}
        @Override public SalienceProfile salienceProfile() { return SalienceProfile.NEUTRAL; }
        @Override public float computeTopicBoost(String text) { return 1.0f; }
        @Override public float computeSelfRelevanceBoost(String text) { return 1.0f; }
        @Override public void close() {}
    }
}
