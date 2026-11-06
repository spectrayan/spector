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
package com.spectrayan.spector.memory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.spectrayan.spector.commons.observation.MemoryObservationHook;
import com.spectrayan.spector.commons.observation.PathwayObservationHook;
import com.spectrayan.spector.commons.observation.PathwayObservationHooks;
import com.spectrayan.spector.commons.pathway.FaultKind;
import com.spectrayan.spector.config.properties.AffectProperties;
import com.spectrayan.spector.config.properties.MemoryProperties;
import com.spectrayan.spector.kernel.api.ConversationRole;
import com.spectrayan.spector.kernel.api.EpisodeRecord;
import com.spectrayan.spector.kernel.api.MemorySource;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.kernel.api.SourceModality;
import com.spectrayan.spector.memory.api.ImportanceProvider;
import com.spectrayan.spector.memory.model.ImportanceBreakdown;
import com.spectrayan.spector.memory.model.ImportanceContext;
import com.spectrayan.spector.memory.model.ImportanceResult;
import com.spectrayan.spector.memory.model.MemoryPersistenceMode;
import com.spectrayan.spector.memory.pathway.pipeline.TagExtractionResult;
import com.spectrayan.spector.memory.pathway.pipeline.TagExtractor;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.embedding.EmbeddingResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class RememberEpisodicAffectAndImportanceTest {

    private static final String ANCHOR_POLES = """
            positive:
              - joy
            negative:
              - grief
            high:
              - alarm
            low:
              - calm
            """;

    @TempDir
    Path tmp;

    @Test
    void rememberEpisodicAppraisesNonZeroAffectFromSoulAnchorsConsistentWithRemember() throws Exception {
        Path anchors = Files.writeString(tmp.resolve("anchors.yaml"), ANCHOR_POLES);
        FixedEmbedder embedder = anchorAxes();
        String chargedTurn = "Critical production outage triggered an urgent alarm and joyful recovery!";
        embedder.vectors.put(chargedTurn, new float[] {0.8f, 0.0f, 0.6f, 0.0f});

        MemoryProperties props = baseProps();
        props.getAffect()
                .setAppraiser(AffectProperties.Mode.SOUL_ANCHORS)
                .setAnchorsPath(anchors.toString())
                .setDeadband(0.05f)
                .setGain(4.0f);

        try (DefaultSpectorMemory memory = (DefaultSpectorMemory) DefaultSpectorMemory.builder(props)
                .embeddingProvider(embedder)
                .persistenceMode(MemoryPersistenceMode.IN_MEMORY)
                .build()) {
            long sessionId = 42L;
            memory.rememberEpisodic(
                    ConversationRole.USER, 1, 1_000L, sessionId,
                    chargedTurn.getBytes(StandardCharsets.UTF_8),
                    (short) 1, 12, 0, 5, 1L, (short) 1, SourceModality.TEXT);

            List<EpisodeRecord> turns = memory.tailEpisodic(sessionId, 1);
            assertThat(turns).hasSize(1);
            EpisodeRecord episodic = turns.get(0);
            assertThat(episodic.valence()).isGreaterThan((byte) 0);
            assertThat(Byte.toUnsignedInt(episodic.arousal())).isGreaterThan(0);

            memory.remember("sem-1", chargedTurn, MemoryType.SEMANTIC, MemorySource.OBSERVED);
            var loc = memory.index().locate("sem-1");
            var semHeader = memory.partitionManager().routerFor(loc.colocatedPartition()).readHeader(loc);
            assertThat(episodic.valence()).isEqualTo(semHeader.valence());
            assertThat(episodic.arousal()).isEqualTo(semHeader.arousal());
        }
    }

    @Test
    void rememberEpisodicAppraisesNonZeroAffectFromAppraisingTagExtractor() {
        FixedEmbedder embedder = anchorAxes();
        TagExtractor appraisingExtractor = new TagExtractor() {
            @Override
            public String[] extract(String id, String text) {
                return new String[0];
            }

            @Override
            public boolean appraises() {
                return true;
            }

            @Override
            public TagExtractionResult extractWithContext(String id, String text) {
                return new TagExtractionResult(new String[0], (byte) -45, (byte) 90);
            }
        };

        try (DefaultSpectorMemory memory = (DefaultSpectorMemory) DefaultSpectorMemory.builder(baseProps())
                .embeddingProvider(embedder)
                .tagExtractor(appraisingExtractor)
                .persistenceMode(MemoryPersistenceMode.IN_MEMORY)
                .build()) {
            long sessionId = 77L;
            memory.rememberEpisodic(
                    ConversationRole.USER, 1, 2_000L, sessionId,
                    "A deeply frustrating failure occurred".getBytes(StandardCharsets.UTF_8),
                    (short) 1, 6, 0, 2, 1L, (short) 1, SourceModality.TEXT);

            List<EpisodeRecord> turns = memory.tailEpisodic(sessionId, 1);
            assertThat(turns).hasSize(1);
            assertThat(turns.get(0).valence()).isEqualTo((byte) -45);
            assertThat(Byte.toUnsignedInt(turns.get(0).arousal())).isEqualTo(90);
        }
    }

    @Test
    void rememberEpisodicPassesTurnEmbeddingWhenAvailableAndNullWhenAbsent() {
        FixedEmbedder embedder = anchorAxes();
        embedder.vectors.put("turn with vector", new float[] {3.0f, 4.0f, 0.0f, 0.0f});

        List<ImportanceContext> capturedContexts = new ArrayList<>();
        ImportanceProvider capturingProvider = ctx -> {
            capturedContexts.add(ctx);
            return simpleResult(7.25f);
        };

        try (DefaultSpectorMemory memory = (DefaultSpectorMemory) DefaultSpectorMemory.builder(baseProps())
                .embeddingProvider(embedder)
                .importanceProvider(capturingProvider)
                .persistenceMode(MemoryPersistenceMode.IN_MEMORY)
                .build()) {
            long sessionId = 101L;
            memory.rememberEpisodic(
                    ConversationRole.USER, 1, 1_000L, sessionId,
                    "turn with vector".getBytes(StandardCharsets.UTF_8),
                    (short) 1, 4, 0, 1, 1L, (short) 1, SourceModality.TEXT);

            memory.rememberEpisodic(
                    ConversationRole.USER, 2, 2_000L, sessionId,
                    new byte[0],
                    (short) 1, 0, 0, 1, 1L, (short) 1, SourceModality.TEXT);

            assertThat(capturedContexts).hasSize(2);

            ImportanceContext withVec = capturedContexts.get(0);
            assertThat(withVec.hasVector()).isTrue();
            assertThat(withVec.vector()).isNotNull().hasSize(4);
            assertThat(withVec.vector()[0]).isCloseTo(0.6f, org.assertj.core.data.Offset.offset(1e-5f));
            assertThat(withVec.vector()[1]).isCloseTo(0.8f, org.assertj.core.data.Offset.offset(1e-5f));

            ImportanceContext withoutVec = capturedContexts.get(1);
            assertThat(withoutVec.hasVector()).isFalse();
            assertThat(withoutVec.vector())
                    .describedAs("Absent turn vector must be null rather than a zero-length float[0]")
                    .isNull();

            List<EpisodeRecord> turns = memory.browseEpisodic(sessionId, 0, 10);
            assertThat(turns).hasSize(2);
            assertThat(turns.get(0).importance()).isEqualTo(7.25f);
            assertThat(turns.get(1).importance()).isEqualTo(7.25f);
        }
    }

    @Test
    void rememberEpisodicFallsBackToDefaultImportanceWhenScorerReturnsZeroOrNegativeWithoutFailureCount() {
        FixedEmbedder embedder = anchorAxes();
        AtomicReference<Float> configuredScore = new AtomicReference<>(0.0f);
        ImportanceProvider nonPositiveProvider = ctx -> simpleResult(configuredScore.get());

        try (DefaultSpectorMemory memory = (DefaultSpectorMemory) DefaultSpectorMemory.builder(baseProps())
                .embeddingProvider(embedder)
                .importanceProvider(nonPositiveProvider)
                .persistenceMode(MemoryPersistenceMode.IN_MEMORY)
                .build()) {
            long sessionId = 202L;

            memory.rememberEpisodic(
                    ConversationRole.USER, 1, 1_000L, sessionId,
                    "zero score turn".getBytes(StandardCharsets.UTF_8),
                    (short) 1, 3, 0, 1, 1L, (short) 1, SourceModality.TEXT);

            configuredScore.set(-3.5f);
            memory.rememberEpisodic(
                    ConversationRole.ASSISTANT, 2, 2_000L, sessionId,
                    "negative score turn".getBytes(StandardCharsets.UTF_8),
                    (short) 1, 3, 0, 1, 1L, (short) 1, SourceModality.TEXT);

            List<EpisodeRecord> turns = memory.browseEpisodic(sessionId, 0, 10);
            assertThat(turns).hasSize(2);
            assertThat(turns.get(0).importance()).isEqualTo(DefaultSpectorMemory.DEFAULT_EPISODIC_IMPORTANCE);
            assertThat(turns.get(1).importance()).isEqualTo(DefaultSpectorMemory.DEFAULT_EPISODIC_IMPORTANCE);
            assertThat(memory.episodicImportanceFailureCount()).isZero();
            assertThat(memory.episodicImportanceWarnCount()).isZero();
        }
    }

    @Test
    void rememberEpisodicLogsRateLimitedWarningAndIncrementsMetricsWhenScorerThrows() {
        FixedEmbedder embedder = anchorAxes();
        ImportanceProvider throwingProvider = ctx -> {
            throw new IllegalStateException("simulated importance scorer failure");
        };

        AtomicInteger hookErrors = new AtomicInteger(0);
        MemoryObservationHook observationHook = (suffix, tags) -> {
            if (MemoryObservationHook.SCORING.equals(suffix)
                    && "error".equals(tags.get(MemoryObservationHook.TAG_STATUS))) {
                hookErrors.incrementAndGet();
            }
            return () -> {};
        };

        AtomicInteger degradedEvents = new AtomicInteger(0);
        PathwayObservationHook previousGlobal = PathwayObservationHooks.global();
        PathwayObservationHooks.setGlobal(new PathwayObservationHook() {
            @Override
            public void onDegraded(String pathway, String relay, FaultKind kind) {
                if ("remember_episodic".equals(pathway)
                        && "importance_scorer".equals(relay)
                        && kind == FaultKind.INTERNAL) {
                    degradedEvents.incrementAndGet();
                }
            }
        });

        Logger logger = (Logger) LoggerFactory.getLogger(DefaultSpectorMemory.class);
        Level previousLevel = logger.getLevel();
        logger.setLevel(Level.WARN);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try (DefaultSpectorMemory memory = (DefaultSpectorMemory) DefaultSpectorMemory.builder(baseProps())
                .embeddingProvider(embedder)
                .importanceProvider(throwingProvider)
                .observationHook(observationHook)
                .persistenceMode(MemoryPersistenceMode.IN_MEMORY)
                .build()) {
            long sessionId = 303L;
            for (int seq = 1; seq <= 4; seq++) {
                memory.rememberEpisodic(
                        ConversationRole.USER, seq, seq * 1_000L, sessionId,
                        ("turn-" + seq).getBytes(StandardCharsets.UTF_8),
                        (short) 1, 2, 0, 1, 1L, (short) 1, SourceModality.TEXT);
            }

            List<EpisodeRecord> turns = memory.browseEpisodic(sessionId, 0, 10);
            assertThat(turns).hasSize(4);
            assertThat(turns)
                    .extracting(EpisodeRecord::importance)
                    .containsExactly(5.0f, 5.0f, 5.0f, 5.0f);

            assertThat(memory.episodicImportanceFailureCount()).isEqualTo(4L);
            assertThat(hookErrors.get()).isEqualTo(4);
            assertThat(degradedEvents.get()).isEqualTo(4);

            assertThat(memory.episodicImportanceWarnCount()).isEqualTo(1L);
            assertThat(memory.episodicImportanceSuppressedWarnCount()).isEqualTo(3L);

            List<ILoggingEvent> warnEvents = appender.list.stream()
                    .filter(e -> e.getLevel() == Level.WARN
                            && e.getFormattedMessage().contains("Episodic importance scoring failed"))
                    .toList();
            assertThat(warnEvents).hasSize(1);
            assertThat(warnEvents.get(0).getFormattedMessage())
                    .contains("simulated importance scorer failure");
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(previousLevel);
            PathwayObservationHooks.setGlobal(previousGlobal);
        }
    }

    private static MemoryProperties baseProps() {
        return new MemoryProperties()
                .setDimensions(4)
                .setWorkingCapacity(20)
                .setEpisodicPartitionCapacity(100)
                .setSemanticCapacity(50)
                .setProceduralCapacity(20);
    }

    private static ImportanceResult simpleResult(float importance) {
        return new ImportanceResult(importance, false, ImportanceBreakdown.empty());
    }

    private static FixedEmbedder anchorAxes() {
        FixedEmbedder embedder = new FixedEmbedder();
        embedder.vectors.put("joy", new float[] {1f, 0f, 0f, 0f});
        embedder.vectors.put("grief", new float[] {0f, 1f, 0f, 0f});
        embedder.vectors.put("alarm", new float[] {0f, 0f, 1f, 0f});
        embedder.vectors.put("calm", new float[] {0f, 0f, 0f, 1f});
        return embedder;
    }

    private static final class FixedEmbedder implements EmbeddingProvider {
        private final Map<String, float[]> vectors = new HashMap<>();

        @Override
        public EmbeddingResult embed(String text) {
            float[] v = vectors.getOrDefault(text, new float[] {0.5f, 0.5f, 0.5f, 0.5f});
            return new EmbeddingResult(v.clone(), 1, "fixed");
        }

        @Override
        public int dimensions() {
            return 4;
        }

        @Override
        public String modelName() {
            return "fixed";
        }
    }
}
