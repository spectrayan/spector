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
package com.spectrayan.spector.commons.pathway;

import com.spectrayan.spector.commons.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PathwayCatalog")
class PathwayCatalogTest {

    static class SimpleSignal extends AbstractSignal {
        String data;
        SimpleSignal(String data) { this.data = data; }
    }

    interface SimplePathway extends Pathway<SimpleSignal, String> {}

    static class SimplePathwayImpl extends AbstractPathway<SimpleSignal, String> implements SimplePathway {
        SimplePathwayImpl() {
            super("simple", SimpleSignal.class, String.class,
                    CognitivePathway.<SimpleSignal>pathway("simple")
                            .relay("process", s -> { s.data = "processed:" + s.data; return true; })
                            .build());
        }

        @Override
        protected String project(SimpleSignal signal) {
            return signal.data;
        }
    }

    @Test
    @DisplayName("Registers and retrieves pathways")
    void registerAndRetrieve() {
        var catalog = new DefaultPathwayCatalog();
        var pathway = new SimplePathwayImpl();

        catalog.register(SimplePathway.class, pathway);

        assertThat(catalog.find(SimplePathway.class)).contains(pathway);
        assertThat(catalog.require(SimplePathway.class)).isSameAs(pathway);
        assertThat(catalog.all()).contains(pathway);
    }

    @Test
    @DisplayName("Duplicate registration throws IllegalStateException")
    void duplicateRegistrationThrows() {
        var catalog = new DefaultPathwayCatalog();
        var pathway1 = new SimplePathwayImpl();
        var pathway2 = new SimplePathwayImpl();

        catalog.register(SimplePathway.class, pathway1);

        assertThatThrownBy(() -> catalog.register(SimplePathway.class, pathway2))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already registered");
    }

    @Test
    @DisplayName("Require throws CognitivePathwayException when not registered")
    void requireUnregisteredThrows() {
        var catalog = new DefaultPathwayCatalog();

        assertThatThrownBy(() -> catalog.require(SimplePathway.class))
                .isInstanceOf(CognitivePathwayException.class)
                .satisfies(e -> {
                    var cpe = (CognitivePathwayException) e;
                    assertThat(cpe.errorCode()).isEqualTo(ErrorCode.MEMORY_PATHWAY_FAILED);
                    assertThat(cpe.kind()).isEqualTo(FaultKind.CONTRACT);
                });
    }

    @Test
    @DisplayName("invoke runs pathway within nested context")
    void invokeRunsWithinNestedContext() {
        var catalog = new DefaultPathwayCatalog();
        var pathway = new SimplePathwayImpl();
        catalog.register(SimplePathway.class, pathway);

        var ctx = DefaultPathwayContext.builder()
                .catalog(catalog)
                .build();

        var signal = new SimpleSignal("input");
        String result = catalog.invoke(SimplePathway.class, ctx, signal);

        assertThat(result).isEqualTo("processed:input");
    }

    @Test
    @DisplayName("invoke prevents recursion cycles")
    void invokePreventsCycles() {
        var catalog = new DefaultPathwayCatalog();
        var pathway = new SimplePathwayImpl();
        catalog.register(SimplePathway.class, pathway);

        var scope = new ConductionScope();
        scope.enter("simple"); // already on stack

        var ctx = DefaultPathwayContext.builder()
                .catalog(catalog)
                .scope(scope)
                .build();

        var signal = new SimpleSignal("input");
        assertThatThrownBy(() -> catalog.invoke(SimplePathway.class, ctx, signal))
                .isInstanceOf(CognitivePathwayException.class)
                .satisfies(e -> {
                    var cpe = (CognitivePathwayException) e;
                    assertThat(cpe.errorCode()).isEqualTo(ErrorCode.PATHWAY_CYCLE);
                    assertThat(cpe.kind()).isEqualTo(FaultKind.CONTRACT);
                });
    }
}
