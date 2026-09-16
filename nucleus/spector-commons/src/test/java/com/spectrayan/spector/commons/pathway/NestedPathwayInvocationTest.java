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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests nested pathway invocation via {@link PathwayRelay} and {@link DefaultPathwayCatalog},
 * verifying child outcome isolation, outcome import with prefix, cycle detection, scope
 * sharing, and circuit breaker integration.
 */
@DisplayName("Nested Pathway Invocation")
class NestedPathwayInvocationTest {

    // ---- Inner types ----

    static class ParentSignal extends AbstractSignal {
        String query;
        final List<String> childResults = new ArrayList<>();
        ParentSignal(String query) { this.query = query; }
    }

    static class ChildInput extends AbstractSignal {
        final String text;
        String output;
        ChildInput(String text) { this.text = text; }
    }

    interface ChildPathway extends Pathway<ChildInput, String> {}

    /** A simple child pathway that echoes back its input. */
    static class EchoChildPathway extends AbstractPathway<ChildInput, String> implements ChildPathway {
        final AtomicInteger invocationCount = new AtomicInteger(0);

        EchoChildPathway() {
            super("child-echo", ChildInput.class, String.class,
                    PathwayEngine.<ChildInput>builder("child-echo")
                            .relay("echo", s -> {
                                s.output = "echo:" + s.text;
                                return true;
                            })
                            .build());
        }

        @Override
        protected String project(ChildInput signal) {
            invocationCount.incrementAndGet();
            return signal.output;
        }
    }

    /** A child pathway that marks its outcome as degraded. */
    static class DegradedChildPathway extends AbstractPathway<ChildInput, String> implements ChildPathway {
        DegradedChildPathway() {
            super("child-degraded", ChildInput.class, String.class,
                    PathwayEngine.<ChildInput>builder("child-degraded")
                            .relay("degrade", s -> {
                                s.context().outcome().markDegraded(
                                        "child-degraded/stage", FaultKind.TRANSIENT,
                                        "test-degraded", null);
                                s.output = "degraded-result";
                                return true;
                            })
                            .build());
        }

        @Override
        protected String project(ChildInput signal) {
            return signal.output;
        }
    }

    /** A child pathway that always throws. */
    static class FailingChildPathway extends AbstractPathway<ChildInput, String> implements ChildPathway {
        FailingChildPathway() {
            super("child-fail", ChildInput.class, String.class,
                    PathwayEngine.<ChildInput>builder("child-fail")
                            .relay("fail", s -> {
                                throw new RuntimeException("simulated failure");
                            })
                            .build());
        }

        @Override
        protected String project(ChildInput signal) {
            return null;
        }
    }

    private DefaultPathwayCatalog catalog;

    @BeforeEach
    void setUp() {
        catalog = new DefaultPathwayCatalog();
    }

    @Nested
    @DisplayName("catalog.invoke() outcome isolation")
    class OutcomeIsolation {

        @Test
        @DisplayName("Child degraded marks are imported with pathway name prefix")
        void childDegradedMarksImportedWithPrefix() {
            var child = new DegradedChildPathway();
            catalog.register(ChildPathway.class, child);

            var ctx = DefaultPathwayContext.builder().catalog(catalog).build();
            var input = new ChildInput("test");

            catalog.invoke(ChildPathway.class, ctx, input);

            // Parent outcome should have imported marks with child pathway name prefix
            assertThat(ctx.outcome().degradedMarks())
                    .anySatisfy(mark -> assertThat(mark.scope()).contains("child-degraded"));
        }

        @Test
        @DisplayName("Parent outcome retains its own marks alongside imported child marks")
        void parentRetainsOwnMarks() {
            var child = new EchoChildPathway();
            catalog.register(ChildPathway.class, child);

            var ctx = DefaultPathwayContext.builder().catalog(catalog).build();
            ctx.outcome().markDegraded("parent/stage", FaultKind.CONTRACT, "parent-issue", null);

            catalog.invoke(ChildPathway.class, ctx, new ChildInput("test"));

            // Parent should still have its own mark
            assertThat(ctx.outcome().degradedMarks())
                    .anySatisfy(mark -> assertThat(mark.message()).isEqualTo("parent-issue"));
        }

        @Test
        @DisplayName("Multiple nested invocations each get isolated outcomes")
        void multipleNestingsIsolated() {
            var child = new DegradedChildPathway();
            catalog.register(ChildPathway.class, child);

            var ctx = DefaultPathwayContext.builder().catalog(catalog).build();

            // Invoke 3 times
            for (int i = 0; i < 3; i++) {
                catalog.invoke(ChildPathway.class, ctx, new ChildInput("input-" + i));
            }

            // All degraded marks should be imported
            assertThat(ctx.outcome().degradedMarks()).hasSize(3);
        }
    }

    @Nested
    @DisplayName("PathwayRelay integration")
    class PathwayRelayIntegration {

        @Test
        @DisplayName("PathwayRelay transmits through catalog and absorbs result")
        void relayTransmitsAndAbsorbs() throws Exception {
            var child = new EchoChildPathway();
            catalog.register(ChildPathway.class, child);

            var ctx = DefaultPathwayContext.builder().catalog(catalog).build();
            var parentSignal = new ParentSignal("hello");
            parentSignal.bind(ctx);

            var relay = PathwayRelay.<ParentSignal, ChildInput, String>to(ChildPathway.class)
                    .named("invoke-child")
                    .from(p -> new ChildInput(p.query))
                    .into((p, o) -> p.childResults.add(o))
                    .required(true)
                    .build();

            boolean continued = relay.transmit(parentSignal);

            assertThat(continued).isTrue();
            assertThat(parentSignal.childResults).containsExactly("echo:hello");
            assertThat(child.invocationCount.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("PathwayRelay wraps child exception in CognitivePathwayException with nested=true")
        void relayWrapsChildException() {
            var child = new FailingChildPathway();
            catalog.register(ChildPathway.class, child);

            var ctx = DefaultPathwayContext.builder().catalog(catalog).build();
            var parentSignal = new ParentSignal("hello");
            parentSignal.bind(ctx);

            var relay = PathwayRelay.<ParentSignal, ChildInput, String>to(ChildPathway.class)
                    .named("invoke-child")
                    .from(p -> new ChildInput(p.query))
                    .into((p, o) -> p.childResults.add(o))
                    .required(true)
                    .build();

            assertThatThrownBy(() -> relay.transmit(parentSignal))
                    .isInstanceOf(CognitivePathwayException.class)
                    .satisfies(e -> {
                        var cpe = (CognitivePathwayException) e;
                        assertThat(cpe.nested()).isTrue();
                    });
        }

        @Test
        @DisplayName("Non-required relay skips when catalog is null")
        void nonRequiredSkipsWhenNoCatalog() throws Exception {
            var ctx = DefaultPathwayContext.builder().build(); // no catalog
            var parentSignal = new ParentSignal("hello");
            parentSignal.bind(ctx);

            var relay = PathwayRelay.<ParentSignal, ChildInput, String>to(ChildPathway.class)
                    .named("invoke-child")
                    .from(p -> new ChildInput(p.query))
                    .required(false)
                    .build();

            boolean continued = relay.transmit(parentSignal);
            assertThat(continued).isTrue();
        }
    }

    @Nested
    @DisplayName("Cycle detection")
    class CycleDetection {

        interface CyclicPathway extends Pathway<ChildInput, String> {}

        @Test
        @DisplayName("Direct self-invocation throws PATHWAY_CYCLE")
        void selfInvocationThrowsCycle() {
            // Create a pathway that tries to invoke itself via catalog
            var selfInvoking = new AbstractPathway<ChildInput, String>(
                    "cyclic", ChildInput.class, String.class,
                    PathwayEngine.<ChildInput>builder("cyclic")
                            .relay("self-call", s -> {
                                s.context().catalog().invoke(CyclicPathway.class, s.context(), new ChildInput("recursive"));
                                return true;
                            })
                            .build()
            ) {
                @Override
                protected String project(ChildInput signal) {
                    return "unreachable";
                }
            };
            catalog.register(CyclicPathway.class, selfInvoking);

            var ctx = DefaultPathwayContext.builder().catalog(catalog).build();
            var input = new ChildInput("start");

            assertThatThrownBy(() -> catalog.invoke(CyclicPathway.class, ctx, input))
                    .isInstanceOf(CognitivePathwayException.class)
                    .satisfies(e -> {
                        var cpe = (CognitivePathwayException) e;
                        assertThat(cpe.errorCode()).isEqualTo(ErrorCode.PATHWAY_CYCLE);
                    });
        }
    }

    @Nested
    @DisplayName("Scope sharing")
    class ScopeSharing {

        @Test
        @DisplayName("Nested context shares same ConductionScope instance")
        void nestedSharesScope() {
            var ctx = DefaultPathwayContext.builder().catalog(catalog).build();
            var nested = ctx.nested("child-stage");

            assertThat(nested.scope()).isSameAs(ctx.scope());
        }

        @Test
        @DisplayName("nestedWithOutcome shares scope but not outcome")
        void nestedWithOutcomeSharesScopeNotOutcome() {
            var ctx = DefaultPathwayContext.builder().catalog(catalog).build();
            var childOutcome = new ConductionOutcome();
            var nested = ctx.nestedWithOutcome("child-stage", childOutcome);

            assertThat(nested.scope()).isSameAs(ctx.scope());
            assertThat(nested.outcome()).isSameAs(childOutcome);
            assertThat(nested.outcome()).isNotSameAs(ctx.outcome());
        }

        @Test
        @DisplayName("nestedWithOutcome shares attribute bag")
        void nestedWithOutcomeSharesBag() {
            var key = Key.of("test-key", String.class);
            var ctx = DefaultPathwayContext.builder().catalog(catalog).build();
            ctx.bag().put(key, "value");

            var childOutcome = new ConductionOutcome();
            var nested = ctx.nestedWithOutcome("child-stage", childOutcome);

            assertThat(nested.bag().get(key)).isEqualTo("value");
        }
    }

    @Nested
    @DisplayName("Circuit breaker integration")
    class CircuitBreakerIntegration {

        @Test
        @DisplayName("Open circuit with BYPASS marks outcome bypassed and skips invocation")
        void openCircuitBypasses() throws Exception {
            var child = new EchoChildPathway();
            catalog.register(ChildPathway.class, child);

            var registry = CircuitBreakerRegistry.create();
            var breakerRef = new BreakerRef("test-breaker",
                    CircuitBreakerConfig.builder().failureThreshold(1).build(),
                    OnOpen.BYPASS);

            // Trip the breaker by recording a failure
            var breaker = registry.get(breakerRef);
            var permit = breaker.tryAcquire(OnOpen.BYPASS);
            breaker.onFailure(permit, FaultKind.TRANSIENT);

            var ctx = DefaultPathwayContext.builder()
                    .catalog(catalog)
                    .bind(CircuitBreakerRegistry.class, registry)
                    .build();

            var parentSignal = new ParentSignal("hello");
            parentSignal.bind(ctx);

            var relay = PathwayRelay.<ParentSignal, ChildInput, String>to(ChildPathway.class)
                    .named("breaker-test")
                    .from(p -> new ChildInput(p.query))
                    .into((p, o) -> p.childResults.add(o))
                    .required(true)
                    .breaker(breakerRef)
                    .build();

            boolean continued = relay.transmit(parentSignal);

            assertThat(continued).isTrue();
            assertThat(parentSignal.childResults).isEmpty(); // skipped, not invoked
            assertThat(child.invocationCount.get()).isEqualTo(0);
            assertThat(ctx.outcome().bypassedMarks())
                    .anySatisfy(mark -> assertThat(mark.message()).contains("circuit_open:test-breaker"));
        }

        @Test
        @DisplayName("Relay without breaker works normally")
        void relayWithoutBreakerWorksNormally() throws Exception {
            var child = new EchoChildPathway();
            catalog.register(ChildPathway.class, child);

            var ctx = DefaultPathwayContext.builder().catalog(catalog).build();
            var parentSignal = new ParentSignal("hello");
            parentSignal.bind(ctx);

            var relay = PathwayRelay.<ParentSignal, ChildInput, String>to(ChildPathway.class)
                    .named("no-breaker")
                    .from(p -> new ChildInput(p.query))
                    .into((p, o) -> p.childResults.add(o))
                    .required(true)
                    .build(); // no breaker ref

            relay.transmit(parentSignal);

            assertThat(parentSignal.childResults).containsExactly("echo:hello");
        }
    }
}
