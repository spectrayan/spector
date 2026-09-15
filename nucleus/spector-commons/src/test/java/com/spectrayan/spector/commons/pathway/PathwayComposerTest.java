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

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PathwayComposer")
class PathwayComposerTest {

    static class Signal extends AbstractSignal implements DivergentCapable<Signal> {
        final List<String> log = new ArrayList<>();

        @Override
        public Signal fork() {
            Signal s = new Signal();
            s.log.addAll(this.log);
            return s;
        }

        @Override
        public void merge(List<Signal> forks) {
            for (Signal f : forks) {
                log.addAll(f.log);
            }
        }
    }

    interface TargetPathway extends Pathway<Signal, String> {}

    @Test
    @DisplayName("Divergent branch build-time rejects ErrorPolicy.ABORT")
    void divergentRejectsAbortPolicy() {
        var composer = PathwayComposer.<Signal>of("test-pathway");
        SynapticRelay<Signal> branch = s -> true;

        assertThatThrownBy(() -> composer.divergent("div", List.of(branch), List.of(ErrorPolicy.ABORT)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Divergent branch cannot use ErrorPolicy.ABORT");
    }

    @Test
    @DisplayName("Divergent branch build-time rejects PathwayRelay")
    void divergentRejectsPathwayRelay() {
        var composer = PathwayComposer.<Signal>of("test-pathway");

        var pathwayRelay = PathwayRelay.<Signal, Signal, String>to(TargetPathway.class)
                .named("target")
                .from(s -> s)
                .into((s, o) -> {})
                .build();

        assertThatThrownBy(() -> composer.divergent("div", List.of(pathwayRelay)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Divergent branch cannot contain PathwayRelay");

        // Wrapped in NamedRelay
        var named = new NamedRelay<>("named", pathwayRelay);
        assertThatThrownBy(() -> composer.divergent("div", List.of(named)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Divergent branch cannot contain PathwayRelay");

        // Wrapped in GatedRelay
        var gated = new GatedRelay<>("gated", s -> true, pathwayRelay);
        assertThatThrownBy(() -> composer.divergent("div", List.of(gated)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Divergent branch cannot contain PathwayRelay");

        // Wrapped in CircuitBreakerRelay
        var cb = new CircuitBreakerRelay<>(pathwayRelay);
        assertThatThrownBy(() -> composer.divergent("div", List.of(cb)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Divergent branch cannot contain PathwayRelay");
    }

    @Test
    @DisplayName("Composes pathway using Recipe")
    void composesViaRecipe() {
        PathwayRecipe<Signal> recipe = c -> {
            c.relay("step1", s -> { s.log.add("1"); return true; });
            c.gated("step2", s -> true, s -> { s.log.add("2"); return true; });
        };

        var composer = PathwayComposer.<Signal>of("recipe-pathway");
        recipe.compose(composer);
        CognitivePathway<Signal> pathway = composer.build();

        var signal = new Signal();
        pathway.conduct(signal);

        assertThat(signal.log).containsExactly("1", "2");
    }

    @Test
    @DisplayName("Resolves optional relays via RelayFactory")
    void resolvesOptionalRelays() {
        SynapticRelay<Signal> resolvedRelay = s -> { s.log.add("optional-hit"); return true; };

        RelayFactory factory = new RelayFactory() {
            @Override
            @SuppressWarnings("unchecked")
            public <S> SynapticRelay<S> create(Class<? extends SynapticRelay<S>> type) {
                return (SynapticRelay<S>) resolvedRelay;
            }
        };

        var composer = PathwayComposer.<Signal>of("optional-pathway", factory);
        composer.optional("opt", null, ErrorPolicy.DEGRADE_GRACEFULLY); // null type -> skipped
        composer.optional("opt2", (Class) resolvedRelay.getClass(), ErrorPolicy.FAIL_FAST);

        CognitivePathway<Signal> pathway = composer.build();
        var signal = new Signal();
        pathway.conduct(signal);

        assertThat(signal.log).containsExactly("optional-hit");
    }
}
