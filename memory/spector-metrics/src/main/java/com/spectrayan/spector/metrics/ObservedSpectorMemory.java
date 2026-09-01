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

import com.spectrayan.spector.commons.chunker.ChunkConfig;
import com.spectrayan.spector.commons.concurrent.MemoryScope;
import com.spectrayan.spector.config.ObservabilityConfig;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.SpectorMemoryAdmin;
import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.kernel.layout.EpisodicFieldAccessor;
import com.spectrayan.spector.memory.metamemory.MemoryInsight;
import com.spectrayan.spector.memory.model.CognitiveProfile;
import com.spectrayan.spector.memory.model.CognitiveRecord;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.ConversationRole;
import com.spectrayan.spector.memory.model.FactHistory;
import com.spectrayan.spector.memory.model.ImportanceResult;
import com.spectrayan.spector.memory.model.IngestionContext;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.model.ReflectReport;
import com.spectrayan.spector.memory.model.SalienceProfile;
import com.spectrayan.spector.memory.model.SourceModality;
import com.spectrayan.spector.memory.model.WhyNotExplanation;
import com.spectrayan.spector.memory.neurodivergent.IngestionHints;
import com.spectrayan.spector.memory.pathway.RememberPathway;
import com.spectrayan.spector.memory.prospective.Reminder;
import com.spectrayan.spector.memory.session.EpisodicSessionIndex;
import com.spectrayan.spector.memory.temporal.TemporalFact;
import com.spectrayan.spector.metrics.observation.DefaultSpectorObservationConvention;
import com.spectrayan.spector.metrics.observation.MemoryObservationContext;
import com.spectrayan.spector.metrics.observation.ObservableComponent;
import com.spectrayan.spector.metrics.observation.SpectorObservationConvention;
import com.spectrayan.spector.metrics.observation.SpectorObservationDocumentation;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Observed decorator for {@link SpectorMemory} using the Micrometer Observation API.
 *
 * <p>Wraps a delegate cognitive memory instance and instruments all operations (remember, recall,
 * forget, reinforce, reflect, sync) with {@link Observation} spans. A single observation
 * simultaneously produces Micrometer metrics (timers, counters, histograms) and OpenTelemetry /
 * W3C Trace Context distributed trace spans (Trace ID, Span ID, parent-child span hierarchies).</p>
 */
public class ObservedSpectorMemory extends ObservableComponent implements SpectorMemory {

    private final SpectorMemory delegate;
    private final ObservationRegistry registry;
    private final SpectorObservationConvention convention;

    public ObservedSpectorMemory(SpectorMemory delegate, ObservationRegistry registry, ObservabilityConfig config) {
        this(delegate, registry, config, DefaultSpectorObservationConvention.INSTANCE);
    }

    public ObservedSpectorMemory(SpectorMemory delegate, ObservationRegistry registry, ObservabilityConfig config,
                                 SpectorObservationConvention convention) {
        super(registry, config);
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.convention = convention != null ? convention : DefaultSpectorObservationConvention.INSTANCE;
    }

    public SpectorMemory unwrap() {
        return delegate;
    }

    public ObservationRegistry observationRegistry() {
        return registry;
    }

    // --------------------------------------------------------------
    // OBSERVATION HELPER METHODS
    // --------------------------------------------------------------

    private Map<String, String> createTags(String tier, String memoryId, String query) {
        Map<String, String> tags = new HashMap<>();
        if (tier != null) tags.put("tier", tier);
        if (memoryId != null) tags.put("memory_id", memoryId);
        if (query != null) tags.put("query", query);
        return tags;
    }

    // --------------------------------------------------------------
    // INGESTION TARGET
    // --------------------------------------------------------------

    @Override
    public RememberPathway target() {
        return delegate.target();
    }

    @Override
    public String namespaceId() {
        return delegate.namespaceId();
    }

    // --------------------------------------------------------------
    // CORE API (OBSERVED)
    // --------------------------------------------------------------

    @Override
    public void remember(String id, String text, MemoryType type, MemorySource source, String... tags) {
        withObservation(SpectorObservationDocumentation.MEMORY_REMEMBER,
                createTags(type != null ? type.name() : null, id, null),
                () -> delegate.remember(id, text, type, source, tags));
    }

    @Override
    public void remember(String id, String text, MemoryType type, MemorySource source,
                         IngestionHints hints, String... tags) {
        withObservation(SpectorObservationDocumentation.MEMORY_REMEMBER,
                createTags(type != null ? type.name() : null, id, null),
                () -> delegate.remember(id, text, type, source, hints, tags));
    }

    @Override
    public void remember(String id, String text, MemoryType type, String... tags) {
        withObservation(SpectorObservationDocumentation.MEMORY_REMEMBER,
                createTags(type != null ? type.name() : null, id, null),
                () -> delegate.remember(id, text, type, tags));
    }

    @Override
    public void remember(String id, String text, MemoryType type, MemorySource source,
                         IngestionContext context, String... tags) {
        withObservation(SpectorObservationDocumentation.MEMORY_REMEMBER,
                createTags(type != null ? type.name() : null, id, null),
                () -> delegate.remember(id, text, type, source, context, tags));
    }

    @Override
    public String remember(String text, MemoryType type, MemorySource source, String... tags) {
        return withObservation(SpectorObservationDocumentation.MEMORY_REMEMBER,
                createTags(type != null ? type.name() : null, null, null),
                () -> delegate.remember(text, type, source, tags));
    }

    @Override
    public String remember(String text, MemoryType type, MemorySource source,
                           IngestionHints hints, String... tags) {
        return withObservation(SpectorObservationDocumentation.MEMORY_REMEMBER,
                createTags(type != null ? type.name() : null, null, null),
                () -> delegate.remember(text, type, source, hints, tags));
    }

    @Override
    public String remember(String text, MemoryType type, MemorySource source,
                           IngestionContext context, String... tags) {
        return withObservation(SpectorObservationDocumentation.MEMORY_REMEMBER,
                createTags(type != null ? type.name() : null, null, null),
                () -> delegate.remember(text, type, source, context, tags));
    }

    @Override
    public List<CognitiveResult> recall(String queryText, RecallOptions options) {
        return withObservation(SpectorObservationDocumentation.MEMORY_RECALL,
                createTags(null, null, queryText),
                () -> delegate.recall(queryText, options));
    }

    @Override
    public List<CognitiveResult> recall(String queryText, CognitiveProfile profile) {
        return withObservation(SpectorObservationDocumentation.MEMORY_RECALL,
                createTags(null, null, queryText),
                () -> delegate.recall(queryText, profile));
    }

    @Override
    public List<CognitiveResult> recall(String queryText) {
        return withObservation(SpectorObservationDocumentation.MEMORY_RECALL,
                createTags(null, null, queryText),
                () -> delegate.recall(queryText));
    }

    @Override
    public void forget(String id) {
        withObservation(SpectorObservationDocumentation.MEMORY_FORGET,
                createTags(null, id, null),
                () -> delegate.forget(id));
    }

    @Override
    public ReflectReport reflect() {
        return withObservation(SpectorObservationDocumentation.MEMORY_REFLECT,
                createTags(null, null, null),
                delegate::reflect);
    }

    @Override
    public void consolidate() {
        withObservation(SpectorObservationDocumentation.MEMORY_CONSOLIDATE,
                createTags(null, null, null),
                delegate::consolidate);
    }

    @Override
    public void updateChunkConfig(ChunkConfig config) {
        delegate.updateChunkConfig(config);
    }

    @Override
    public ImportanceResult estimateImportance(String text, IngestionHints hints) {
        return delegate.estimateImportance(text, hints);
    }

    @Override
    public void reinforce(String memoryId, byte valence) {
        withObservation(SpectorObservationDocumentation.MEMORY_REINFORCE,
                createTags(null, memoryId, null),
                () -> delegate.reinforce(memoryId, valence));
    }

    @Override
    public void reinforce(String memoryId, byte valence, IngestionHints updatedHints) {
        withObservation(SpectorObservationDocumentation.MEMORY_REINFORCE,
                createTags(null, memoryId, null),
                () -> delegate.reinforce(memoryId, valence, updatedHints));
    }

    @Override
    public void suppress(String memoryId, String reason) {
        withObservation(SpectorObservationDocumentation.MEMORY_FORGET,
                createTags(null, memoryId, null),
                () -> delegate.suppress(memoryId, reason));
    }

    @Override
    public void suppress(String memoryId) {
        withObservation(SpectorObservationDocumentation.MEMORY_FORGET,
                createTags(null, memoryId, null),
                () -> delegate.suppress(memoryId));
    }

    @Override
    public void unsuppress(String memoryId) {
        delegate.unsuppress(memoryId);
    }

    @Override
    public void markResolved(String memoryId) {
        delegate.markResolved(memoryId);
    }

    @Override
    public void markUnresolved(String memoryId) {
        delegate.markUnresolved(memoryId);
    }

    @Override
    public MemoryInsight introspect(String topic) {
        return withObservation(SpectorObservationDocumentation.MEMORY_RECALL,
                createTags(null, null, topic),
                () -> delegate.introspect(topic));
    }

    @Override
    public WhyNotExplanation whyNot(String memoryId, String query, RecallOptions options) {
        return withObservation(SpectorObservationDocumentation.MEMORY_RECALL,
                createTags(null, memoryId, query),
                () -> delegate.whyNot(memoryId, query, options));
    }

    @Override
    public CognitiveRecord inspect(String id) {
        return delegate.inspect(id);
    }

    @Override
    public List<CognitiveRecord> browse(String... tags) {
        return delegate.browse(tags);
    }

    @Override
    public long rememberEpisodic(ConversationRole role, int sequenceId,
                                 long timestampMs, long sessionId,
                                 byte[] body, short modelId,
                                 int tokenIn, int tokenOut,
                                 int latencyMs, long userId,
                                 short soulVersion, SourceModality modality) {
        return delegate.rememberEpisodic(role, sequenceId, timestampMs, sessionId,
                body, modelId, tokenIn, tokenOut, latencyMs, userId, soulVersion, modality);
    }

    @Override
    public List<EpisodicFieldAccessor.EpisodicRecord> browseEpisodic(long sessionId, int offset, int limit) {
        return delegate.browseEpisodic(sessionId, offset, limit);
    }

    @Override
    public List<EpisodicFieldAccessor.EpisodicRecord> tailEpisodic(long sessionId, int count) {
        return delegate.tailEpisodic(sessionId, count);
    }

    @Override
    public EpisodicSessionIndex episodicSessionIndex() {
        return delegate.episodicSessionIndex();
    }

    @Override
    public String exportJson() {
        return delegate.exportJson();
    }

    @Override
    public Reminder scheduleReminder(String text, Instant triggerAt, String... tags) {
        return delegate.scheduleReminder(text, triggerAt, tags);
    }

    @Override
    public Reminder scheduleReminder(String text, Duration delay, String... tags) {
        return delegate.scheduleReminder(text, delay, tags);
    }

    @Override
    public void scratchpad(String text) {
        delegate.scratchpad(text);
    }

    @Override
    public int totalMemories() {
        return delegate.totalMemories();
    }

    @Override
    public int memoryCount(MemoryType type) {
        return delegate.memoryCount(type);
    }

    @Override
    public void setSalienceProfile(SalienceProfile profile) {
        delegate.setSalienceProfile(profile);
    }

    @Override
    public void setSoulVersion(short version) {
        delegate.setSoulVersion(version);
    }

    @Override
    public void applyIdentity(com.spectrayan.spector.memory.model.SoulContext primarySoul,
                              List<com.spectrayan.spector.memory.model.SoulContext> soulStack,
                              SalienceProfile salience) {
        delegate.applyIdentity(primarySoul, soulStack, salience);
    }

    @Override
    public SalienceProfile salienceProfile() {
        return delegate.salienceProfile();
    }

    @Override
    public float computeTopicBoost(String text) {
        return delegate.computeTopicBoost(text);
    }

    @Override
    public float computeSelfRelevanceBoost(String text) {
        return delegate.computeSelfRelevanceBoost(text);
    }

    @Override
    public SpectorMemoryAdmin admin() {
        return delegate.admin();
    }

    @Override
    public int assertFact(String subject, String predicate, String object,
                          long validFrom, long validTo, float confidence) {
        return delegate.assertFact(subject, predicate, object, validFrom, validTo, confidence);
    }

    @Override
    public int assertFact(String subject, String predicate, String object,
                          long validFrom, long validTo, float confidence,
                          boolean allowCoexisting) {
        return delegate.assertFact(subject, predicate, object, validFrom, validTo, confidence, allowCoexisting);
    }

    @Override
    public int retractFact(int factId) {
        return delegate.retractFact(factId);
    }

    @Override
    public List<TemporalFact> factsAbout(String entityName, Instant asOf) {
        return delegate.factsAbout(entityName, asOf);
    }

    @Override
    public FactHistory factHistory(String subject, String predicate) {
        return delegate.factHistory(subject, predicate);
    }

    @Override
    public com.spectrayan.spector.memory.express.relay.ExpressReport express(
            com.spectrayan.spector.memory.express.relay.ExpressSignal signal) {
        return delegate.express(signal);
    }

    @Override
    public void close() {
        delegate.close();
    }
}
