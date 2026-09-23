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
import com.spectrayan.spector.kernel.api.MemorySource;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.kernel.engram.field.EncodingHeaderFields;
import com.spectrayan.spector.kernel.id.TsidGenerator;
import com.spectrayan.spector.kernel.layout.ProvenanceLayout;
import com.spectrayan.spector.kernel.store.HyperEntityGraphMemory;
import com.spectrayan.spector.kernel.store.ProvenanceEdge;
import com.spectrayan.spector.kernel.store.ProvenanceMemory;
import com.spectrayan.spector.memory.graph.EntityDirectory;
import com.spectrayan.spector.memory.pathway.FakeRememberPathway;
import com.spectrayan.spector.memory.pathway.remember.RememberPathway;
import com.spectrayan.spector.memory.pathway.remember.relay.RememberSignal;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ADR-0086 §5.2, §5.9: SkillPathway Nested Remember and Lineage Tests")
class SkillPathwayNestedRememberTest {

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
    @DisplayName("COMPILE mode persists procedural skill with null header (calculator-driven) and FLAG_CRYSTALLIZED overlay")
    void testCompilePersistsViaNestedRememberWithDynamicImportance() {
        long parentTsid = 0x07123456789ABCDEFL;
        String parentId = TsidGenerator.encodeCrockford(parentTsid);

        SkillMeta meta = new SkillMeta(
                SkillMeta.SCHEMA_V1,
                "auth-npe-guard",
                SkillKind.PLAYBOOK,
                0.35f,
                List.of("auth_tool"),
                Map.of("episodic", List.of(parentId))
        );
        SkillBody body = new SkillBody(meta, "# Auth NPE Guard\n1. Guard missing context");

        SkillSignal signal = new SkillSignal(
                SkillSignal.Mode.COMPILE,
                List.of(new SkillSignal.ParentRef(parentId, MemoryType.EPISODIC)),
                "auth NPE fix",
                true,
                null,
                0.0f,
                hyperEntityGraph,
                entityDirectory,
                provenanceMemory
        );
        signal.extractedBody(body);
        signal.bind(context);

        when(entityDirectory.intern(anyString(), anyString())).thenReturn(10, 20);

        SkillReport report = pathway.conduct(signal);

        assertThat(report).isNotNull();
        assertThat(report.skillId()).isNotNull();
        assertThat(report.mode()).isEqualTo(SkillSignal.Mode.COMPILE);

        // Verify RememberPathway was invoked
        assertThat(fakeRemember.invocationCount()).isEqualTo(1);
        RememberSignal rs = fakeRemember.received().getFirst();

        assertThat(rs.type()).isEqualTo(MemoryType.PROCEDURAL);
        assertThat(rs.source()).isEqualTo(MemorySource.REFLECTED);
        assertThat(rs.source().toEngramSource()).isEqualTo(com.spectrayan.spector.kernel.api.EngramSource.DISTILLED);
        // Header must be null so DopaminergicSurpriseRelay uses the importance calculator!
        assertThat(rs.header()).isNull();
        // FLAG_CRYSTALLIZED must be passed in the overlay
        assertThat(rs.consolidationFlagsOverlay()).isEqualTo(EncodingHeaderFields.FLAG_CRYSTALLIZED);
        // Text must contain the serialized markdown with frontmatter
        assertThat(rs.text()).contains("schema: spector.skill.v1");
        assertThat(rs.text()).contains("name: auth-npe-guard");

        // Verify ProvenanceMemory received a row
        ArgumentCaptor<ProvenanceEdge> edgeCaptor = ArgumentCaptor.forClass(ProvenanceEdge.class);
        verify(provenanceMemory).append(edgeCaptor.capture());
        ProvenanceEdge edge = edgeCaptor.getValue();
        assertThat(edge.sourceKind()).isEqualTo(ProvenanceLayout.SOURCE_EPISODIC_LOG);
        assertThat(edge.targetKind()).isEqualTo(ProvenanceLayout.TARGET_PROCEDURAL);

        // Verify HyperEntityGraph created a real hyperedge linking parent -> skill (not a self-edge!)
        verify(hyperEntityGraph).addHyperedge(
                eq(new int[]{10, 20}),
                eq(new int[]{HyperEntityGraphMemory.ROLE_SUBJECT, HyperEntityGraphMemory.ROLE_DERIVED_FROM}),
                eq(HyperEntityGraphMemory.TYPE_RELATIONSHIP),
                eq(1.0f),
                eq(0),
                anyLong()
        );
    }

    @Test
    @DisplayName("DRY_RUN mode does not invoke RememberPathway and returns preview report")
    void testDryRunDoesNotPersist() {
        SkillBody body = new SkillBody(null, "Preview procedural heuristic");

        SkillSignal signal = new SkillSignal(
                SkillSignal.Mode.DRY_RUN,
                List.of(),
                "cue",
                false,
                null,
                0.0f,
                hyperEntityGraph,
                entityDirectory,
                provenanceMemory
        );
        signal.extractedBody(body);
        signal.bind(context);

        SkillReport report = pathway.conduct(signal);

        assertThat(report).isNotNull();
        assertThat(report.mode()).isEqualTo(SkillSignal.Mode.DRY_RUN);
        assertThat(report.skillId()).isNull();
        assertThat(fakeRemember.invocationCount()).isZero();
        verify(provenanceMemory, never()).append(any());
        verify(hyperEntityGraph, never()).addHyperedge(any(), any(), anyInt(), anyFloat(), anyInt(), anyLong());
    }
}
