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

import com.spectrayan.spector.commons.pathway.DefaultPathwayCatalog;
import com.spectrayan.spector.commons.pathway.DefaultPathwayContext;
import com.spectrayan.spector.commons.template.TemplateEngine;
import com.spectrayan.spector.kernel.api.MemorySource;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.kernel.engram.field.EncodingHeaderFields;
import com.spectrayan.spector.kernel.id.TsidGenerator;
import com.spectrayan.spector.memory.model.CognitiveRecord;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.ForgetCascade;
import com.spectrayan.spector.memory.model.ForgetResult;
import com.spectrayan.spector.memory.model.MemoryPersistenceMode;
import com.spectrayan.spector.memory.model.MemoryProvenance;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.pathway.reflect.relay.EpisodicLogConsolidationRelay;
import com.spectrayan.spector.memory.pathway.reflect.relay.ReflectSignal;
import com.spectrayan.spector.memory.pathway.remember.RememberPathway;
import com.spectrayan.spector.memory.sync.WalEvent;
import com.spectrayan.spector.provider.generation.GenerationOptions;
import com.spectrayan.spector.provider.generation.LlmProvider;
import com.spectrayan.spector.provider.model.LlmRequest;
import com.spectrayan.spector.provider.model.LlmResponse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Forgetting a memory cascades to the facts consolidated from it (#1144).
 */
class ForgetCascadeIntegrationTest {

    private static final int DIMENSIONS = 32;
    private static final String EPISODE = "Deploy window moved to Thursday afternoons for the billing service.";

    private static final String FACT = "The billing service deploy window is Thursday afternoon.";

    private SpectorMemory memory;
    private ConsolidationIntegrationTest.TestEmbeddingProvider embeddingProvider;
    private final AtomicInteger merges = new AtomicInteger();
    private LlmProvider llm;

    @BeforeEach
    void setUp() {
        embeddingProvider = new ConsolidationIntegrationTest.TestEmbeddingProvider(DIMENSIONS);
        llm = new LlmProvider() {
            @Override
            public LlmResponse generate(LlmRequest request, GenerationOptions options) {
                String prompt = request.messages().isEmpty() ? "" : request.messages().get(0).text();
                String reply;
                if (prompt.contains("Merge these")) {
                    reply = "Billing deploy merged note " + merges.incrementAndGet() + ".";
                } else if (prompt.contains("Thursday afternoons for the billing")
                        && !prompt.toLowerCase(java.util.Locale.ROOT).contains("contradict")) {
                    reply = "{\"facts\":[{\"text\":\"" + FACT + "\",\"synapticTags\":[\"deploy\"]}]}";
                } else {
                    reply = "NO";
                }
                return new LlmResponse(reply, 10, 10, "mock-llm");
            }

            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public String modelName() {
                return "mock-llm";
            }
        };
        memory = DefaultSpectorMemory.builder(new com.spectrayan.spector.config.properties.MemoryProperties()
                        .setDimensions(DIMENSIONS)
                        .setWorkingCapacity(20)
                        .setEpisodicPartitionCapacity(100)
                        .setSemanticCapacity(100)
                        .setProceduralCapacity(100))
                .embeddingProvider(embeddingProvider)
                .llmProvider(llm)
                .persistenceMode(MemoryPersistenceMode.IN_MEMORY)
                .build();
    }

    @AfterEach
    void tearDown() {
        memory.close();
    }

    /**
     * Stores one episode, runs the episodic-log consolidation relay over it, and returns the ids of the
     * facts distilled from it. The relay is driven directly with a catalog that can reach
     * RememberPathway, so the distilled facts and their provenance rows are written.
     */
    private List<String> reflectEpisode(String episodeId) {
        memory.remember(episodeId, EPISODE, MemoryType.EPISODIC, MemorySource.USER_STATED, "deploy");
        DefaultSpectorMemory dm = (DefaultSpectorMemory) memory;
        DefaultPathwayCatalog catalog = new DefaultPathwayCatalog();
        catalog.register(RememberPathway.class, dm.rememberPathway());
        ReflectSignal signal = ReflectSignal.builder()
                .partitionManager(dm.partitionManager())
                .index(dm.index())
                .embeddingProvider(embeddingProvider)
                .textGenerator(llm)
                .templateEngine(TemplateEngine.getDefault())
                .provenanceMemory(dm.provenanceMemory())
                .idGenerator(new TsidGenerator())
                .build();
        signal.bind(DefaultPathwayContext.builder().catalog(catalog).build());
        new EpisodicLogConsolidationRelay().transmit(signal);

        List<String> facts = memory.sessionProvenance(0L).stream()
                .map(MemoryProvenance::targetId)
                .filter(id -> memory.inspect(id) != null)
                .toList();
        assertThat(facts).as("reflection should distill at least one fact from the episode").isNotEmpty();
        return facts;
    }

    private static boolean isRetracted(CognitiveRecord rec) {
        return EncodingHeaderFields.isRetracted(rec.consolidationFlags());
    }

    private List<String> recalledIds() {
        return memory.recall("billing deploy window Thursday", RecallOptions.builder().topK(20).build())
                .stream().map(CognitiveResult::id).toList();
    }

    @Test
    void reflectStoresDistilledFactsWithProvenance() {
        // #1166: the built-in reflect run must store the facts it distills, not just consume the turns.
        memory.remember("ep-1", EPISODE, MemoryType.EPISODIC, MemorySource.USER_STATED, "deploy");

        memory.reflect();

        List<MemoryProvenance> lineage = memory.sessionProvenance(0L);
        assertThat(lineage).isNotEmpty();
        CognitiveRecord fact = memory.inspect(lineage.get(0).targetId());
        assertThat(fact).isNotNull();
        assertThat(fact.memoryType()).isEqualTo(MemoryType.SEMANTIC);
        assertThat(fact.text()).isEqualTo(FACT);
    }

    @Test
    void reflectWithoutRememberPathwayLeavesTurnsUnconsolidated() {
        // #1166: with nothing to store facts through, the turns stay available for a later sweep.
        memory.remember("ep-1", EPISODE, MemoryType.EPISODIC, MemorySource.USER_STATED, "deploy");
        DefaultSpectorMemory dm = (DefaultSpectorMemory) memory;
        var episodic = dm.partitionManager().routerFor(dm.partitionManager().activeSeq()).episodic();
        assertThat(episodic.unconsolidatedTurnOffsets()).hasSize(1);

        ReflectSignal signal = ReflectSignal.builder()
                .partitionManager(dm.partitionManager())
                .index(dm.index())
                .embeddingProvider(embeddingProvider)
                .textGenerator(llm)
                .templateEngine(TemplateEngine.getDefault())
                .provenanceMemory(dm.provenanceMemory())
                .idGenerator(new TsidGenerator())
                .build();
        signal.bind(DefaultPathwayContext.builder().build());
        new EpisodicLogConsolidationRelay().transmit(signal);

        assertThat(episodic.unconsolidatedTurnOffsets()).hasSize(1);
        assertThat(memory.sessionProvenance(0L)).isEmpty();
    }

    @Test
    void forgetFlagsFactsDistilledFromTheEpisodeByDefault() {
        List<String> facts = reflectEpisode("ep-1");
        assertThat(recalledIds()).containsAnyElementsOf(facts);

        ForgetResult result = memory.forgetWithResult("ep-1");

        assertThat(result.found()).isTrue();
        assertThat(result.derivedIds()).containsExactlyInAnyOrderElementsOf(facts);
        assertThat(result.retractedIds()).containsExactlyInAnyOrderElementsOf(facts);
        assertThat(result.cascadeTombstonedIds()).isEmpty();
        assertThat(((DefaultSpectorMemory) memory).wal().replay(-1L))
                .extracting(WalEvent::type)
                .contains(WalEvent.EventType.MARK_RETRACTED);
        for (String fact : facts) {
            CognitiveRecord rec = memory.inspect(fact);
            assertThat(rec).as("flagged facts stay inspectable").isNotNull();
            assertThat(isRetracted(rec)).isTrue();
        }
        assertThat(recalledIds()).doesNotContainAnyElementsOf(facts);
    }

    @Test
    void forgetWithTombstoneCascadeRemovesSingleSourceFacts() {
        List<String> facts = reflectEpisode("ep-1");

        ForgetResult result = memory.forgetWithResult("ep-1", ForgetCascade.TOMBSTONE);

        assertThat(result.derivedIds()).containsExactlyInAnyOrderElementsOf(facts);
        assertThat(result.cascadeTombstonedIds()).containsExactlyInAnyOrderElementsOf(facts);
        assertThat(result.retractedIds()).isEmpty();
        for (String fact : facts) {
            assertThat(memory.inspect(fact)).isNull();
            assertThat(memory.explainAll(fact)).isEmpty();
        }
        assertThat(recalledIds()).doesNotContainAnyElementsOf(facts);
    }

    @Test
    void forgetWithReportCascadeOnlyReportsDerivedFacts() {
        List<String> facts = reflectEpisode("ep-1");

        ForgetResult result = memory.forgetWithResult("ep-1", ForgetCascade.REPORT);

        assertThat(result.derivedIds()).containsExactlyInAnyOrderElementsOf(facts);
        assertThat(result.retractedIds()).isEmpty();
        assertThat(result.cascadeTombstonedIds()).isEmpty();
        for (String fact : facts) {
            CognitiveRecord rec = memory.inspect(fact);
            assertThat(rec).isNotNull();
            assertThat(isRetracted(rec)).isFalse();
        }
    }

    @Test
    void forgetOfUnrelatedEpisodeDoesNotTouchFacts() {
        List<String> facts = reflectEpisode("ep-1");
        memory.remember("ep-2", "Lunch order for the team offsite is pizza.",
                MemoryType.EPISODIC, MemorySource.USER_STATED, "food");

        ForgetResult result = memory.forgetWithResult("ep-2", ForgetCascade.TOMBSTONE);

        assertThat(result.found()).isTrue();
        assertThat(result.derivedIds()).isEmpty();
        for (String fact : facts) {
            assertThat(memory.inspect(fact)).isNotNull();
        }
    }

    @Test
    void cascadeFollowsMergesAndOnlyFlagsMultiSourceMerges() {
        List<String> facts = reflectEpisode("ep-1");
        String fact = facts.get(0);
        String factText = memory.inspect(fact).text();

        // A near-duplicate of the distilled fact; consolidation merges the two into a cns- memory.
        float[] vector = embeddingProvider.embed(factText).vector();
        String twin = "Billing service deploys now happen on Thursday afternoons.";
        embeddingProvider.register(twin, vector);
        memory.remember("sem-twin", twin, MemoryType.SEMANTIC, MemorySource.OBSERVED, "deploy");
        memory.consolidate();

        String mergedId = null;
        for (CognitiveResult r : memory.recall("Billing deploy merged note", RecallOptions.builder().topK(20).build())) {
            if (r.id().startsWith("cns-")) {
                mergedId = r.id();
                break;
            }
        }
        assertThat(mergedId).as("the fact and its twin should have been merged").isNotNull();
        assertThat(memory.explainAll(mergedId)).extracting(MemoryProvenance::sourceId)
                .containsExactlyInAnyOrder(fact, "sem-twin");

        ForgetResult result = memory.forgetWithResult("ep-1", ForgetCascade.TOMBSTONE);

        // The original fact was retired by the merge; the merge still has another source, so it is flagged.
        assertThat(result.derivedIds()).contains(mergedId);
        assertThat(result.retractedIds()).contains(mergedId);
        assertThat(result.cascadeTombstonedIds()).doesNotContain(mergedId);
        assertThat(isRetracted(memory.inspect(mergedId))).isTrue();
    }

    @Test
    void forgetOfMergeSourceViaMergedMemoryCascadesToLaterMerges() {
        String a = "Standup moved to 9:30 for the platform team.";
        String b = "Platform team standup now starts at 9:30.";
        float[] vector = new float[DIMENSIONS];
        vector[0] = 1.0f;
        embeddingProvider.register(a, vector);
        embeddingProvider.register(b, vector);
        memory.remember("dup-a", a, MemoryType.SEMANTIC, MemorySource.OBSERVED, "standup");
        memory.remember("dup-b", b, MemoryType.SEMANTIC, MemorySource.OBSERVED, "standup");
        memory.consolidate();

        String mergedId = null;
        for (CognitiveResult r : memory.recall("Billing deploy merged note", RecallOptions.builder().topK(20).build())) {
            if (r.id().startsWith("cns-")) {
                mergedId = r.id();
                break;
            }
        }
        assertThat(mergedId).isNotNull();

        // Forgetting the merged memory leaves nothing derived behind it.
        ForgetResult result = memory.forgetWithResult(mergedId, ForgetCascade.FLAG);
        assertThat(result.found()).isTrue();
        assertThat(result.derivedIds()).isEmpty();
    }
}
