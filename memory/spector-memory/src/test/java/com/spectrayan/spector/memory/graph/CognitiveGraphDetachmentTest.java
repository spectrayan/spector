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
package com.spectrayan.spector.memory.graph;

import com.spectrayan.spector.kernel.store.HyperEntityGraphMemory;
import com.spectrayan.spector.kernel.store.TemporalChainMemory;
import com.spectrayan.spector.memory.cortex.index.MemoryIndex;
import com.spectrayan.spector.memory.graph.hebbian.HebbianGraph;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests that {@code detachMemory} reaches <b>every</b> graph plane and reports each one separately.
 *
 * <p>Scope note: these are wiring tests and nothing more. The facade's only job here is to fan a detach out
 * across four planes and aggregate the result, so mocks are the right instrument. Whether the planes actually
 * remove edges correctly is covered by {@code HebbianNodeDetachmentTest} against real graph
 * implementations — a mock cannot and does not prove that.</p>
 *
 * <p>The reason coverage of all four planes is asserted at all: a purge that detached from three of them
 * would look like it worked. The record would vanish from recall while still bridging its temporal
 * neighbours, or while its hyperedges kept asserting relationships derived from destroyed content.</p>
 */
@DisplayName("CognitiveGraphFacade — detaching a purged memory from every graph plane")
class CognitiveGraphDetachmentTest {

    private static final int SLOT = 7;

    @Test
    @DisplayName("every plane is asked to detach, and each result is reported separately")
    void allFourPlanesAreDetached() {
        var hebbian = mock(HebbianGraph.class);
        var temporal = mock(TemporalChainMemory.class);
        var entities = mock(EntityDirectory.class);
        var hyper = mock(HyperEntityGraphMemory.class);
        var facade = new CognitiveGraphFacade(hebbian, temporal, entities, hyper, mock(MemoryIndex.class));

        when(hebbian.removeNode(SLOT)).thenReturn(6);
        when(temporal.isLinked(SLOT)).thenReturn(true);
        when(entities.unlinkMemory(SLOT)).thenReturn(3);
        when(hyper.removeHyperedgesForMemory(SLOT)).thenReturn(2);

        var report = facade.detachMemory(SLOT);

        verify(hebbian).removeNode(SLOT);
        verify(temporal).unlink(SLOT);
        verify(entities).unlinkMemory(SLOT);
        verify(hyper).removeHyperedgesForMemory(SLOT);

        assertThat(report.hebbianEdges()).isEqualTo(6);
        assertThat(report.temporalUnlinked()).isTrue();
        assertThat(report.entityLinks()).isEqualTo(3);
        assertThat(report.hyperedges()).isEqualTo(2);
        // Reported per plane, not as a single number, so an audit can say which planes were reached.
        assertThat(report.total()).isEqualTo(12);
    }

    @Test
    @DisplayName("a record not in the temporal chain is not unlinked, and is not reported as unlinked")
    void temporalUnlinkIsConditional() {
        var hebbian = mock(HebbianGraph.class);
        var temporal = mock(TemporalChainMemory.class);
        var facade = new CognitiveGraphFacade(hebbian, temporal, mock(EntityDirectory.class),
                mock(HyperEntityGraphMemory.class), mock(MemoryIndex.class));

        when(temporal.isLinked(SLOT)).thenReturn(false);

        var report = facade.detachMemory(SLOT);

        verify(temporal, never()).unlink(anyInt());
        assertThat(report.temporalUnlinked()).isFalse();
    }

    @Test
    @DisplayName("one plane failing does not abort the others — partial detachment is reported, not hidden")
    void oneFailingPlaneDoesNotStopTheRest() {
        var hebbian = mock(HebbianGraph.class);
        var temporal = mock(TemporalChainMemory.class);
        var entities = mock(EntityDirectory.class);
        var hyper = mock(HyperEntityGraphMemory.class);
        var facade = new CognitiveGraphFacade(hebbian, temporal, entities, hyper, mock(MemoryIndex.class));

        when(hebbian.removeNode(SLOT)).thenThrow(new IllegalStateException("edge slab unavailable"));
        when(temporal.isLinked(SLOT)).thenReturn(true);
        when(entities.unlinkMemory(SLOT)).thenReturn(4);
        when(hyper.removeHyperedgesForMemory(SLOT)).thenReturn(1);

        var report = facade.detachMemory(SLOT);

        // An all-or-nothing detach would have left the remaining three planes untouched and given the
        // caller no way to tell which ones were cleaned.
        verify(entities).unlinkMemory(SLOT);
        verify(hyper).removeHyperedgesForMemory(SLOT);
        assertThat(report.hebbianEdges()).isZero();
        assertThat(report.entityLinks()).isEqualTo(4);
        assertThat(report.hyperedges()).isEqualTo(1);
    }

    @Test
    @DisplayName("a negative slot is a no-op rather than an out-of-range scan")
    void negativeSlotIsANoOp() {
        var hebbian = mock(HebbianGraph.class);
        var facade = new CognitiveGraphFacade(hebbian, mock(TemporalChainMemory.class),
                mock(EntityDirectory.class), mock(HyperEntityGraphMemory.class), mock(MemoryIndex.class));

        var report = facade.detachMemory(-1);

        verify(hebbian, never()).removeNode(anyInt());
        assertThat(report.total()).isZero();
    }

    @Test
    @DisplayName("isReferencedInAnyGraph consults every plane before answering no")
    void referenceCheckCoversEveryPlane() {
        var hebbian = mock(HebbianGraph.class);
        var temporal = mock(TemporalChainMemory.class);
        var entities = mock(EntityDirectory.class);
        var hyper = mock(HyperEntityGraphMemory.class);
        var facade = new CognitiveGraphFacade(hebbian, temporal, entities, hyper, mock(MemoryIndex.class));

        when(hebbian.hasAnyEdge(SLOT)).thenReturn(false);
        when(temporal.isLinked(SLOT)).thenReturn(false);
        when(entities.entityIdsForMemory(SLOT)).thenReturn(Set.of());
        when(hyper.hasHyperedgesForMemory(SLOT)).thenReturn(false);
        assertThat(facade.isReferencedInAnyGraph(SLOT)).isFalse();

        // Each plane on its own is enough to make the answer yes.
        when(hyper.hasHyperedgesForMemory(SLOT)).thenReturn(true);
        assertThat(facade.isReferencedInAnyGraph(SLOT)).isTrue();

        when(hyper.hasHyperedgesForMemory(SLOT)).thenReturn(false);
        when(entities.entityIdsForMemory(SLOT)).thenReturn(Set.of(1));
        assertThat(facade.isReferencedInAnyGraph(SLOT)).isTrue();

        when(entities.entityIdsForMemory(SLOT)).thenReturn(Set.of());
        when(temporal.isLinked(SLOT)).thenReturn(true);
        assertThat(facade.isReferencedInAnyGraph(SLOT)).isTrue();

        when(temporal.isLinked(SLOT)).thenReturn(false);
        when(hebbian.hasAnyEdge(SLOT)).thenReturn(true);
        assertThat(facade.isReferencedInAnyGraph(SLOT)).isTrue();
    }
}
