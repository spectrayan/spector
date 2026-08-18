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
}
