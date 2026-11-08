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
package com.spectrayan.spector.memory.policy;

import com.spectrayan.spector.commons.concurrent.MemoryScope;
import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorServerException;
import com.spectrayan.spector.kernel.api.ConversationRole;
import com.spectrayan.spector.kernel.api.DreamMode;
import com.spectrayan.spector.kernel.api.MemorySource;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.kernel.api.SourceModality;
import com.spectrayan.spector.kernel.engram.EncodingHeader;
import com.spectrayan.spector.memory.DefaultSpectorMemory;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.SpectorMemoryBuilder;
import com.spectrayan.spector.memory.model.ForgetCascade;
import com.spectrayan.spector.memory.model.MemoryPersistenceMode;
import com.spectrayan.spector.memory.model.RememberContext;
import com.spectrayan.spector.memory.neuromod.neurodivergent.RememberHints;
import com.spectrayan.spector.memory.pathway.reflect.ReflectSweepSpec;
import com.spectrayan.spector.memory.pathway.skill.relay.SkillSignal;
import com.spectrayan.spector.memory.sync.WalEvent;
import com.spectrayan.spector.memory.test.FakeEmbeddingProvider;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.embedding.EmbeddingResult;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EngineFencingIntegrationTest {

    private static final Set<String> NON_MUTATING_PUBLIC_METHODS = Set.of(
            // Read / query methods
            "recall",
            "inspect",
            "browse",
            "browseEpisodic",
            "tailEpisodic",
            "listAll",
            "exportJson",
            "estimateImportance",
            "introspect",
            "whyNot",
            "explain",
            "explainAll",
            "sessionProvenance",
            "continuityHistory",
            "calculateLongitudinalDrift",
            "factsAbout",
            "factHistory",
            "survey",
            "tombstoneRatios",
            "totalMemories",
            "memoryCount",
            "tombstonedCount",
            "progress",
            "lastReflectReport",
            "lastReflectTimestamp",
            "computeTopicBoost",
            "computeSelfRelevanceBoost",
            "express",
            "wander",
            "decide",
            // Runtime configuration & identity setters (in-memory config knobs, not storage mutations)
            "defaultRecallOptions",
            "updateChunkConfig",
            "updateRecallOptions",
            "applyLiveMemoryPatch",
            "updateHnswEfSearch",
            "liveMemoryPatch",
            "setSalienceProfile",
            "setSoulVersion",
            "applyIdentity",
            "salienceProfile",
            "bindRecallSignalContext",
            // Subsystem accessors & lifecycle
            "embeddingProvider",
            "episodicSessionIndex",
            "episodicImportanceFailureCount",
            "reflectPathway",
            "admin",
            "coActivation",
            "wal",
            "prospective",
            "scheduler",
            "suppression",
            "habituation",
            "quantizer",
            "rememberPathway",
            "recallPathway",
            "sharedPathways",
            "cognitiveRouter",
            "partitionManager",
            "partitionRegistry",
            "index",
            "lateralEvaluator",
            "graph",
            "entityDirectory",
            "insularCortex",
            "semanticIndex",
            "temporalKnowledgeGraph",
            "hyperEntityGraph",
            "dreamPathway",
            "wanderPathway",
            "indexPlaneCoordinator",
            "indexReconcileEngine",
            "graphEnricher",
            "namespaceManager",
            "namespaceId",
            "acquireLease",
            "releaseLease",
            "hasActiveLeases",
            "aismeBundle",
            "quiesceGuard",
            "checkpointEngine",
            "close"
    );

    @Test
    void engineRefusesWritesWhenEpochIsStale() throws Exception {
        AtomicLong currentEpoch = new AtomicLong(10);
        FencingMutationPolicy policy = new FencingMutationPolicy(currentEpoch::get);

        try (SpectorMemory memory = SpectorMemoryBuilder.createEmpty()
                .persistenceMode(MemoryPersistenceMode.IN_MEMORY)
                .namespaceId("test-ns")
                .mutationPolicy(policy)
                .embeddingProvider(new FakeEmbeddingProvider())
                .build()) {

            // Should succeed with valid epoch
            MemoryScope.callWithScope("session", "test-ns", () -> {
                ScopedValue.where(MemoryScope.FENCE_EPOCH, 10L).run(() -> {
                    assertDoesNotThrow(() -> memory.remember("id1", "Hello", MemoryType.SEMANTIC, MemorySource.OBSERVED));
                });
                return null;
            });

            // Should fail with stale epoch
            MemoryScope.callWithScope("session", "test-ns", () -> {
                ScopedValue.where(MemoryScope.FENCE_EPOCH, 9L).run(() -> {
                    assertThrows(SpectorServerException.class, () ->
                            memory.remember("id2", "World", MemoryType.SEMANTIC, MemorySource.OBSERVED)
                    );
                });
                return null;
            });
        }
    }

    @Test
    void delayedWriteAdmittedAtEpochNIsRejectedWhenEpochAdvancesBeforeWalAppend() throws Exception {
        AtomicLong currentEpoch = new AtomicLong(10);
        FencingMutationPolicy policy = new FencingMutationPolicy(currentEpoch::get);
        FakeEmbeddingProvider delegateEmbedder = new FakeEmbeddingProvider();

        EmbeddingProvider advancingEmbedder = new EmbeddingProvider() {
            @Override
            public EmbeddingResult embed(String text) {
                // Simulate epoch advancing from 10 -> 11 while the admitted write is in flight (after entry check)
                currentEpoch.set(11L);
                return delegateEmbedder.embed(text);
            }

            @Override
            public List<EmbeddingResult> embedBatch(List<String> texts) {
                currentEpoch.set(11L);
                return delegateEmbedder.embedBatch(texts);
            }

            @Override
            public int dimensions() {
                return delegateEmbedder.dimensions();
            }

            @Override
            public String modelName() {
                return delegateEmbedder.modelName();
            }
        };

        try (SpectorMemory memory = SpectorMemoryBuilder.createEmpty()
                .persistenceMode(MemoryPersistenceMode.IN_MEMORY)
                .namespaceId("test-ns")
                .mutationPolicy(policy)
                .embeddingProvider(advancingEmbedder)
                .build()) {

            MemoryScope.callWithScope("session", "test-ns", () -> {
                ScopedValue.where(MemoryScope.FENCE_EPOCH, 10L).run(() -> {
                    SpectorServerException ex = assertThrows(SpectorServerException.class, () ->
                            memory.remember("delayed-id", "In-flight write stalled across epoch bump",
                                    MemoryType.SEMANTIC, MemorySource.OBSERVED)
                    );
                    assertEquals(ErrorCode.FENCED, ex.errorCode());
                });
                return null;
            });

            assertNull(memory.inspect("delayed-id"), "Stale in-flight write must not leave a live record");
            assertTrue(memory.admin().wal().replay(0).isEmpty(), "Stale in-flight write must not append to WAL");
        }
    }

    @Test
    void walFramesCarryRealEpochInClusteredModeAndNeverMinusOne() throws Exception {
        AtomicLong currentEpoch = new AtomicLong(10);
        FencingMutationPolicy policy = new FencingMutationPolicy(currentEpoch::get);

        try (SpectorMemory memory = SpectorMemoryBuilder.createEmpty()
                .persistenceMode(MemoryPersistenceMode.IN_MEMORY)
                .namespaceId("test-ns")
                .mutationPolicy(policy)
                .embeddingProvider(new FakeEmbeddingProvider())
                .build()) {

            MemoryScope.callWithScope("session", "test-ns", () -> {
                ScopedValue.where(MemoryScope.FENCE_EPOCH, 10L).run(() -> {
                    memory.remember("id1", "First memory", MemoryType.SEMANTIC, MemorySource.OBSERVED);
                    memory.reinforce("id1", (byte) 5);
                    memory.rehearse("id1");
                    memory.forget("id1");
                });
                return null;
            });

            // Also verify engine-epoch fallback when FENCE_EPOCH is not explicitly bound on caller thread
            currentEpoch.set(12L);
            memory.remember("id2", "Second memory under engine epoch", MemoryType.SEMANTIC, MemorySource.OBSERVED);

            List<WalEvent> events = memory.admin().wal().replay(0);
            assertFalse(events.isEmpty(), "Expected WAL events to be recorded");
            for (WalEvent event : events) {
                assertTrue(event.epoch() >= 10L,
                        "Expected real epoch >= 10 on WAL event " + event.type() + " but got " + event.epoch());
            }
            assertEquals(12L, events.getLast().epoch());
        }
    }

    @Test
    void bypassWritePathsConsultMutationPolicyAndRejectStaleEpochs() throws Exception {
        AtomicLong currentEpoch = new AtomicLong(10);
        FencingMutationPolicy policy = new FencingMutationPolicy(currentEpoch::get);

        try (SpectorMemory memory = SpectorMemoryBuilder.createEmpty()
                .persistenceMode(MemoryPersistenceMode.IN_MEMORY)
                .namespaceId("test-ns")
                .mutationPolicy(policy)
                .embeddingProvider(new FakeEmbeddingProvider())
                .build()) {

            DefaultSpectorMemory dsm = (DefaultSpectorMemory) memory;

            // Seed two valid records at epoch 10 so target IDs exist for update/graph operations
            ScopedValue.where(MemoryScope.FENCE_EPOCH, 10L).run(() -> {
                dsm.remember("seed-1", "Alpha seed memory", MemoryType.SEMANTIC, MemorySource.OBSERVED);
                dsm.remember("seed-2", "Beta seed memory", MemoryType.SEMANTIC, MemorySource.OBSERVED);
            });

            // Now run all previously-bypassed mutating paths under stale epoch 9
            ScopedValue.where(MemoryScope.FENCE_EPOCH, 9L).run(() -> {
                assertFenced(() -> dsm.rememberEpisodic(
                        ConversationRole.USER, 1, System.currentTimeMillis(), 42L,
                        "turn".getBytes(StandardCharsets.UTF_8), (short) 1, 4, 4, 10, 1L,
                        (short) 0, SourceModality.TEXT));

                assertFenced(() -> dsm.reinforce("seed-1", (byte) 10, RememberHints.NONE));
                assertFenced(() -> dsm.rehearse("seed-1"));
                assertFenced(() -> dsm.suppress("seed-1", "test"));
                assertFenced(() -> dsm.suppress("seed-1"));
                assertFenced(() -> dsm.unsuppress("seed-1"));
                assertFenced(() -> dsm.markResolved("seed-1"));
                assertFenced(() -> dsm.markUnresolved("seed-1"));

                assertFenced(() -> dsm.associateHebbian("seed-1", "seed-2", 0.5f));
                assertFenced(() -> dsm.associateTemporal("seed-1", "seed-2", 1));
                assertFenced(() -> dsm.associateHyperedge(List.of("EntityA", "EntityB"), "seed-1", 0.8f));

                assertFenced(() -> dsm.assertFact("Alice", "knows", "Bob", 1L, 100L, 0.9f));
                assertFenced(() -> dsm.assertFact("Alice", "knows", "Bob", 1L, 100L, 0.9f, true));
                assertFenced(() -> dsm.retractFact(1));

                EncodingHeader header = new EncodingHeader(
                        System.currentTimeMillis(), 0L, 0L, 1.0f, 0.5f, 0, (short) 0,
                        (byte) 0, (byte) 0, (byte) 0, 1.0f, (byte) 0, (byte) 0, (byte) 0,
                        (short) 0, 0.0f, (byte) 0, com.spectrayan.spector.kernel.api.EngramSource.EXPERIENCED);
                assertFenced(() -> dsm.ingestMigratedRecord(
                        "migrated-1", "Migrated text", MemoryType.SEMANTIC, MemorySource.OBSERVED,
                        new String[]{"tag"}, header, Map.of("k", "v")));

                assertFenced(dsm::consolidate);
                assertFenced(dsm::reflect);
                assertFenced(() -> dsm.reflect(ReflectSweepSpec.fullCycle()));
                assertFenced(() -> dsm.reflectKernel(ReflectSweepSpec.fullCycle()));
                assertFenced(dsm::dream);
                assertFenced(() -> dsm.dream(DreamMode.REM));
                assertFenced(() -> dsm.compileSkill((SkillSignal) null));
                assertFenced(() -> dsm.decay(Duration.ofMinutes(5), 0.5f));
                assertFenced(() -> dsm.vacuum(MemoryType.SEMANTIC));
                assertFenced(() -> dsm.vacuum(MemoryType.SEMANTIC, true));
                assertFenced(() -> dsm.scheduleReminder("Remind me", Instant.now().plusSeconds(60), "tag"));
                assertFenced(() -> dsm.scheduleReminder("Remind me", Duration.ofSeconds(60), "tag"));
                assertFenced(() -> dsm.scratchpad("Scratchpad note"));
            });
        }
    }

    @Test
    void everyPublicMutatingMethodOnDefaultSpectorMemoryIsAccountedForAndConsultsMutationPolicy() throws Exception {
        Set<String> discoveredMutatingMethodNames = Arrays.stream(DefaultSpectorMemory.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()) && !Modifier.isStatic(m.getModifiers()))
                .filter(m -> !m.isBridge() && !m.isSynthetic())
                .map(Method::getName)
                .filter(name -> !NON_MUTATING_PUBLIC_METHODS.contains(name))
                .collect(Collectors.toSet());

        Set<String> expectedMutatingMethodNames = Set.of(
                "remember",
                "rememberEpisodic",
                "forget",
                "forgetWithResult",
                "purge",
                "reflect",
                "reflectKernel",
                "compileSkill",
                "dream",
                "consolidate",
                "reinforce",
                "rehearse",
                "suppress",
                "unsuppress",
                "markResolved",
                "markUnresolved",
                "ingestMigratedRecord",
                "scheduleReminder",
                "scratchpad",
                "decay",
                "vacuum",
                "assertFact",
                "retractFact",
                "associateHebbian",
                "associateTemporal",
                "associateHyperedge"
        );

        assertEquals(expectedMutatingMethodNames, discoveredMutatingMethodNames,
                "Every public instance method on DefaultSpectorMemory must either be in the mutating set or in NON_MUTATING_PUBLIC_METHODS");

        AtomicInteger policyChecks = new AtomicInteger(0);
        MutationPolicy countingRejectingPolicy = new MutationPolicy() {
            @Override
            public void checkWrite(WriteRequest request) {
                policyChecks.incrementAndGet();
                throw new SpectorServerException(ErrorCode.FENCED, "Fenced by architecture guard test");
            }
        };

        try (SpectorMemory memory = SpectorMemoryBuilder.createEmpty()
                .persistenceMode(MemoryPersistenceMode.IN_MEMORY)
                .namespaceId("test-ns")
                .mutationPolicy(countingRejectingPolicy)
                .embeddingProvider(new FakeEmbeddingProvider())
                .build()) {

            DefaultSpectorMemory dsm = (DefaultSpectorMemory) memory;
            List<Runnable> mutatingInvocations = List.of(
                    () -> dsm.remember("id", "text", MemoryType.SEMANTIC, MemorySource.OBSERVED),
                    () -> dsm.remember("id", "text", MemoryType.SEMANTIC, MemorySource.OBSERVED, RememberHints.NONE),
                    () -> dsm.remember("id", "text", MemoryType.SEMANTIC),
                    () -> dsm.remember("text", MemoryType.SEMANTIC, MemorySource.OBSERVED),
                    () -> dsm.remember("text", MemoryType.SEMANTIC, MemorySource.OBSERVED, RememberHints.NONE),
                    () -> dsm.remember("text", MemoryType.SEMANTIC, MemorySource.OBSERVED, RememberContext.EMPTY),
                    () -> dsm.remember("id", "text", MemoryType.SEMANTIC, MemorySource.OBSERVED, RememberContext.EMPTY),
                    () -> dsm.remember("id", "text", new float[]{1f, 0f, 0f, 0f}, MemoryType.SEMANTIC, MemorySource.OBSERVED, RememberContext.EMPTY),
                    () -> dsm.remember("id", "text", new float[]{1f, 0f, 0f, 0f}, MemoryType.SEMANTIC, MemorySource.OBSERVED),
                    () -> dsm.rememberEpisodic(ConversationRole.USER, 1, 1000L, 1L, new byte[]{1}, (short) 0, 1, 1, 1, 1L, (short) 0, SourceModality.TEXT),
                    () -> dsm.forget("id"),
                    () -> dsm.forgetWithResult("id"),
                    () -> dsm.forgetWithResult("id", ForgetCascade.FLAG),
                    () -> dsm.purge("id"),
                    dsm::reflect,
                    () -> dsm.reflect(ReflectSweepSpec.fullCycle()),
                    () -> dsm.reflectKernel(ReflectSweepSpec.fullCycle()),
                    () -> dsm.compileSkill((SkillSignal) null),
                    dsm::dream,
                    () -> dsm.dream(DreamMode.REM),
                    dsm::consolidate,
                    () -> dsm.reinforce("id", (byte) 1),
                    () -> dsm.reinforce("id", (byte) 1, RememberHints.NONE),
                    () -> dsm.rehearse("id"),
                    () -> dsm.suppress("id", "reason"),
                    () -> dsm.suppress("id"),
                    () -> dsm.unsuppress("id"),
                    () -> dsm.markResolved("id"),
                    () -> dsm.markUnresolved("id"),
                    () -> dsm.ingestMigratedRecord("id", "text", MemoryType.SEMANTIC, MemorySource.OBSERVED, new String[0], null, Map.of()),
                    () -> dsm.scheduleReminder("text", Instant.now().plusSeconds(10)),
                    () -> dsm.scheduleReminder("text", Duration.ofSeconds(10)),
                    () -> dsm.scratchpad("text"),
                    () -> dsm.decay(Duration.ofSeconds(10), 0.5f),
                    () -> dsm.vacuum(MemoryType.SEMANTIC),
                    () -> dsm.vacuum(MemoryType.SEMANTIC, true),
                    () -> dsm.assertFact("s", "p", "o", 1L, 2L, 0.9f),
                    () -> dsm.assertFact("s", "p", "o", 1L, 2L, 0.9f, false),
                    () -> dsm.retractFact(1),
                    () -> dsm.associateHebbian("a", "b", 0.5f),
                    () -> dsm.associateTemporal("a", "b", 1),
                    () -> dsm.associateHyperedge(List.of("e1", "e2"), "a", 0.5f)
            );

            for (Runnable invocation : mutatingInvocations) {
                int before = policyChecks.get();
                assertFenced(invocation::run);
                assertTrue(policyChecks.get() > before, "Expected mutationPolicy.checkWrite to be called");
            }
        }
    }

    private static void assertFenced( org.junit.jupiter.api.function.Executable action) {
        SpectorServerException ex = assertThrows(SpectorServerException.class, action);
        assertEquals(ErrorCode.FENCED, ex.errorCode());
    }
}
