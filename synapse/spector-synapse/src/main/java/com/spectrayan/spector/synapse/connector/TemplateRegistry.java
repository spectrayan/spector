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
package com.spectrayan.spector.synapse.connector;

import com.spectrayan.spector.synapse.connector.ConnectorDto.TemplateDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for connector templates.
 *
 * <p>Templates define the configuration schema for each connector type
 * (e.g., File System, Git, Confluence). Templates are loaded from YAML
 * configs at startup and can also be registered dynamically.</p>
 */
@Component
public class TemplateRegistry {

    private static final Logger log = LoggerFactory.getLogger(TemplateRegistry.class);

    private final ConcurrentHashMap<String, TemplateDescriptor> templates = new ConcurrentHashMap<>();

    /** Register a template. */
    public void register(TemplateDescriptor template) {
        templates.put(template.id(), template);
        log.info("[ConnectorTemplate] Registered: {} ({})", template.name(), template.type());
    }

    /** Get a template by ID. */
    public Optional<TemplateDescriptor> get(String id) {
        return Optional.ofNullable(templates.get(id));
    }

    /** List all templates. */
    public List<TemplateDescriptor> all() {
        return List.copyOf(templates.values());
    }

    /** Get all templates as a map. */
    public Map<String, TemplateDescriptor> allAsMap() {
        return Collections.unmodifiableMap(templates);
    }

    /** Check if a template is registered. */
    public boolean has(String id) {
        return templates.containsKey(id);
    }

    /** Get template count. */
    public int count() {
        return templates.size();
    }
}
