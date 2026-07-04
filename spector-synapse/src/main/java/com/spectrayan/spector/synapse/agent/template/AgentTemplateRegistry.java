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
package com.spectrayan.spector.synapse.agent.template;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Registry of pre-built FlowSpec templates — auto-discovers templates from classpath.
 *
 * <p>Templates are loaded at startup from {@code templates/*.json} on the classpath.
 * New templates are added by dropping a JSON file in the templates directory —
 * no code changes required.</p>
 */
@Component
public class AgentTemplateRegistry {

    private static final Logger log = LoggerFactory.getLogger(AgentTemplateRegistry.class);
    private static final String TEMPLATE_DIR = "templates/";

    private final Map<String, FlowTemplate> templates = new LinkedHashMap<>();

    /**
     * Constructor — loads all built-in templates from the classpath templates directory.
     */
    public AgentTemplateRegistry() {
        loadBuiltInTemplates();
        log.info("[TemplateRegistry] Loaded {} templates: {}", templates.size(), templates.keySet());
    }

    /** Look up a template by ID. */
    public Optional<FlowTemplate> get(String templateId) {
        return Optional.ofNullable(templates.get(templateId));
    }

    /** Get a template or throw. */
    public FlowTemplate require(String templateId) {
        return get(templateId).orElseThrow(() ->
                new IllegalArgumentException("Template not found: " + templateId));
    }

    /** Get all registered template IDs. */
    public Set<String> templateIds() {
        return Collections.unmodifiableSet(templates.keySet());
    }

    /** Get all templates. */
    public List<FlowTemplate> all() {
        return List.copyOf(templates.values());
    }

    /** Get templates by category. */
    public List<FlowTemplate> byCategory(String category) {
        return templates.values().stream()
                .filter(t -> category.equalsIgnoreCase(t.category()))
                .toList();
    }

    /** Number of registered templates. */
    public int size() { return templates.size(); }

    /** Register a template programmatically. */
    public void register(FlowTemplate template) {
        templates.put(template.id(), template);
        log.debug("[TemplateRegistry] Registered template: {}", template.id());
    }

    private void loadBuiltInTemplates() {
        String[] builtInTemplates = {
                "research-agent",
                "code-generation-agent",
                "data-analysis-agent",
                "content-creation-agent",
                "customer-support-agent",
                "conversation-agent"
        };

        for (String templateId : builtInTemplates) {
            try {
                var template = FlowTemplate.fromClasspath(TEMPLATE_DIR + templateId + ".json");
                templates.put(template.id(), template);
            } catch (IOException e) {
                log.debug("[TemplateRegistry] Template '{}' not found on classpath — skipping", templateId);
            }
        }
    }
}
