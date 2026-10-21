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

import com.spectrayan.spector.config.properties.MemoryProperties;
import com.spectrayan.spector.kernel.api.MemorySource;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.memory.model.GraphNeighborhood;
import com.spectrayan.spector.memory.model.GraphNeighborhood.GraphEdge;
import com.spectrayan.spector.memory.model.MemoryPersistenceMode;
import com.spectrayan.spector.memory.model.TopologyStats.RelationTypeStats;
import com.spectrayan.spector.provider.generation.GenerationOptions;
import com.spectrayan.spector.provider.generation.LlmProvider;
import com.spectrayan.spector.provider.model.LlmRequest;
import com.spectrayan.spector.provider.model.LlmResponse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * End-to-end check that typed relations from LLM entity extraction (works_at, owns, located_in)
 * on semantic memories are stored as temporal facts and come back as typed ENTITY edges and topology relation types.
 *
 * <p>Predicates are canonicalised by {@code relationship-predicates.yaml}: {@code WORKS_AT} is an alias of
 * {@code MEMBER_OF}, and free-form spellings such as "located in" normalise to {@code LOCATED_IN}.</p>
 */
class TypedRelationEdgesIntegrationTest {

    private static final int DIMENSIONS = 32;
    private static final String WORKS = "Mike Thompson works at Vertex Health in Frisco.";
    private static final String SHIPS = "Vertex Health shipped the CareConnect release on Friday.";
    private static final String PLANE = "Mike Thompson owns a Lie-Nielsen hand plane.";
    /** Eight other memories that mention Mike Thompson, enough to use up the per-entity edge budget. */
    private static final List<String> BUSY = List.of(
            "Mike Thompson ran the Cottonwood Creek loop before sunrise.",
            "Mike Thompson smoked a brisket over post oak for twelve hours.",
            "Mike Thompson coached the Saturday soccer scrimmage.",
            "Mike Thompson cut dovetails for the walnut bookcase.",
            "Mike Thompson booked a cardiology appointment in Naperville.",
            "Mike Thompson rebalanced the index fund portfolio.",
            "Mike Thompson fixed the garage dehumidifier automation.",
            "Mike Thompson planned the anniversary date night.");

    private SpectorMemory memory;

    @AfterEach
    void tearDown() {
        if (memory != null) {
            memory.close();
        }
    }

    @Test
    void llmRelationsBecomeTypedEntityEdges() {
        memory = build();
        memory.remember("mem-works", WORKS, MemoryType.SEMANTIC, MemorySource.USER_STATED);
        memory.remember("mem-ships", SHIPS, MemoryType.SEMANTIC, MemorySource.USER_STATED);
        memory.remember("mem-plane", PLANE, MemoryType.SEMANTIC, MemorySource.USER_STATED);

        await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(100))
                .until(() -> relationTypes().containsAll(Set.of("MEMBER_OF", "OWNS", "LOCATED_IN")));

        GraphNeighborhood hood = memory.admin().graph().neighborhood("mem-works", 1, memory::inspect);
        List<GraphEdge> typed = hood.edges().stream()
                .filter(e -> "ENTITY".equals(e.type()) && "MEMBER_OF".equals(e.relationType()))
                .toList();
        assertThat(typed).isNotEmpty();
        assertThat(typed).anySatisfy(e -> {
            assertThat(e.targetId()).isEqualTo("mem-ships");
            assertThat(e.sourceEntityType()).isEqualTo("PERSON");
        });
    }

    @Test
    void typedEdgesSurviveABusySharedEntity() {
        memory = build();
        for (int i = 0; i < BUSY.size(); i++) {
            memory.remember("mem-busy-" + i, BUSY.get(i), MemoryType.SEMANTIC, MemorySource.USER_STATED);
        }
        memory.remember("mem-works", WORKS, MemoryType.SEMANTIC, MemorySource.USER_STATED);
        memory.remember("mem-ships", SHIPS, MemoryType.SEMANTIC, MemorySource.USER_STATED);

        await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(100))
                .until(() -> relationTypes().contains("MEMBER_OF"));
        assertThat(memory.admin().graph().neighborhood("mem-works", 1, memory::inspect).edges())
                .filteredOn(e -> e.targetId().startsWith("mem-busy-"))
                .hasSizeGreaterThanOrEqualTo(5);

        GraphNeighborhood hood = memory.admin().graph().neighborhood("mem-works", 1, memory::inspect);
        assertThat(hood.edges())
                .anySatisfy(e -> {
                    assertThat(e.type()).isEqualTo("ENTITY");
                    assertThat(e.relationType()).isEqualTo("MEMBER_OF");
                    assertThat(e.targetId()).isEqualTo("mem-ships");
                });
    }

    @Test
    void topologyCountsEachRelationType() {
        memory = build();
        memory.remember("mem-works", WORKS, MemoryType.SEMANTIC, MemorySource.USER_STATED);
        memory.remember("mem-plane", PLANE, MemoryType.SEMANTIC, MemorySource.USER_STATED);

        await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(100))
                .until(() -> relationTypes().containsAll(Set.of("MEMBER_OF", "OWNS")));

        memory.admin().graph().invalidateCache();
        assertThat(memory.admin().graph().topologyStats().relationTypes())
                .filteredOn(r -> r.type().equals("OWNS"))
                .singleElement()
                .satisfies(r -> assertThat(r.edgeCount()).isPositive());
    }

    /** Topology stats are cached; drop the cache so each poll sees the graph as it is now. */
    private Set<String> relationTypes() {
        memory.admin().graph().invalidateCache();
        return memory.admin().graph().topologyStats().relationTypes().stream()
                .map(RelationTypeStats::type)
                .collect(Collectors.toSet());
    }

    private static SpectorMemory build() {
        MemoryProperties props = new MemoryProperties()
                .setDimensions(DIMENSIONS)
                .setWorkingCapacity(20)
                .setEpisodicPartitionCapacity(100)
                .setSemanticCapacity(100)
                .setProceduralCapacity(100)
                .setEntityGraphCapacity(1000)
                .setHebbianGraphCapacity(500)
                .setTemporalChainCapacity(500);
        props.getGraph().getEntity().setExtractionMode("llm");
        return DefaultSpectorMemory.builder(props)
                .embeddingProvider(new SpectorMemoryIntegrationTest.MockEmbeddingProvider(DIMENSIONS))
                .llmProvider(new RelationLlm())
                .persistenceMode(MemoryPersistenceMode.IN_MEMORY)
                .build();
    }

    /** Answers the entity-extraction prompt in the ENTITY/RELATION line format, keyed on the memory text. */
    static final class RelationLlm implements LlmProvider {

        @Override
        public LlmResponse generate(LlmRequest request, GenerationOptions options) {
            String prompt = request.messages().isEmpty() ? "" : request.messages().get(0).text();
            if (!prompt.startsWith("Extract key entities")) {
                return new LlmResponse("NONE", 10, 10, "scripted");
            }
            String reply;
            if (prompt.contains(WORKS)) {
                reply = """
                        ENTITY: Mike Thompson | PERSON
                        ENTITY: Vertex Health | ORGANIZATION
                        ENTITY: Frisco | LOCATION
                        RELATION: Mike Thompson | WORKS_AT | Vertex Health
                        RELATION: Vertex Health | located in | Frisco
                        """;
            } else if (prompt.contains(SHIPS)) {
                reply = """
                        ENTITY: Vertex Health | ORGANIZATION
                        ENTITY: CareConnect | PRODUCT
                        """;
            } else if (BUSY.stream().anyMatch(prompt::contains)) {
                reply = "ENTITY: Mike Thompson | PERSON\n";
            } else if (prompt.contains(PLANE)) {
                reply = """
                        ENTITY: Mike Thompson | PERSON
                        ENTITY: Lie-Nielsen hand plane | PRODUCT
                        RELATION: Mike Thompson | owns | Lie-Nielsen hand plane
                        """;
            } else {
                reply = "NONE";
            }
            return new LlmResponse(reply, 10, 10, "scripted");
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public String modelName() {
            return "scripted";
        }
    }
}
