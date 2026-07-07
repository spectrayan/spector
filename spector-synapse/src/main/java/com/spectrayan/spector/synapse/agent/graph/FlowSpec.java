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
package com.spectrayan.spector.synapse.agent.graph;

import java.util.List;
import java.util.Map;

/**
 * FlowSpec DSL — a declarative specification for agent graph execution flows.
 *
 * <p>FlowSpec defines nodes, edges, and conditional routing for a LangGraph4j
 * graph. It can be created programmatically or loaded from YAML configuration.</p>
 *
 * @param name            flow name
 * @param description     human-readable description
 * @param entryPoint      name of the entry node
 * @param nodes           list of node definitions
 * @param edges           list of edge connections
 * @param conditionalEdges list of conditional edge definitions
 */
public record FlowSpec(
        String name,
        String description,
        String entryPoint,
        List<NodeSpec> nodes,
        List<EdgeSpec> edges,
        List<ConditionalEdgeSpec> conditionalEdges
) {

    /** A node in the graph. */
    public record NodeSpec(
            String name,
            String type,           // "retrieve", "generate", "evaluate", "tool"
            Map<String, String> config
    ) {
        public NodeSpec {
            if (config == null) config = Map.of();
        }
    }

    /** A direct edge between two nodes. */
    public record EdgeSpec(
            String from,
            String to
    ) {}

    /** A conditional edge with routing function. */
    public record ConditionalEdgeSpec(
            String from,
            String routerType,     // "quality_check", "tool_needed", "custom"
            Map<String, String> routes  // condition -> target node
    ) {
        public ConditionalEdgeSpec {
            if (routes == null) routes = Map.of();
        }
    }

    /** Builder for creating FlowSpec instances. */
    public static Builder builder(String name) {
        return new Builder(name);
    }

    public static class Builder {
        private final String name;
        private String description = "";
        private String entryPoint;
        private final java.util.ArrayList<NodeSpec> nodes = new java.util.ArrayList<>();
        private final java.util.ArrayList<EdgeSpec> edges = new java.util.ArrayList<>();
        private final java.util.ArrayList<ConditionalEdgeSpec> conditionalEdges = new java.util.ArrayList<>();

        Builder(String name) { this.name = name; }

        public Builder description(String desc) { this.description = desc; return this; }
        public Builder entryPoint(String entry) { this.entryPoint = entry; return this; }

        public Builder addNode(String name, String type) {
            nodes.add(new NodeSpec(name, type, Map.of()));
            return this;
        }

        public Builder addNode(String name, String type, Map<String, String> config) {
            nodes.add(new NodeSpec(name, type, config));
            return this;
        }

        public Builder addEdge(String from, String to) {
            edges.add(new EdgeSpec(from, to));
            return this;
        }

        public Builder addConditionalEdge(String from, String routerType, Map<String, String> routes) {
            conditionalEdges.add(new ConditionalEdgeSpec(from, routerType, routes));
            return this;
        }

        public FlowSpec build() {
            return new FlowSpec(name, description, entryPoint,
                    List.copyOf(nodes), List.copyOf(edges), List.copyOf(conditionalEdges));
        }
    }
}
