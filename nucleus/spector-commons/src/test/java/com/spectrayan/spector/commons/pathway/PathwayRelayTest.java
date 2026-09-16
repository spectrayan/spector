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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PathwayRelay")
class PathwayRelayTest {

    static class ParentSignal extends AbstractSignal {
        String query;
        String answer;
        ParentSignal(String query) { this.query = query; }
    }

    static class ChildSignal extends AbstractSignal {
        String input;
        String output;
        ChildSignal(String input) { this.input = input; }
    }

    interface ChildPathway extends Pathway<ChildSignal, String> {}

    static class ChildPathwayImpl extends AbstractPathway<ChildSignal, String> implements ChildPathway {
        ChildPathwayImpl() {
            super("child", ChildSignal.class, String.class,
                    PathwayEngine.<ChildSignal>builder("child")
                            .relay("compute", s -> {
                                s.output = "computed:" + s.input;
                                return true;
                            })
                            .build());
        }

        @Override
        protected String project(ChildSignal signal) {
            return signal.output;
        }
    }

    @Test
    @DisplayName("Transmits through catalog, converts input, and absorbs output")
    void transmitAndAbsorb() throws Exception {
        var catalog = new DefaultPathwayCatalog();
        var childPathway = new ChildPathwayImpl();
        catalog.register(ChildPathway.class, childPathway);

        var ctx = DefaultPathwayContext.builder()
                .catalog(catalog)
                .build();

        var parentSignal = new ParentSignal("hello");
        parentSignal.bind(ctx);

        var relay = PathwayRelay.<ParentSignal, ChildSignal, String>to(ChildPathway.class)
                .named("child_call")
                .from(p -> new ChildSignal(p.query))
                .into((p, o) -> p.answer = o)
                .required(true)
                .build();

        boolean continued = relay.transmit(parentSignal);

        assertThat(continued).isTrue();
        assertThat(parentSignal.answer).isEqualTo("computed:hello");
    }

    @Test
    @DisplayName("Throws when required target pathway is not registered")
    void throwsWhenRequiredNotRegistered() {
        var catalog = new DefaultPathwayCatalog();
        var ctx = DefaultPathwayContext.builder().catalog(catalog).build();

        var parentSignal = new ParentSignal("hello");
        parentSignal.bind(ctx);

        var relay = PathwayRelay.<ParentSignal, ChildSignal, String>to(ChildPathway.class)
                .named("child_call")
                .from(p -> new ChildSignal(p.query))
                .into((p, o) -> p.answer = o)
                .required(true)
                .build();

        assertThatThrownBy(() -> relay.transmit(parentSignal))
                .isInstanceOf(CognitivePathwayException.class)
                .satisfies(e -> {
                    var cpe = (CognitivePathwayException) e;
                    assertThat(cpe.kind()).isEqualTo(FaultKind.CONTRACT);
                });
    }

    @Test
    @DisplayName("Skips when unrequired target pathway is not registered")
    void skipsWhenUnrequiredNotRegistered() throws Exception {
        var catalog = new DefaultPathwayCatalog();
        var ctx = DefaultPathwayContext.builder().catalog(catalog).build();

        var parentSignal = new ParentSignal("hello");
        parentSignal.bind(ctx);

        var relay = PathwayRelay.<ParentSignal, ChildSignal, String>to(ChildPathway.class)
                .named("child_call")
                .from(p -> new ChildSignal(p.query))
                .into((p, o) -> p.answer = o)
                .required(false)
                .build();

        boolean continued = relay.transmit(parentSignal);

        assertThat(continued).isTrue();
        assertThat(parentSignal.answer).isNull();
    }
}
