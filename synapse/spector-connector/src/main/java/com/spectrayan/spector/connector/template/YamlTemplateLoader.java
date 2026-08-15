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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.spectrayan.spector.connector.model.TemplateDescriptor;
import com.spectrayan.spector.connector.model.TemplateDescriptor.ParameterDefinition;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Loads template descriptors from YAML files.
 *
 * <p>Resolution order (highest priority wins):
 * <ol>
 *   <li><b>External filesystem</b>: {@code ${templatesDir}/} — user/admin overrides</li>
 *   <li><b>Classpath</b>: {@code templates/built-in-templates.yaml} — shipped with the jar</li>
 * </ol>
 *
 * <p>External templates with the same {@code templateId} as a classpath template
 * will override the classpath version. This allows admins to customize built-in
 * templates without touching the jar.</p>
 *
 * <p>YAML schema per file:</p>
 * <pre>{@code
 * templates:
 *   - templateId: file-watch
 *     displayName: "Local File Watch"
 *     ...
 * }</pre>
 */
public class YamlTemplateLoader {

    private static final Logger log = LoggerFactory.getLogger(YamlTemplateLoader.class);

    private static final String CLASSPATH_TEMPLATES = "templates/built-in-templates.yaml";

    private final ObjectMapper yamlMapper;

    public YamlTemplateLoader() {
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
    }

    /**
     * Load all template descriptors: classpath defaults + filesystem overrides.
     *
     * @param externalDir external templates directory (nullable — classpath only if null)
     * @return all loaded template descriptors (deduped by templateId, filesystem wins)
     */
    public List<TemplateDescriptor> loadAll(Path externalDir) {
        // LinkedHashMap preserves insertion order and dedupes by templateId
        Map<String, TemplateDescriptor> templates = new LinkedHashMap<>();

        // 1. Load from classpath (defaults)
        loadFromClasspath().forEach(t -> templates.put(t.templateId(), t));

        // 2. Load from filesystem (overrides)
        if (externalDir != null && Files.isDirectory(externalDir)) {
            loadFromDirectory(externalDir).forEach(t -> {
                if (templates.containsKey(t.templateId())) {
                    log.info("Filesystem template '{}' overrides classpath default", t.templateId());
                }
                templates.put(t.templateId(), t);
            });
        }

        log.info("Loaded {} template descriptors ({} classpath, {} external)",
                templates.size(),
                loadFromClasspath().size(),
                externalDir != null && Files.isDirectory(externalDir)
                        ? loadFromDirectory(externalDir).size() : 0);

        return List.copyOf(templates.values());
    }

    /**
     * Load template descriptors from the classpath resource.
     *
     * <p>Supports two modes:
     * <ol>
     *   <li><b>Manifest mode</b> — the YAML contains a {@code files:} list pointing to
     *       individual connector YAML files (e.g., {@code connectors/file-watch.yaml}).
     *       Each file is loaded from the classpath under {@code templates/}.</li>
     *   <li><b>Legacy mode</b> — the YAML contains a {@code templates:} array directly
     *       (backwards-compatible single-file format).</li>
     * </ol>
     */
    @SuppressWarnings("unchecked")
    public List<TemplateDescriptor> loadFromClasspath() {
        try (InputStream is = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream(CLASSPATH_TEMPLATES)) {
            if (is == null) {
                log.warn("Classpath template file not found: {}", CLASSPATH_TEMPLATES);
                return List.of();
            }

            Map<String, Object> root = yamlMapper.readValue(is, new TypeReference<>() {});

            // ── Manifest mode: files: [...] ──
            Object filesObj = root.get("files");
            if (filesObj instanceof List<?> filesList) {
                List<TemplateDescriptor> result = new ArrayList<>();
                for (Object entry : filesList) {
                    String relativePath = entry.toString();
                    String classpathPath = "templates/" + relativePath;
                    try (InputStream fileIs = Thread.currentThread()
                            .getContextClassLoader()
                            .getResourceAsStream(classpathPath)) {
                        if (fileIs == null) {
                            log.warn("Template file not found on classpath: {}", classpathPath);
                            continue;
                        }
                        List<TemplateDescriptor> loaded = parseTemplateFile(fileIs);
                        loaded.forEach(t -> log.debug("Loaded template '{}' from classpath: {}",
                                t.templateId(), classpathPath));
                        result.addAll(loaded);
                    } catch (Exception e) {
                        log.error("Failed to parse template file {}: {}", classpathPath, e.getMessage(), e);
                    }
                }
                log.info("Loaded {} templates from manifest ({} files)", result.size(), filesList.size());
                return result;
            }

            // ── Legacy mode: templates: [...] (single-file format) ──
            Object templatesObj = root.get("templates");
            if (templatesObj instanceof List<?>) {
                // Re-parse the whole file via parseTemplateFile — need a fresh stream
                try (InputStream is2 = Thread.currentThread()
                        .getContextClassLoader()
                        .getResourceAsStream(CLASSPATH_TEMPLATES)) {
                    if (is2 != null) {
                        return parseTemplateFile(is2);
                    }
                }
            }

            log.warn("Classpath template file has neither 'files' nor 'templates' key");
            return List.of();
        } catch (IOException e) {
            log.error("Failed to load classpath templates: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Load template descriptors from all YAML files in a directory.
     */
    public List<TemplateDescriptor> loadFromDirectory(Path dir) {
        List<TemplateDescriptor> result = new ArrayList<>();
        if (!Files.isDirectory(dir)) {
            return result;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.{yaml,yml}")) {
            for (Path file : stream) {
                try (InputStream is = Files.newInputStream(file)) {
                    List<TemplateDescriptor> loaded = parseTemplateFile(is);
                    loaded.forEach(t -> log.debug("Loaded template '{}' from {}", t.templateId(), file));
                    result.addAll(loaded);
                } catch (Exception e) {
                    log.error("Failed to parse template file {}: {}", file, e.getMessage(), e);
                }
            }
        } catch (IOException e) {
            log.error("Failed to scan templates directory {}: {}", dir, e.getMessage(), e);
        }

        return result;
    }

    /**
     * Load Camel YAML DSL route template content from classpath or filesystem.
     *
     * @param externalDir external templates directory (nullable)
     * @return the route template YAML content, or empty if not found
     */
    public Optional<String> loadRouteTemplateYaml(Path externalDir) {
        // 1. Check filesystem first
        if (externalDir != null) {
            Path externalRouteFile = externalDir.resolve("route-templates.yaml");
            if (Files.isRegularFile(externalRouteFile)) {
                try {
                    String yaml = Files.readString(externalRouteFile);
                    log.info("Loaded route templates from filesystem: {}", externalRouteFile);
                    return Optional.of(yaml);
                } catch (IOException e) {
                    log.error("Failed to read route templates from {}: {}", externalRouteFile, e.getMessage());
                }
            }
        }

        // 2. Fallback to classpath
        try (InputStream is = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream("templates/route-templates.yaml")) {
            if (is != null) {
                String yaml = new String(is.readAllBytes());
                log.info("Loaded route templates from classpath");
                return Optional.of(yaml);
            }
        } catch (IOException e) {
            log.error("Failed to load route templates from classpath: {}", e.getMessage());
        }

        return Optional.empty();
    }

    // ═══════════════════════════════════════════════════════════════
    //  Internal Parsing
    // ═══════════════════════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    private List<TemplateDescriptor> parseTemplateFile(InputStream is) throws IOException {
        Map<String, Object> root = yamlMapper.readValue(is, new TypeReference<>() {});
        Object templatesObj = root.get("templates");
        if (!(templatesObj instanceof List<?> templatesList)) {
            log.warn("YAML file has no 'templates' array");
            return List.of();
        }

        List<TemplateDescriptor> result = new ArrayList<>();
        for (Object entry : templatesList) {
            if (entry instanceof Map<?, ?> map) {
                result.add(parseTemplateEntry((Map<String, Object>) map));
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private TemplateDescriptor parseTemplateEntry(Map<String, Object> map) {
        String templateId = getString(map, "templateId");
        String displayName = getString(map, "displayName");
        String description = getString(map, "description");
        String icon = getString(map, "icon");
        String category = getString(map, "category");
        String connectorType = getString(map, "connectorType");
        boolean requiresCredential = Boolean.TRUE.equals(map.get("requiresCredential"));
        String routeYaml = getString(map, "routeYaml");

        List<ParameterDefinition> params = new ArrayList<>();
        Object paramsObj = map.get("parameters");
        if (paramsObj instanceof List<?> paramsList) {
            for (Object paramEntry : paramsList) {
                if (paramEntry instanceof Map<?, ?> paramMap) {
                    params.add(parseParameterEntry((Map<String, Object>) paramMap));
                }
            }
        }

        return TemplateDescriptor.builder()
                .templateId(templateId)
                .displayName(displayName)
                .description(description)
                .icon(icon)
                .category(category)
                .connectorType(connectorType)
                .builtIn(true)
                .requiresCredential(requiresCredential)
                .routeYaml(routeYaml)
                .parameters(params)
                .build();
    }

    private ParameterDefinition parseParameterEntry(Map<String, Object> map) {
        return ParameterDefinition.builder()
                .name(getString(map, "name"))
                .displayName(getString(map, "displayName"))
                .type(getString(map, "type"))
                .required(Boolean.TRUE.equals(map.get("required")))
                .defaultValue(getString(map, "defaultValue"))
                .placeholder(getString(map, "placeholder"))
                .build();
    }

    private String getString(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? val.toString() : null;
    }
}
