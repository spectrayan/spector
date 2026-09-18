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
 * GraphDsl — a programmatic DSL for building agent graph execution flows.
 *
 * <p>Defines nodes, edges, and conditional routing for a LangGraph4j
 * graph. This is the programmatic builder API; for JSON-based declarative
 * flows, see {@link com.spectrayan.spector.synapse.agent.graph.spec.FlowSpec}.</p>
 *
 * @param name            flow name
 * @param description     human-readable description
 * @param entryPoint      name of the entry node
 * @param nodes           list of node definitions
 * @param edges           list of edge connections
 * @param conditionalEdges list of conditional edge definitions
 */
public record GraphDsl(
        String name,
        String description,
        String entryPoint,
        List<NodeDef> nodes,
        List<EdgeDef> edges,
        List<ConditionalEdgeDef> conditionalEdges
) {

    /** A node definition in the graph. */
    public record NodeDef(
            String name,
            String type,           // "retrieve", "generate", "evaluate", "reflection", "tool"
            Map<String, String> config
    ) {
        public NodeDef {
            if (config == null) config = Map.of();
        }
    }

    /** A direct edge between two nodes. */
    public record EdgeDef(
            String from,
            String to
    ) {}

    /** A conditional edge with routing function. */
    public record ConditionalEdgeDef(
            String from,
            String routerType,     // "quality_check", "reflection_check", "tool_needed", "custom"
            Map<String, String> routes  // condition -> target node
    ) {
        public ConditionalEdgeDef {
            if (routes == null) routes = Map.of();
        }
    }

    /** Builder for creating GraphDsl instances. */
    public static Builder builder(String name) {
        return new Builder(name);
    }

    public static class Builder {
        private final String name;
        private String description = "";
        private String entryPoint;
        private final java.util.ArrayList<NodeDef> nodes = new java.util.ArrayList<>();
        private final java.util.ArrayList<EdgeDef> edges = new java.util.ArrayList<>();
        private final java.util.ArrayList<ConditionalEdgeDef> conditionalEdges = new java.util.ArrayList<>();

        Builder(String name) { this.name = name; }

        public Builder description(String desc) { this.description = desc; return this; }
        public Builder entryPoint(String entry) { this.entryPoint = entry; return this; }

        public Builder addNode(String name, String type) {
            nodes.add(new NodeDef(name, type, Map.of()));
            return this;
        }

        public Builder addNode(String name, String type, Map<String, String> config) {
            nodes.add(new NodeDef(name, type, config));
            return this;
        }

        public Builder addEdge(String from, String to) {
            edges.add(new EdgeDef(from, to));
            return this;
        }

        public Builder addConditionalEdge(String from, String routerType, Map<String, String> routes) {
            conditionalEdges.add(new ConditionalEdgeDef(from, routerType, routes));
            return this;
        }

        public GraphDsl build() {
            return new GraphDsl(name, description, entryPoint,
                    List.copyOf(nodes), List.copyOf(edges), List.copyOf(conditionalEdges));
        }
    }
}
