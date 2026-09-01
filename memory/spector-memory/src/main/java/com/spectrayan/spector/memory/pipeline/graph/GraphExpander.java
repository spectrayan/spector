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
package com.spectrayan.spector.memory.pipeline.graph;

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

import com.spectrayan.spector.memory.hebbian.HebbianGraphBase;
import com.spectrayan.spector.memory.temporal.TemporalChainMemory;

/**
 * Expands recalled candidate nodes along Hebbian co-activation edges and temporal chain links.
 */
public class GraphExpander {

    private final HebbianGraphBase hebbianGraph;
    private final TemporalChainMemory temporalChain;

    public GraphExpander(HebbianGraphBase hebbianGraph, TemporalChainMemory temporalChain) {
        this.hebbianGraph = hebbianGraph;
        this.temporalChain = temporalChain;
    }

    public HebbianGraphBase hebbianGraph() {
        return hebbianGraph;
    }

    public TemporalChainMemory temporalChain() {
        return temporalChain;
    }
}
