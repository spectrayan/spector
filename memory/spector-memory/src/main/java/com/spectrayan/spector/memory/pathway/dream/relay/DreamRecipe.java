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
package com.spectrayan.spector.memory.pathway.dream.relay;

import com.spectrayan.spector.commons.pathway.ErrorPolicy;
import com.spectrayan.spector.commons.pathway.PathwayComposer;
import com.spectrayan.spector.commons.pathway.PathwayRecipe;
import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.memory.pathway.PathwayResilience;
import com.spectrayan.spector.memory.pathway.RelayNames;
import com.spectrayan.spector.memory.pathway.simulation.relay.SpacetimeSeedRelay;

/**
 * Declarative recipe for composing the Dream cognitive pathway (ADR-0035 §7.3).
 *
 * <p>Dream was the last composer-based pathway whose stage list lived inline in its pathway
 * constructor, which kept its shape invisible to the M6.4 recipe parity gate — awkward, because
 * Dream carries the most delicate wiring in the system: an {@code ABORT} gate, an LLM stage, and
 * a nested-Remember stage that deliberately has no timeout. Extracting the recipe puts that
 * wiring under the same assertion as Remember, Recall and Reflect.</p>
 *
 * <p>Every relay here is stateless and no-arg, so the recipe takes no constructor
 * parameters. Relays are supplied through overridable factory methods purely so tests can
 * substitute a probe without reimplementing the stage graph.</p>
 */
public class DreamRecipe implements PathwayRecipe<DreamSignal> {

    @Override
    public void compose(final PathwayComposer<DreamSignal> composer) {
        // 1. Dream Gate (circadian & sleep pressure check).
        // ABORT, not DEGRADE: not dreaming is not a failure, and letting the remaining
        // eleven stages run against an empty signal burns a sleep cycle to produce the
        // same empty report (ADR-0036 §14 Dream row).
        composer.relay(RelayNames.DREAM_GATE, dreamGate(), ErrorPolicy.ABORT);

        // 2. Salient Seed Selection (TMR + Soul / Salience Matching)
        composer.gated(RelayNames.SALIENT_SEED, DreamGates.DREAMING_ENABLED, salientSeed(), ErrorPolicy.DEGRADE_GRACEFULLY);

        // 2b. Spacetime Shortlist Seed Selection (ADR-0031)
        composer.gated(RelayNames.SPACETIME_SEED, DreamGates.DREAMING_ENABLED, spacetimeSeed(), ErrorPolicy.DEGRADE_GRACEFULLY);

        // 3. Fragment Unpack (entity/role/affect decomposition)
        composer.gated(RelayNames.FRAGMENT_UNPACK, DreamGates.HAS_SEEDS, fragmentUnpack(), ErrorPolicy.DEGRADE_GRACEFULLY);

        // 4. Anti-Centroid Hyper-Association
        composer.gated(RelayNames.HYPER_ASSOCIATE, DreamGates.HAS_FRAGMENTS, hyperAssociate(), ErrorPolicy.DEGRADE_GRACEFULLY);

        // 5. REM Compressed Replay with Hartmann Boundary Modulated Hoel Noise
        composer.gated(RelayNames.REM_REPLAY, DreamGates.HAS_SEEDS, remReplay(), ErrorPolicy.DEGRADE_GRACEFULLY);

        // 6. Compositional Scene Construction — may call the LLM. 8s budget, shared
        // llm-provider breaker (BYPASS: a failed scene just means less dreaming) and a
        // 2-permit bulkhead so a sleep cycle cannot monopolise the provider. No retry:
        // regenerating a scene is not free and not idempotent.
        composer.stage(RelayNames.SCENE_CONSTRUCT)
                .relay(sceneConstruct())
                .gate(DreamGates.HAS_SEEDS)
                .policy(ErrorPolicy.DEGRADE_GRACEFULLY)
                .timeout(PathwayResilience.LLM_TIMEOUT)
                .breaker(PathwayResilience.llmProvider())
                .bulkhead(PathwayResilience.LLM_PROVIDER, PathwayResilience.llmBulkhead())
                .add();

        // 7. Predictive Coding Reality Testing & Counterfactual Probing
        composer.gated(RelayNames.COUNTERFACTUAL_PROBE, DreamGates.HAS_CONSTRUCTED_SCENES, counterfactualProbe(), ErrorPolicy.DEGRADE_GRACEFULLY);

        // 8. Langevin Stochastic SDE Discovery with Soul Attractor Potential
        composer.gated(RelayNames.LANGEVIN_DISCOVERY, DreamGates.LANGEVIN_ENABLED, langevinDiscovery(), ErrorPolicy.DEGRADE_GRACEFULLY);

        // 9. Prefrontal Multi-Soul EFE Triage & Ethical Reality Testing
        composer.gated(RelayNames.EFE_TRIAGE, DreamGates.HAS_CONSTRUCTED_SCENES, efeTriage(), ErrorPolicy.DEGRADE_GRACEFULLY);

        // 10. Distill Residue, Discard Scaffold (Concept Extraction)
        composer.gated(RelayNames.CONCEPT_EXTRACT, DreamGates.DREAMING_ENABLED, conceptExtract(), ErrorPolicy.DEGRADE_GRACEFULLY);

        // 11. Dream Journal Recording (Audit Trail)
        composer.gated(RelayNames.DREAM_JOURNAL, DreamGates.JOURNAL_ENABLED, dreamJournal(), ErrorPolicy.DEGRADE_GRACEFULLY);

        // 12. Ingestion & Hebbian Synaptic Inhibition — invokes the nested Remember
        // pathway once per surviving scene. Protected by ADMISSION CONTROL ONLY
        // (ADR-0036 §7.3): the pathway:remember breaker plus a 4-permit bulkhead so a
        // sleep cycle cannot pile nested writes on top of live Recall. Deliberately NO
        // timeout — Remember ends in an mmap + WAL write that cannot observe an
        // interrupt, so a budget would report a timeout while the write completed on a
        // detached thread, leaving Dream to inhibit a pair it wrongly believes failed.
        composer.stage(RelayNames.DREAM_INGESTION)
                .relay(dreamIngestion())
                .gate(DreamGates.DREAMING_ENABLED)
                .policy(ErrorPolicy.DEGRADE_GRACEFULLY)
                .breaker(PathwayResilience.nestedRemember())
                .bulkhead(PathwayResilience.PATHWAY_REMEMBER,
                        PathwayResilience.nestedRememberBulkhead())
                .add();
    }

    /** Returns the circadian and sleep-pressure gate relay. */
    protected SynapticRelay<DreamSignal> dreamGate() {
        return new DreamGateRelay();
    }

    /** Returns the TMR and soul-salience seed selection relay. */
    protected SynapticRelay<DreamSignal> salientSeed() {
        return new SalientSeedRelay();
    }

    /** Returns the spacetime shortlist seed selection relay (ADR-0031). */
    protected SynapticRelay<DreamSignal> spacetimeSeed() {
        return new SpacetimeSeedRelay.DreamSeedRelay();
    }

    /** Returns the entity/role/affect fragment decomposition relay. */
    protected SynapticRelay<DreamSignal> fragmentUnpack() {
        return new FragmentUnpackRelay();
    }

    /** Returns the anti-centroid hyper-association relay. */
    protected SynapticRelay<DreamSignal> hyperAssociate() {
        return new HyperAssociateRelay();
    }

    /** Returns the REM compressed replay relay. */
    protected SynapticRelay<DreamSignal> remReplay() {
        return new RemReplayRelay();
    }

    /** Returns the compositional scene construction relay (may call the LLM). */
    protected SynapticRelay<DreamSignal> sceneConstruct() {
        return new SceneConstructRelay();
    }

    /** Returns the predictive-coding reality testing relay. */
    protected SynapticRelay<DreamSignal> counterfactualProbe() {
        return new CounterfactualProbeRelay();
    }

    /** Returns the Langevin stochastic SDE discovery relay. */
    protected SynapticRelay<DreamSignal> langevinDiscovery() {
        return new LangevinDiscoveryRelay();
    }

    /** Returns the prefrontal multi-soul EFE triage relay. */
    protected SynapticRelay<DreamSignal> efeTriage() {
        return new EfeTriageRelay();
    }

    /** Returns the concept extraction (residue distillation) relay. */
    protected SynapticRelay<DreamSignal> conceptExtract() {
        return new ConceptExtractRelay();
    }

    /** Returns the dream journal audit-trail relay. */
    protected SynapticRelay<DreamSignal> dreamJournal() {
        return new DreamJournalRelay();
    }

    /** Returns the nested-Remember ingestion and Hebbian inhibition relay. */
    protected SynapticRelay<DreamSignal> dreamIngestion() {
        return new DreamIngestionRelay();
    }
}
