/*
 * Copyright 2025–2026 Spectrayan. Licensed under the Apache License, Version 2.0.
 */
package com.spectrayan.spector.synapse.agent.template;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for FlowTemplate loading and TemplateRegistry.
 */
class AgentTemplateRegistryTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "research-agent",
            "code-generation-agent",
            "data-analysis-agent",
            "content-creation-agent",
            "customer-support-agent",
            "conversation-agent"
    })
    void loadTemplateFromClasspath(String templateId) throws IOException {
        var template = FlowTemplate.fromClasspath("templates/" + templateId + ".json");
        assertThat(template.id()).isEqualTo(templateId);
        assertThat(template.name()).isNotBlank();
        assertThat(template.description()).isNotBlank();
        assertThat(template.category()).isNotBlank();
        assertThat(template.tags()).isNotEmpty();
        assertThat(template.flowSpec()).isNotEmpty();
        assertThat(template.flowSpec()).containsKey("nodes");
        assertThat(template.flowSpec()).containsKey("entry_point");
    }

    @Test
    void registryLoadsAllTemplates() {
        var registry = new AgentTemplateRegistry();
        assertThat(registry.size()).isEqualTo(6);
        assertThat(registry.templateIds()).containsExactlyInAnyOrder(
                "research-agent",
                "code-generation-agent",
                "data-analysis-agent",
                "content-creation-agent",
                "customer-support-agent",
                "conversation-agent"
        );
    }

    @Test
    void registryLookupById() {
        var registry = new AgentTemplateRegistry();
        var template = registry.require("research-agent");
        assertThat(template.category()).isEqualTo("research");
    }

    @Test
    void registryFilterByCategory() {
        var registry = new AgentTemplateRegistry();
        var codeTemplates = registry.byCategory("code");
        assertThat(codeTemplates).hasSize(1);
        assertThat(codeTemplates.getFirst().id()).isEqualTo("code-generation-agent");
    }

    @Test
    void registryProgrammaticRegistration() {
        var registry = new AgentTemplateRegistry();
        registry.register(new FlowTemplate("custom-agent", "Custom", "A custom agent", "custom", null, null));
        assertThat(registry.size()).isEqualTo(7);
        assertThat(registry.get("custom-agent")).isPresent();
    }

    @Test
    void templateHasCorrectFlowSpecStructure() throws IOException {
        var template = FlowTemplate.fromClasspath("templates/research-agent.json");
        var flowSpec = template.flowSpec();

        assertThat(flowSpec.get("version")).isEqualTo("1.0");
        assertThat(flowSpec.get("entry_point")).isEqualTo("plan");

        @SuppressWarnings("unchecked")
        var nodes = (java.util.Map<String, Object>) flowSpec.get("nodes");
        assertThat(nodes).containsKeys("plan", "search", "fetch", "analyze", "synthesize");
    }
}
