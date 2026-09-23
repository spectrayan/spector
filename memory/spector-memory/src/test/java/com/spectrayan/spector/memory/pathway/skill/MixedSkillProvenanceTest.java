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
package com.spectrayan.spector.memory.pathway.skill;

import com.spectrayan.spector.commons.pathway.DefaultPathwayCatalog;
import com.spectrayan.spector.commons.pathway.DefaultPathwayContext;
import com.spectrayan.spector.commons.pathway.PathwayContext;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.kernel.api.ProvenanceSourceKind;
import com.spectrayan.spector.kernel.id.TsidGenerator;
import com.spectrayan.spector.kernel.layout.ProvenanceLayout;
import com.spectrayan.spector.kernel.store.HyperEntityGraphMemory;
import com.spectrayan.spector.kernel.store.ProvenanceEdge;
import com.spectrayan.spector.kernel.store.ProvenanceMemory;
import com.spectrayan.spector.memory.graph.EntityDirectory;
import com.spectrayan.spector.memory.pathway.FakeRememberPathway;
import com.spectrayan.spector.memory.pathway.remember.RememberPathway;
import com.spectrayan.spector.memory.pathway.skill.model.SkillBody;
import com.spectrayan.spector.memory.pathway.skill.model.SkillKind;
import com.spectrayan.spector.memory.pathway.skill.model.SkillMeta;
import com.spectrayan.spector.memory.pathway.skill.relay.SkillReport;
import com.spectrayan.spector.memory.pathway.skill.relay.SkillSignal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ADR-0086: Mixed Multi-Tier Skill Provenance Lineage Tests")
class MixedSkillProvenanceTest {

    private SkillPathway pathway;
    private FakeRememberPathway fakeRemember;
    private ProvenanceMemory provenanceMemory;
    private HyperEntityGraphMemory hyperEntityGraph;
    private EntityDirectory entityDirectory;
    private PathwayContext context;

    @BeforeEach
    void setUp() {
        pathway = SkillPathway.standard();
        fakeRemember = new FakeRememberPathway();
        provenanceMemory = mock(ProvenanceMemory.class);
        hyperEntityGraph = mock(HyperEntityGraphMemory.class);
        entityDirectory = mock(EntityDirectory.class);

        DefaultPathwayCatalog catalog = new DefaultPathwayCatalog();
        catalog.register(RememberPathway.class, fakeRemember);
        catalog.register(SkillPathway.class, pathway);

        context = DefaultPathwayContext.builder()
                .namespaceId("test-ns")
                .catalog(catalog)
                .build();
    }

    @Test
    @DisplayName("Crystallized skill from mixed parents (episodic + semantic + procedural) writes correct typed provenance rows")
    void testMixedLineageProvenanceWriting() {
        long episodicSessionId = 99991L;
        long semanticTsid = 0x07AAAAAAAABBBBBBL;
        long proceduralParentTsid = 0x07CCCCCDDDDDEEEEL;

        String semanticId = "sem-" + TsidGenerator.encodeCrockford(semanticTsid);
        String proceduralParentId = "skill-" + TsidGenerator.encodeCrockford(proceduralParentTsid);
        String episodicId = "ep-session-99991";

        SkillSignal.ParentRef episodicParent = SkillSignal.ParentRef.episodic(
                episodicSessionId, 1, 5, "step 1 to 5 execution"
        );
        SkillSignal.ParentRef semanticParent = SkillSignal.ParentRef.semantic(
                semanticTsid, "domain rule axiom"
        );
        SkillSignal.ParentRef proceduralParent = SkillSignal.ParentRef.procedural(
                proceduralParentTsid, "base playbook step"
        );
        SkillSignal.ParentRef workingParent = SkillSignal.ParentRef.of(
                "work-scratchpad", MemoryType.WORKING
        );

        SkillMeta meta = new SkillMeta(
                SkillMeta.SCHEMA_V1,
                "multi-tier-synthesis-rule",
                SkillKind.HEURISTIC,
                0.85f,
                List.of("synthesizer"),
                Map.of(
                        "episodic", List.of(episodicId),
                        "semantic", List.of(semanticId),
                        "procedural", List.of(proceduralParentId),
                        "working", List.of("work-scratchpad")
                )
        );
        SkillBody body = new SkillBody(meta, "# Synthesis Rule\n1. Combine facts with experiences");

        SkillSignal signal = new SkillSignal(
                SkillSignal.Mode.COMPILE,
                List.of(episodicParent, semanticParent, proceduralParent, workingParent),
                "synthesis cue",
                true,
                null,
                0.0f,
                hyperEntityGraph,
                entityDirectory,
                provenanceMemory
        );
        signal.extractedBody(body);
        signal.bind(context);

        when(entityDirectory.intern(anyString(), anyString())).thenReturn(101, 102, 103, 104, 105);

        SkillReport report = pathway.conduct(signal);

        assertThat(report).isNotNull();
        assertThat(report.skillId()).isNotNull();
        String generatedSkillId = report.skillId();
        long generatedSkillTsid = TsidGenerator.decodeCrockford(
                generatedSkillId.startsWith("skill-") ? generatedSkillId.substring(6) : generatedSkillId
        );

        // Verify ProvenanceMemory received exactly 3 edges (working parent is skipped)
        ArgumentCaptor<ProvenanceEdge> edgeCaptor = ArgumentCaptor.forClass(ProvenanceEdge.class);
        verify(provenanceMemory, times(3)).append(edgeCaptor.capture());
        List<ProvenanceEdge> edges = edgeCaptor.getAllValues();

        // Edge 1: Episodic
        ProvenanceEdge epEdge = edges.get(0);
        assertThat(epEdge.sourceKind()).isEqualTo(ProvenanceLayout.SOURCE_EPISODIC);
        assertThat(epEdge.sourceKindEnum()).isEqualTo(ProvenanceSourceKind.EPISODIC);
        assertThat(epEdge.targetKind()).isEqualTo(ProvenanceLayout.TARGET_PROCEDURAL);
        assertThat(epEdge.targetTsid()).isEqualTo(generatedSkillTsid);
        assertThat(epEdge.sessionId()).isEqualTo(episodicSessionId);
        assertThat(epEdge.firstSeq()).isEqualTo(1);
        assertThat(epEdge.lastSeq()).isEqualTo(5);
        assertThat(epEdge.turnCount()).isEqualTo((short) 5);
        assertThat(epEdge.sourceTsid()).isEqualTo(0L);

        // Edge 2: Semantic
        ProvenanceEdge semEdge = edges.get(1);
        assertThat(semEdge.sourceKind()).isEqualTo(ProvenanceLayout.SOURCE_SEMANTIC);
        assertThat(semEdge.sourceKindEnum()).isEqualTo(ProvenanceSourceKind.SEMANTIC);
        assertThat(semEdge.targetKind()).isEqualTo(ProvenanceLayout.TARGET_PROCEDURAL);
        assertThat(semEdge.targetTsid()).isEqualTo(generatedSkillTsid);
        assertThat(semEdge.sourceTsid()).isEqualTo(semanticTsid);

        // Edge 3: Procedural
        ProvenanceEdge procEdge = edges.get(2);
        assertThat(procEdge.sourceKind()).isEqualTo(ProvenanceLayout.SOURCE_PROCEDURAL);
        assertThat(procEdge.sourceKindEnum()).isEqualTo(ProvenanceSourceKind.PROCEDURAL);
        assertThat(procEdge.targetKind()).isEqualTo(ProvenanceLayout.TARGET_PROCEDURAL);
        assertThat(procEdge.targetTsid()).isEqualTo(generatedSkillTsid);
        assertThat(procEdge.sourceTsid()).isEqualTo(proceduralParentTsid);
    }
}
