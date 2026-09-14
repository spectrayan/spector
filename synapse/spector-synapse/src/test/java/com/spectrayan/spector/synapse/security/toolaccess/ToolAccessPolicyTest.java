/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-synapse/LICENSE
 *
 * Change Date: July 6, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.synapse.security.toolaccess;

import com.spectrayan.spector.synapse.security.config.SecurityProperties;
import com.spectrayan.spector.synapse.security.toolaccess.ToolAccessPolicy.AgentRule;
import com.spectrayan.spector.synapse.security.toolaccess.ToolAccessPolicy.DefaultMode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ToolAccessPolicy")
class ToolAccessPolicyTest {

    private static final Set<String> REGISTERED = Set.of(
            "web_search", "memory_recall", "calculator", "shell_execution");

    @Test
    @DisplayName("loads default deny-all from classpath YAML")
    void loadsClasspathYaml() throws Exception {
        try (InputStream in = ToolAccessPolicy.class.getResourceAsStream(ToolAccessPolicy.RESOURCE_PATH)) {
            assertThat(in).as("tool-access.yml must be on the classpath").isNotNull();
        }
        SecurityProperties.ToolAccessProperties props = new SecurityProperties.ToolAccessProperties();
        // Do not override default — use YAML deny-all
        ToolAccessPolicy policy = new ToolAccessPolicy(props);
        assertThat(policy.isEnabled()).isTrue();
        assertThat(policy.defaultWhenNoEntry()).isEqualTo(DefaultMode.DENY_ALL);
        assertThat(policy.effectiveTools("unknown-agent", List.of(), REGISTERED)).isEmpty();
    }

    @Test
    @DisplayName("application override can set allow-all when no entry")
    void propsOverrideDefaultToAllowAll() {
        SecurityProperties.ToolAccessProperties props = new SecurityProperties.ToolAccessProperties();
        props.setDefaultWhenNoEntry(DefaultMode.ALLOW_ALL);
        ToolAccessPolicy policy = new ToolAccessPolicy(props);
        assertThat(policy.defaultWhenNoEntry()).isEqualTo(DefaultMode.ALLOW_ALL);
        assertThat(policy.effectiveTools("unknown-agent", List.of(), REGISTERED))
                .containsExactlyInAnyOrderElementsOf(REGISTERED);
    }

    @Test
    @DisplayName("agent allowlist of 2 tools exposes only those tools")
    void agentAllowlistOfTwo() {
        Map<String, AgentRule> agents = new LinkedHashMap<>();
        agents.put("researcher", new AgentRule(List.of("web_search", "memory_recall"), List.of()));
        ToolAccessPolicy policy = ToolAccessPolicy.of(DefaultMode.DENY_ALL, agents, true);

        assertThat(policy.effectiveTools("researcher", List.of(), REGISTERED))
                .containsExactly("web_search", "memory_recall");
        assertThat(policy.isAllowed("researcher", "calculator", List.of(), REGISTERED)).isFalse();
        assertThat(policy.isAllowed("researcher", "web_search", List.of(), REGISTERED)).isTrue();
    }

    @Test
    @DisplayName("intersection with soul.tools when hint is non-empty")
    void intersectionWithSoulToolsHint() {
        Map<String, AgentRule> agents = Map.of(
                "researcher", new AgentRule(List.of("web_search", "memory_recall", "calculator"), List.of()));
        ToolAccessPolicy policy = ToolAccessPolicy.of(DefaultMode.DENY_ALL, agents, true);

        assertThat(policy.effectiveTools("researcher",
                List.of("web_search", "shell_execution"), REGISTERED))
                .containsExactly("web_search");
    }

    @Test
    @DisplayName("empty soul.tools uses Synapse policy alone")
    void emptySoulToolsUsesPolicyAlone() {
        Map<String, AgentRule> agents = Map.of(
                "researcher", new AgentRule(List.of("web_search", "memory_recall"), List.of()));
        ToolAccessPolicy policy = ToolAccessPolicy.of(DefaultMode.DENY_ALL, agents, true);

        assertThat(policy.effectiveTools("researcher", List.of(), REGISTERED))
                .containsExactly("web_search", "memory_recall");
    }

    @Test
    @DisplayName("denylist subtracts from allowlist")
    void denylistSubtracts() {
        Map<String, AgentRule> agents = Map.of(
                "researcher", new AgentRule(
                        List.of("web_search", "memory_recall"),
                        List.of("web_search")));
        ToolAccessPolicy policy = ToolAccessPolicy.of(DefaultMode.DENY_ALL, agents, true);

        assertThat(policy.effectiveTools("researcher", List.of(), REGISTERED))
                .containsExactly("memory_recall");
    }

    @Test
    @DisplayName("wildcard entry applies when specific agent id missing")
    void wildcardFallback() {
        Map<String, AgentRule> agents = Map.of(
                "*", new AgentRule(List.of("calculator"), List.of()));
        ToolAccessPolicy policy = ToolAccessPolicy.of(DefaultMode.DENY_ALL, agents, true);

        assertThat(policy.effectiveTools("any-agent", List.of(), REGISTERED))
                .containsExactly("calculator");
    }

    @Test
    @DisplayName("disabled policy passes all registered tools")
    void disabledIsAllowAll() {
        Map<String, AgentRule> agents = Map.of(
                "researcher", new AgentRule(List.of("calculator"), List.of()));
        ToolAccessPolicy policy = ToolAccessPolicy.of(DefaultMode.DENY_ALL, agents, false);

        assertThat(policy.effectiveTools("researcher", List.of(), REGISTERED))
                .containsExactlyInAnyOrderElementsOf(REGISTERED);
    }

    @Test
    @DisplayName("omit allow with deny-only rule starts from all registered")
    void denyOnlyRule() {
        Map<String, AgentRule> agents = Map.of(
                "ops", new AgentRule(null, List.of("shell_execution")));
        ToolAccessPolicy policy = ToolAccessPolicy.of(DefaultMode.DENY_ALL, agents, true);

        assertThat(policy.effectiveTools("ops", List.of(), REGISTERED))
                .containsExactlyInAnyOrder("web_search", "memory_recall", "calculator");
    }

    @Test
    @DisplayName("parse YAML allowlist and default")
    void parseYamlDocument() {
        String yaml = """
                default: allow-all
                agents:
                  researcher:
                    allow:
                      - web_search
                      - memory_recall
                    deny:
                      - memory_recall
                """;
        var doc = ToolAccessPolicy.parse(
                new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));
        assertThat(doc.defaultMode()).isEqualTo(DefaultMode.ALLOW_ALL);
        assertThat(doc.agents()).containsKey("researcher");
        assertThat(doc.agents().get("researcher").allow())
                .containsExactly("web_search", "memory_recall");
        assertThat(doc.agents().get("researcher").deny()).containsExactly("memory_recall");
    }

    @Test
    @DisplayName("permission denied message includes SPE-500-013")
    void permissionMessage() {
        assertThat(ToolAccessPolicy.permissionDeniedMessage("shell_execution"))
                .contains("SPE-500-013")
                .contains("shell_execution")
                .contains("Permission denied");
    }
}
