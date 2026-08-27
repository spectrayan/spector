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
package com.spectrayan.spector.memory.dream.relay;

import com.spectrayan.spector.commons.pathway.Specification;

/**
 * Predicate specifications guarding execution of {@link com.spectrayan.spector.commons.pathway.SynapticRelay}
 * stages within the {@link com.spectrayan.spector.memory.DreamPathway}.
 *
 * <h3>Biological Analog: Thalamic Gating during Sleep</h3>
 * <p>Blocks sensory input and regulates entry into different phases of sleep and consolidation.</p>
 *
 * @since 1.4.0
 */
public final class DreamGates {
    
    private DreamGates() {}

    /**
     * Gate evaluating whether dreaming is enabled in the configuration.
     */
    public static final Specification<DreamSignal> DREAMING_ENABLED =
            Specification.of("Dream pathway is disabled in configuration",
                    s -> s.config() != null && s.config().enabled());

    /**
     * Gate evaluating whether salient seeds were identified for generation.
     */
    public static final Specification<DreamSignal> HAS_SEEDS =
            Specification.of("No seeds identified for dream generation",
                    s -> s.seedMemoryIds() != null && !s.seedMemoryIds().isEmpty());

    /**
     * Gate evaluating whether journaling is enabled.
     */
    public static final Specification<DreamSignal> JOURNAL_ENABLED =
            Specification.of("Dream journaling is disabled in configuration",
                    s -> s.config() != null && s.config().journalEnabled());

    /**
     * Gate evaluating whether fragments were identified.
     */
    public static final Specification<DreamSignal> HAS_FRAGMENTS =
            Specification.of("No fragments available",
                    s -> !s.fragments().isEmpty());

    /**
     * Gate evaluating whether extracted insights exist.
     */
    public static final Specification<DreamSignal> HAS_INSIGHTS =
            Specification.of("No extracted insights available",
                    s -> !s.extractedInsights().isEmpty());

    /**
     * Gate evaluating whether constructed scenes are available.
     */
    public static final Specification<DreamSignal> HAS_CONSTRUCTED_SCENES =
            Specification.of("No constructed scenes available",
                    s -> !s.constructedScenes().isEmpty());

    /**
     * Gate evaluating whether Langevin dynamics are enabled.
     */
    public static final Specification<DreamSignal> LANGEVIN_ENABLED =
            Specification.of("Langevin dynamics disabled or missing tensor",
                    s -> s.distributedMemoryTensor() != null && s.config().langevinSteps() > 0);
}
