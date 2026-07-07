/*
 * Copyright 2025–2026 Spectrayan. Licensed under the Apache License, Version 2.0.
 */
package com.spectrayan.spector.synapse.agent.template;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Schema validation for all JSON FlowSpec template files.
 *
 * <p>Validates structural correctness of every template — required fields,
 * node types, edge references, and entry point validity. No external
 * dependencies required.</p>
 *
 * @see <a href="https://github.com/spectrayan/spector-enterprise/issues/97">#97</a>
 */
class TemplateSchemaValidationTest {

    private static final Path TEMPLATES_DIR = Path.of(
            "src/main/resources/templates");

    private static ObjectMapper mapper;

    @BeforeAll
    static void setUp() {
        mapper = new ObjectMapper();
    }

    /** Discover all JSON template files. */
    static Stream<Path> allTemplateFiles() throws IOException {
        return Files.list(TEMPLATES_DIR)
                .filter(p -> p.toString().endsWith(".json"))
                .sorted();
    }

    // ── Required Top-Level Fields ───────────────────────────────────────

    @ParameterizedTest(name = "{0}")
    @MethodSource("allTemplateFiles")
    void templateHasRequiredFields(Path jsonFile) throws Exception {
        var root = loadJson(jsonFile);
        var name = jsonFile.getFileName().toString();

        assertThat(root.has("id")).as("id in %s", name).isTrue();
        assertThat(root.has("name")).as("name in %s", name).isTrue();
        assertThat(root.has("description")).as("description in %s", name).isTrue();
        assertThat(root.has("category")).as("category in %s", name).isTrue();
        assertThat(root.has("tags")).as("tags in %s", name).isTrue();

        // id should be a valid identifier
        assertThat(root.get("id").asText())
                .as("id value in %s", name)
                .matches("[a-z][a-z0-9-]*");

        // tags should be a non-empty array
        assertThat(root.get("tags").isArray()).as("tags is array in %s", name).isTrue();
        assertThat(root.get("tags").size()).as("tags count in %s", name).isGreaterThan(0);
    }

    // ── FlowSpec Structure ──────────────────────────────────────────────

    @ParameterizedTest(name = "{0}")
    @MethodSource("allTemplateFiles")
    void templateHasValidFlowSpec(Path jsonFile) throws Exception {
        var root = loadJson(jsonFile);
        var name = jsonFile.getFileName().toString();

        assertThat(root.has("flowSpec")).as("flowSpec in %s", name).isTrue();

        var flowSpec = root.get("flowSpec");
        assertThat(flowSpec.has("version")).as("flowSpec.version in %s", name).isTrue();
        assertThat(flowSpec.has("id")).as("flowSpec.id in %s", name).isTrue();
        assertThat(flowSpec.has("name")).as("flowSpec.name in %s", name).isTrue();
        assertThat(flowSpec.has("entry_point")).as("flowSpec.entry_point in %s", name).isTrue();
        assertThat(flowSpec.has("nodes")).as("flowSpec.nodes in %s", name).isTrue();
        assertThat(flowSpec.has("edges")).as("flowSpec.edges in %s", name).isTrue();
    }

    // ── Node Validation ─────────────────────────────────────────────────

    @ParameterizedTest(name = "{0}")
    @MethodSource("allTemplateFiles")
    void templateNodesHaveRequiredFields(Path jsonFile) throws Exception {
        var root = loadJson(jsonFile);
        var name = jsonFile.getFileName().toString();
        var nodes = root.get("flowSpec").get("nodes");

        assertThat(nodes.isObject()).as("nodes is object in %s", name).isTrue();
        assertThat(nodes.size()).as("nodes count in %s", name).isGreaterThan(0);

        var fieldNames = nodes.fieldNames();
        while (fieldNames.hasNext()) {
            var nodeName = fieldNames.next();
            var node = nodes.get(nodeName);

            assertThat(node.has("type"))
                    .as("node '%s'.type in %s", nodeName, name).isTrue();
            assertThat(node.get("type").asText())
                    .as("node '%s'.type value in %s", nodeName, name)
                    .isIn("TOOL", "AGENT", "CONDITION", "FUNCTION");
            assertThat(node.has("description"))
                    .as("node '%s'.description in %s", nodeName, name).isTrue();

            // Type-specific fields
            var type = node.get("type").asText();
            if ("TOOL".equals(type)) {
                assertThat(node.has("tool_name"))
                        .as("TOOL node '%s'.tool_name in %s", nodeName, name).isTrue();
            } else if ("AGENT".equals(type)) {
                assertThat(node.has("agent"))
                        .as("AGENT node '%s'.agent in %s", nodeName, name).isTrue();
            }
        }
    }

    // ── Edge Validation ─────────────────────────────────────────────────

    @ParameterizedTest(name = "{0}")
    @MethodSource("allTemplateFiles")
    void templateEdgesReferenceValidNodes(Path jsonFile) throws Exception {
        var root = loadJson(jsonFile);
        var name = jsonFile.getFileName().toString();
        var nodes = root.get("flowSpec").get("nodes");
        var edges = root.get("flowSpec").get("edges");

        // Collect valid node names
        var nodeNames = new HashSet<String>();
        nodes.fieldNames().forEachRemaining(nodeNames::add);

        assertThat(edges.isArray()).as("edges is array in %s", name).isTrue();

        for (int i = 0; i < edges.size(); i++) {
            var edge = edges.get(i);
            assertThat(edge.has("from")).as("edge[%d].from in %s", i, name).isTrue();
            assertThat(edge.has("to")).as("edge[%d].to in %s", i, name).isTrue();

            assertThat(nodeNames).as("edge[%d].from '%s' references valid node in %s",
                    i, edge.get("from").asText(), name)
                    .contains(edge.get("from").asText());
            assertThat(nodeNames).as("edge[%d].to '%s' references valid node in %s",
                    i, edge.get("to").asText(), name)
                    .contains(edge.get("to").asText());
        }
    }

    // ── Entry Point Validation ──────────────────────────────────────────

    @ParameterizedTest(name = "{0}")
    @MethodSource("allTemplateFiles")
    void templateEntryPointReferencesValidNode(Path jsonFile) throws Exception {
        var root = loadJson(jsonFile);
        var name = jsonFile.getFileName().toString();
        var flowSpec = root.get("flowSpec");
        var entryPoint = flowSpec.get("entry_point").asText();
        var nodes = flowSpec.get("nodes");

        var nodeNames = new HashSet<String>();
        nodes.fieldNames().forEachRemaining(nodeNames::add);

        assertThat(nodeNames).as("entry_point '%s' references valid node in %s",
                entryPoint, name)
                .contains(entryPoint);
    }

    // ── Uniqueness ──────────────────────────────────────────────────────

    @Test
    void allTemplateIdsAreUnique() throws Exception {
        var ids = new HashSet<String>();
        var duplicates = new HashSet<String>();

        try (var files = Files.list(TEMPLATES_DIR)) {
            for (var file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                var root = loadJson(file);
                var id = root.get("id").asText();
                if (!ids.add(id)) {
                    duplicates.add(id + " (in " + file.getFileName() + ")");
                }
            }
        }

        assertThat(duplicates).as("Duplicate template IDs found").isEmpty();
    }

    @Test
    void allTemplateFilesExist() throws Exception {
        try (var files = Files.list(TEMPLATES_DIR)) {
            var count = files.filter(p -> p.toString().endsWith(".json")).count();
            assertThat(count)
                    .as("Expected at least 6 template JSON files")
                    .isGreaterThanOrEqualTo(6);
        }
    }

    // ── Conditional Edges (Optional) ────────────────────────────────────

    @ParameterizedTest(name = "{0}")
    @MethodSource("allTemplateFiles")
    void templateConditionalEdgesAreValid(Path jsonFile) throws Exception {
        var root = loadJson(jsonFile);
        var name = jsonFile.getFileName().toString();
        var flowSpec = root.get("flowSpec");

        if (!flowSpec.has("conditional_edges")) return; // Optional field

        var condEdges = flowSpec.get("conditional_edges");
        assertThat(condEdges.isArray()).as("conditional_edges is array in %s", name).isTrue();

        var nodeNames = new HashSet<String>();
        flowSpec.get("nodes").fieldNames().forEachRemaining(nodeNames::add);

        for (int i = 0; i < condEdges.size(); i++) {
            var condEdge = condEdges.get(i);
            assertThat(condEdge.has("from"))
                    .as("conditional_edge[%d].from in %s", i, name).isTrue();
            assertThat(nodeNames)
                    .as("conditional_edge[%d].from references valid node in %s", i, name)
                    .contains(condEdge.get("from").asText());
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private JsonNode loadJson(Path path) throws Exception {
        return mapper.readTree(path.toFile());
    }
}
