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
package com.spectrayan.spector.connector.template;

import com.spectrayan.spector.connector.model.TemplateDescriptor;
import com.spectrayan.spector.connector.spi.TemplateConfigProvider;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.TemplatedRouteBuilder;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.RoutesLoader;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.ResourceHelper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Composite template registry — serves templates from multiple sources:
 * <ol>
 *   <li><b>YAML files (classpath)</b>: Shipped descriptors in
 *       {@code templates/built-in-templates.yaml}.</li>
 *   <li><b>YAML files (filesystem)</b>: Admin-managed overrides and
 *       custom templates in a configurable external directory.</li>
 *   <li><b>SPI (TemplateConfigProvider)</b>: Programmatic or DB-backed
 *       custom templates — optional, for future use.</li>
 * </ol>
 *
 * <p>Route templates (the actual Camel routeTemplate DSL) are loaded
 * from {@code route-templates.yaml} via Camel's native {@link RoutesLoader},
 * following the same pattern as synaptiq's {@code CamelEngineManager}.</p>
 *
 * <p>This design means <b>no code changes are needed</b> to add new
 * integration types. Just add a YAML file to the templates directory.</p>
 */
public class TemplateRegistry {

    private static final Logger log = LoggerFactory.getLogger(TemplateRegistry.class);

    /** All loaded template descriptors, indexed by templateId. */
    private final Map<String, TemplateDescriptor> templates = new ConcurrentHashMap<>();

    /** Set of template IDs that are built-in (cannot be modified/deleted). */
    private final Set<String> builtInIds = ConcurrentHashMap.newKeySet();

    /** Optional SPI provider for additional custom templates. */
    private final TemplateConfigProvider templateConfigProvider;

    /** Tracks which custom YAML templates have been loaded into a CamelContext. */
    private final Set<String> loadedCustomTemplates = ConcurrentHashMap.newKeySet();

    /** Loader for YAML template files. */
    private final YamlTemplateLoader yamlLoader;

    /** External templates directory (nullable — classpath only if null). */
    private final Path externalDir;

    /**
     * Creates a template registry that loads from YAML files.
     *
     * @param externalDir           external templates directory (nullable)
     * @param templateConfigProvider optional SPI for additional custom templates (nullable)
     */
    public TemplateRegistry(Path externalDir, TemplateConfigProvider templateConfigProvider) {
        this.externalDir = externalDir;
        this.templateConfigProvider = templateConfigProvider;
        this.yamlLoader = new YamlTemplateLoader();

        // Load template descriptors from YAML
        List<TemplateDescriptor> loaded = yamlLoader.loadAll(externalDir);
        loaded.forEach(t -> {
            templates.put(t.templateId(), t);
            builtInIds.add(t.templateId());
        });

        log.info("Template registry initialized with {} templates from YAML", templates.size());
    }

    /**
     * Convenience constructor — YAML-only, no SPI provider.
     */
    public TemplateRegistry(Path externalDir) {
        this(externalDir, null);
    }

    // ═══════════════════════════════════════════════════════════════
    //  Route Template Loading (Camel YAML DSL)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Load Camel YAML DSL route templates into the given CamelContext.
     *
     * <p>This loads the {@code route-templates.yaml} file via Camel's
     * native {@link RoutesLoader}, exactly like synaptiq's
     * {@code CamelEngineManager.loadRouteFromYaml()}.</p>
     *
     * @param context the CamelContext to load templates into
     */
    public void loadRouteTemplatesInto(CamelContext context) {
        Objects.requireNonNull(context, "CamelContext must not be null");

        Optional<String> routeYaml = yamlLoader.loadRouteTemplateYaml(externalDir);
        if (routeYaml.isPresent()) {
            try {
                Resource resource = ResourceHelper.fromBytes(
                        "route-templates.yaml", routeYaml.get().getBytes());
                RoutesLoader loader = PluginHelper.getRoutesLoader(context);
                loader.loadRoutes(resource);
                log.info("Loaded Camel route templates from YAML into CamelContext");
            } catch (Exception e) {
                throw new IllegalStateException(
                        "Failed to load route templates YAML into CamelContext", e);
            }
        } else {
            log.warn("No route-templates.yaml found — no built-in route templates loaded");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Query
    // ═══════════════════════════════════════════════════════════════

    /**
     * List all templates: YAML-loaded + SPI custom.
     */
    public List<TemplateDescriptor> listTemplates() {
        List<TemplateDescriptor> result = new ArrayList<>(templates.values());
        if (templateConfigProvider != null) {
            templateConfigProvider.findAll().stream()
                    .filter(t -> !templates.containsKey(t.templateId()))
                    .forEach(result::add);
        }
        return List.copyOf(result);
    }

    /**
     * Find a template by ID — checks loaded templates first, then SPI.
     */
    public Optional<TemplateDescriptor> findTemplate(String templateId) {
        TemplateDescriptor loaded = templates.get(templateId);
        if (loaded != null) return Optional.of(loaded);

        if (templateConfigProvider != null) {
            return templateConfigProvider.findByTemplateId(templateId);
        }
        return Optional.empty();
    }

    /**
     * Find the default template for a given connector type.
     */
    public Optional<TemplateDescriptor> findDefaultTemplateForType(String connectorType) {
        return templates.values().stream()
                .filter(t -> connectorType.equals(t.connectorType()))
                .findFirst()
                .or(() -> {
                    if (templateConfigProvider != null) {
                        return templateConfigProvider.findAll().stream()
                                .filter(t -> connectorType.equals(t.connectorType()))
                                .filter(t -> !templates.containsKey(t.templateId()))
                                .findFirst();
                    }
                    return Optional.empty();
                });
    }

    // ═══════════════════════════════════════════════════════════════
    //  Route Instantiation
    // ═══════════════════════════════════════════════════════════════

    /**
     * Instantiate a route from a template in the given CamelContext.
     *
     * <p>Uses Camel's native {@link TemplatedRouteBuilder} which references
     * the route templates already loaded via {@link #loadRouteTemplatesInto}.</p>
     *
     * @param context    the CamelContext
     * @param templateId the template to instantiate
     * @param routeId    the route ID to assign
     * @param tenantId   the tenant for isolation
     * @param parameters user-provided parameters
     */
    public void instantiate(CamelContext context,
                            String templateId,
                            String routeId,
                            String tenantId,
                            Map<String, String> parameters) {
        Objects.requireNonNull(context, "CamelContext must not be null");
        Objects.requireNonNull(templateId, "templateId must not be null");
        Objects.requireNonNull(routeId, "routeId must not be null");

        TemplateDescriptor descriptor = findTemplate(templateId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Template not found: " + templateId));

        // For SPI-provided custom templates with inline routeYaml,
        // load the YAML into CamelContext first
        if (!builtInIds.contains(templateId) && descriptor.routeYaml() != null) {
            loadCustomTemplateYaml(context, templateId, descriptor.routeYaml());
        }

        // Use Camel's native TemplatedRouteBuilder for instantiation
        TemplatedRouteBuilder builder = TemplatedRouteBuilder.builder(context, templateId)
                .routeId(routeId)
                .parameter("tenantId", tenantId != null ? tenantId : "default")
                .parameter("routeId", routeId);

        parameters.forEach(builder::parameter);

        try {
            builder.add();
            log.info("Instantiated template '{}' as route '{}' for tenant '{}'",
                    templateId, routeId, tenantId);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to instantiate template '" + templateId
                            + "' as route '" + routeId + "'", e);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Template CRUD (admin operations)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Save a custom template. Built-in templates cannot be modified via this API.
     */
    public TemplateDescriptor saveCustomTemplate(TemplateDescriptor template) {
        if (builtInIds.contains(template.templateId())) {
            throw new IllegalArgumentException(
                    "Cannot modify built-in template: " + template.templateId());
        }
        if (templateConfigProvider == null) {
            throw new IllegalStateException(
                    "TemplateConfigProvider not configured — cannot persist custom templates");
        }
        return templateConfigProvider.save(template);
    }

    /**
     * Delete a custom template.
     */
    public void deleteCustomTemplate(String templateId) {
        if (builtInIds.contains(templateId)) {
            throw new IllegalArgumentException(
                    "Cannot delete built-in template: " + templateId);
        }
        if (templateConfigProvider != null) {
            templateConfigProvider.deleteByTemplateId(templateId);
        }
    }

    /** Returns the YAML loader for direct use if needed. */
    public YamlTemplateLoader yamlLoader() {
        return yamlLoader;
    }

    // ═══════════════════════════════════════════════════════════════
    //  Internal
    // ═══════════════════════════════════════════════════════════════

    /**
     * Load a custom template's YAML into the CamelContext so it becomes
     * available for TemplatedRouteBuilder.
     */
    private void loadCustomTemplateYaml(CamelContext context,
                                        String templateId,
                                        String yamlContent) {
        if (!loadedCustomTemplates.add(templateId)) {
            return; // Already loaded
        }
        try {
            Resource resource = ResourceHelper.fromBytes(
                    templateId + ".yaml", yamlContent.getBytes());
            RoutesLoader loader = PluginHelper.getRoutesLoader(context);
            loader.loadRoutes(resource);
            log.info("Loaded custom template YAML into CamelContext: {}", templateId);
        } catch (Exception e) {
            loadedCustomTemplates.remove(templateId); // Rollback
            throw new IllegalStateException(
                    "Failed to load custom template YAML: " + templateId, e);
        }
    }
}
