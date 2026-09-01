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
package com.spectrayan.spector.memory.recall.relay;

import com.spectrayan.spector.commons.pathway.Specification;
import com.spectrayan.spector.config.model.TextSearchMode;

/**
 * Defines standard specifications and gates for controlling the flow of a {@link RecallSignal}.
 */
public final class RecallGates {

    private RecallGates() {}

    /**
     * Gate evaluating whether text search should be executed.
     */
    public static final Specification<RecallSignal> TEXT_SEARCH_ENABLED =
        Specification.of(
            "text search not enabled or mode is VECTOR_ONLY",
            s -> s.options().enableTextSearch()
                 && s.options().textSearchMode() != TextSearchMode.VECTOR_ONLY);

    /**
     * Gate evaluating whether the ColBERT reranker should be executed.
     */
    public static final Specification<RecallSignal> RERANK_CONFIGURED =
        Specification.of(
            "reranker not enabled or ColBERT mode not active",
            s -> s.options().enableReranker()
                 && s.options().textSearchMode().usesColBERT());

    /**
     * Gate evaluating whether MMR diversity reranking is enabled.
     */
    public static final Specification<RecallSignal> MMR_ENABLED =
        Specification.of("MMR not enabled in recall options",
            s -> s.options().enableMmr());

    /**
     * Gate evaluating whether RRF fusion has occurred.
     */
    public static final Specification<RecallSignal> RRF_FUSED =
        Specification.of("no RRF fusion occurred in this recall",
            RecallSignal::isRrfFused);

    /**
     * Gate evaluating whether lateral inhibition & retrieval interference resolution is enabled (MR-04).
     */
    public static final Specification<RecallSignal> LATERAL_INHIBITION_ENABLED =
        Specification.of("lateral inhibition not enabled in recall options",
            s -> s.options().enableLateralInhibition());

    /**
     * Gate evaluating whether Homeostatic Affective Core bias is enabled.
     */
    public static final Specification<RecallSignal> HOMEOSTASIS_ENABLED =
        Specification.of("homeostasis not enabled in AISME options",
            s -> s.options().enableAisme() && s.options().aismeConfig().enableHomeostasis());

    /**
     * Gate evaluating whether Free-Energy Guided active inference is enabled.
     */
    public static final Specification<RecallSignal> FREE_ENERGY_ENABLED =
        Specification.of("free energy guided recall not enabled in AISME options",
            s -> s.options().enableAisme() && s.options().aismeConfig().enableFreeEnergy());

    /**
     * Gate evaluating whether Modern Hopfield Associative Memory network is enabled.
     */
    public static final Specification<RecallSignal> HOPFIELD_ENABLED =
        Specification.of("Hopfield associative recall not enabled in AISME options",
            s -> s.options().enableAisme() && s.options().aismeConfig().enableHopfield());

    /**
     * Gate evaluating whether Neural Manifold Distance reranking is enabled.
     */
    public static final Specification<RecallSignal> MANIFOLD_ENABLED =
        Specification.of("manifold reranking not enabled in AISME options",
            s -> s.options().enableAisme() && s.options().aismeConfig().enableManifold());

    /**
     * Gate evaluating whether Constructive Simulation (Predictive Coding / Narrative Self) is enabled.
     */
    public static final Specification<RecallSignal> CONSTRUCTIVE_SIMULATION_ENABLED =
        Specification.of("constructive simulation not enabled in AISME options",
            s -> s.options().enableAisme() && s.options().aismeConfig().enablePredictiveCoding());

    /**
     * Gate evaluating whether Consciousness Continuity (Phi_CC) scoring is enabled.
     */
    public static final Specification<RecallSignal> CONSCIOUSNESS_CONTINUITY_ENABLED =
        Specification.of("consciousness continuity metric not enabled in AISME options",
            s -> s.options().enableAisme() && s.options().aismeConfig().enableConsciousnessContinuity());

    /**
     * Gate evaluating whether Global Workspace conscious access broadcast is enabled.
     */
    public static final Specification<RecallSignal> CONSCIOUS_ACCESS_ENABLED =
        Specification.of("conscious access gateway not enabled in AISME options",
            s -> s.options().enableAisme() && s.options().aismeConfig().enableGlobalWorkspace());

    /**
     * Gate evaluating whether Constructive Memory Persistence (durable simulation storage) is enabled.
     */
    public static final Specification<RecallSignal> CONSTRUCTIVE_PERSISTENCE_ENABLED =
        Specification.of("constructive memory persistence not enabled in AISME options",
            s -> s.options().enableAisme() && s.options().aismeConfig().constructivePersistenceEnabled());

    /**
     * Gate evaluating whether Epistemic Learning (belief and homeostatic update) is enabled.
     */
    public static final Specification<RecallSignal> EPISTEMIC_LEARNING_ENABLED =
        Specification.of("epistemic learning not enabled in AISME options",
            s -> s.options().enableAisme() && (s.options().aismeConfig().enableFreeEnergy() || s.options().aismeConfig().enableHomeostasis()));

    /**
     * Gate evaluating whether Spacetime harmonic re-ranking is enabled (ADR-0030 v1).
     */
    public static final Specification<RecallSignal> SPACETIME_ENABLED =
        Specification.of("spacetime vector search not enabled in recall options",
            s -> s.options().enableSpacetime() && s.queryTau() != null);
}
