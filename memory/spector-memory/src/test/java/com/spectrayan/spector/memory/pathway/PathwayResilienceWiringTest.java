/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-memory/LICENSE
 *
 * Change Date: May 27, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.memory.pathway;

import com.spectrayan.spector.commons.pathway.BulkheadRelay;
import com.spectrayan.spector.commons.pathway.CircuitBreakerRelay;
import com.spectrayan.spector.commons.pathway.PathwayEngine;
import com.spectrayan.spector.commons.pathway.ErrorPolicy;
import com.spectrayan.spector.commons.pathway.GatedRelay;
import com.spectrayan.spector.commons.pathway.NamedRelay;
import com.spectrayan.spector.commons.pathway.PathwayComposer;
import com.spectrayan.spector.commons.pathway.RetryRelay;
import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.commons.pathway.TimeoutRelay;
import com.spectrayan.spector.memory.pathway.recall.relay.RecallRecipe;
import com.spectrayan.spector.memory.pathway.recall.relay.RecallSignal;
import com.spectrayan.spector.memory.pathway.remember.relay.CorticalWriteTransactionRelay;
import com.spectrayan.spector.memory.pathway.remember.relay.DedupGuardRelay;
import com.spectrayan.spector.memory.pathway.remember.relay.DopaminergicSurpriseRelay;
import com.spectrayan.spector.memory.pathway.remember.relay.KnowledgeGraphEnrichmentRelay;
import com.spectrayan.spector.memory.pathway.remember.relay.RememberRecipe;
import com.spectrayan.spector.memory.pathway.remember.relay.RememberSignal;
import com.spectrayan.spector.memory.pathway.remember.relay.SynapticGraphLinkingRelay;
import com.spectrayan.spector.memory.pathway.remember.relay.SynapticTagTransductionRelay;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the ADR-0036 §5.5 resilience wiring into place.
 *
 * <p>{@code PathwayParityTest$RecipeRelayParity} already guards relay <em>names
 * and order</em>. This suite guards the two things it does not: the {@link ErrorPolicy}
 * assigned to each stage, and the decorator chain wrapped around it.</p>
 *
 * <p>The decorator assertions matter because the failure mode is silent. Removing the
 * {@code embed-provider} breaker, or adding a timeout to the cortical write, changes no
 * relay name and breaks no existing test — it just quietly removes protection or
 * introduces a torn-write hazard.</p>
 */
@DisplayName("ADR-0036 §5.5 resilience wiring")
class PathwayResilienceWiringTest {

    // ── Chain introspection ─────────────────────────────────────────────────

    /** Unwraps a stage's relay into the list of decorator simple names, outermost first. */
    private static List<String> decoratorChain(final SynapticRelay<?> entryRelay) {
        final List<String> chain = new ArrayList<>();
        SynapticRelay<?> r = entryRelay;
        while (r != null) {
            if (r instanceof NamedRelay<?> nr) {
                r = nr.delegate();
                continue;   // NamedRelay is bookkeeping, not a decorator
            }
            if (r instanceof GatedRelay<?> g) {
                chain.add("Gated");
                r = g.delegate();
            } else if (r instanceof BulkheadRelay<?> b) {
                chain.add("Bulkhead");
                r = b.delegate();
            } else if (r instanceof CircuitBreakerRelay<?> c) {
                chain.add("Breaker");
                r = c.delegate();
            } else if (r instanceof RetryRelay<?> rr) {
                chain.add("Retry");
                r = rr.delegate();
            } else if (r instanceof TimeoutRelay<?> t) {
                chain.add("Timeout");
                r = t.delegate();
            } else {
                chain.add("relay:" + r.getClass().getSimpleName());
                break;
            }
        }
        return chain;
    }

    private static List<String> chainFor(final PathwayEngine<?> engine, final String stageName) {
        final int idx = engine.relayNames().indexOf(stageName);
        assertThat(idx).as("stage '%s' must exist in %s", stageName, engine.relayNames()).isNotNegative();
        return decoratorChain(engine.entries().get(idx).relay());
    }

    private static ErrorPolicy policyFor(final PathwayEngine<?> engine, final String stageName) {
        final int idx = engine.relayNames().indexOf(stageName);
        assertThat(idx).as("stage '%s' must exist", stageName).isNotNegative();
        return engine.entries().get(idx).errorPolicy();
    }

    // ── Pathway builders using REAL relays where markers matter ─────────────

    private static PathwayEngine<RememberSignal> rememberEngine() {
        final var composer = PathwayComposer.<RememberSignal>of("remember");
        new RememberRecipe(
                Mockito.mock(DedupGuardRelay.class),
                Mockito.mock(SynapticTagTransductionRelay.class),
                Mockito.mock(DopaminergicSurpriseRelay.class),
                Mockito.mock(CorticalWriteTransactionRelay.class),
                Mockito.mock(SynapticGraphLinkingRelay.class),
                // A REAL instance (with mocked collaborators) rather than a mock of the
                // relay itself: Mockito answers un-stubbed default interface methods with
                // false, so a mocked relay would report interruptible() == false and the
                // recipe's timeoutIfInterruptible would silently skip the budget — making
                // this test pass for the wrong reason.
                new KnowledgeGraphEnrichmentRelay(
                        Mockito.mock(com.spectrayan.spector.memory.pathway.pipeline.PostIngestSync.class),
                        Mockito.mock(com.spectrayan.spector.memory.pathway.pipeline.AsyncEntityExtractionQueue.class),
                        Mockito.mock(com.spectrayan.spector.memory.graph.EntityExtractor.class)))
                .compose(composer);
        return composer.build();
    }

    @Nested
    @DisplayName("Remember")
    class Remember {

        @Test
        @DisplayName("CORTICAL_WRITE carries NO timeout, retry, breaker or bulkhead")
        void corticalWriteIsBare() {
            final var chain = chainFor(rememberEngine(), RelayNames.CORTICAL_WRITE);

            assertThat(chain)
                    .as("an mmap + WAL write must never be interrupted or retried (ADR-0036 §7.3)")
                    .noneMatch(s -> s.equals("Timeout") || s.equals("Retry")
                            || s.equals("Breaker") || s.equals("Bulkhead"));
            assertThat(policyFor(rememberEngine(), RelayNames.CORTICAL_WRITE))
                    .isEqualTo(ErrorPolicy.FAIL_FAST);
        }

        @Test
        @DisplayName("KG_ENRICHMENT gets LLM timeout + breaker + bulkhead, but NO retry")
        void kgEnrichmentDecorated() {
            final var chain = chainFor(rememberEngine(), RelayNames.KG_ENRICHMENT);

            assertThat(chain).contains("Bulkhead", "Breaker", "Timeout");
            assertThat(chain)
                    .as("KG enrichment mutates entity/temporal graphs; retry can duplicate edges")
                    .doesNotContain("Retry");
            assertThat(policyFor(rememberEngine(), RelayNames.KG_ENRICHMENT))
                    .isEqualTo(ErrorPolicy.DEGRADE_GRACEFULLY);
        }

        @Test
        @DisplayName("Bulkhead is outermost, then breaker, then timeout (ADR-0036 §6)")
        void decoratorOrderIsOutsideIn() {
            final var chain = chainFor(rememberEngine(), RelayNames.KG_ENRICHMENT);
            assertThat(chain.indexOf("Bulkhead")).isLessThan(chain.indexOf("Breaker"));
            assertThat(chain.indexOf("Breaker")).isLessThan(chain.indexOf("Timeout"));
        }
    }

    @Nested
    @DisplayName("Recall")
    class Recall {

        private PathwayEngine<RecallSignal> recallEngine() {
            final var composer = PathwayComposer.<RecallSignal>of("recall");
            RecallRecipe.builder()
                    // Real transduction relay so the interruptible/idempotent markers apply.
                    .transductionRelay(new com.spectrayan.spector.memory.pathway.recall.relay
                            .QueryTransductionRelay(null))
                    .prospectiveRelay(noop())
                    .governedReleaseGateRelay(noop())
                    .vectorSearchRelay(noop())
                    .scoringRelay(noop())
                    .graphExpansionRelay(noop())
                    .evidenceFusionRelay(noop())
                    .build()
                    .compose(composer);
            return composer.build();
        }

        private SynapticRelay<RecallSignal> noop() {
            return s -> true;
        }

        @Test
        @DisplayName("TRANSDUCTION gets embed timeout + retry + breaker and stays FAIL_FAST")
        void transductionDecorated() {
            final var engine = recallEngine();
            final var chain = chainFor(engine, RelayNames.TRANSDUCTION);

            assertThat(chain).contains("Breaker", "Retry", "Timeout");
            assertThat(policyFor(engine, RelayNames.TRANSDUCTION))
                    .as("Recall cannot produce results without a query vector")
                    .isEqualTo(ErrorPolicy.FAIL_FAST);
        }

        @Test
        @DisplayName("Hot-path scan/score/fuse relays carry NO decorators (requirement N1)")
        void hotPathIsUndecorated() {
            final var engine = recallEngine();

            // N1: "no hot-path regression". The durable way to guarantee that is
            // structural — every in-process stage on the SIMD scan path must be free of
            // wrapper objects, so conduction allocates nothing extra per relay. A
            // benchmark number would drift; this assertion cannot.
            for (final String hotStage : List.of(
                    RelayNames.VECTOR_SEARCH,
                    RelayNames.SCORING,
                    RelayNames.EVIDENCE_FUSION,
                    RelayNames.GRAPH_EXPANSION)) {
                assertThat(chainFor(engine, hotStage))
                        .as("hot-path stage '%s' must not be wrapped in resilience decorators", hotStage)
                        .noneMatch(s -> s.equals("Timeout") || s.equals("Retry")
                                || s.equals("Breaker") || s.equals("Bulkhead"));
            }
        }
    }

    @Nested
    @DisplayName("Shared breaker naming")
    class SharedBreakers {

        @Test
        @DisplayName("Embed breaker is one shared name with opposite onOpen per call site")
        void embedBreakerSharedNameOppositeReactions() {
            assertThat(PathwayResilience.embedProviderFailFast().name())
                    .isEqualTo(PathwayResilience.embedProviderBypass().name())
                    .isEqualTo(PathwayResilience.EMBED_PROVIDER);

            assertThat(PathwayResilience.embedProviderFailFast().onOpen())
                    .as("Recall must fail; Dream must bypass — same trip state, different reaction")
                    .isNotEqualTo(PathwayResilience.embedProviderBypass().onOpen());
        }

        @Test
        @DisplayName("Nested Remember shares one breaker and bulkhead name across Dream and Reflect")
        void nestedRememberSharedAcrossCallers() {
            assertThat(PathwayResilience.nestedRemember().name())
                    .isEqualTo(PathwayResilience.PATHWAY_REMEMBER);
            assertThat(PathwayResilience.nestedRememberBulkhead().maxInFlight())
                    .as("a sleep cycle must not swamp live Recall")
                    .isEqualTo(4);
        }
    }
}
