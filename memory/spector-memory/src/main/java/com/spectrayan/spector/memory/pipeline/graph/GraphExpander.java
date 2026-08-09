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
package com.spectrayan.spector.memory.pipeline.graph;

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
