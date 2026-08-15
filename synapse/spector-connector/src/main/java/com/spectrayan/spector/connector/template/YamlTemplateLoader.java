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
import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorConnectorException;
import com.spectrayan.spector.connector.model.TemplateDescriptor;
import com.spectrayan.spector.connector.model.TemplateDescriptor.ParameterDefinition;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Loads connector descriptors and Camel route templates from modular YAML files.
 *
 * <p>Resolution order (highest priority wins):
 * <ol>
 *   <li><b>External filesystem</b>: {@code ${templatesDir}/} — user/admin overrides</li>
 *   <li><b>Classpath</b>: {@code templates/built-in-templates.yaml} and {@code templates/routes/*.yaml} — shipped with the jar</li>
 * </ol>
 */
public class YamlTemplateLoader {

    private static final Logger log = LoggerFactory.getLogger(YamlTemplateLoader.class);

    private static final String CLASSPATH_TEMPLATES = "templates/built-in-templates.yaml";
    private static final String CLASSPATH_ROUTES_DIR = "templates/routes/";

    private static final List<String> BUILT_IN_ROUTE_IDS = List.of(
            "file-watch", "s3-poll", "rest-api-poll", "db-query", "mongodb-poll",
            "kafka-consumer", "notion-pages", "github-ingest", "rss", "web-scraper",
            "confluence", "jira", "google-drive", "sharepoint", "salesforce",
            "slack-notify", "slack-ingest", "email-notify", "webhook-receiver", "direct"
    );

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
        long startTime = System.currentTimeMillis();
        Map<String, TemplateDescriptor> templates = new LinkedHashMap<>();

        // 1. Load from classpath (defaults)
        loadFromClasspath().forEach(t -> templates.put(t.templateId(), t));

        // 2. Load from filesystem (overrides)
        int externalCount = 0;
        if (externalDir != null && Files.isDirectory(externalDir)) {
            List<TemplateDescriptor> externalTemplates = loadFromDirectory(externalDir);
            for (TemplateDescriptor t : externalTemplates) {
                if (templates.containsKey(t.templateId())) {
                    log.info("[ConnectorLoader] External template '{}' overrides classpath default", t.templateId());
                }
                templates.put(t.templateId(), t);
                externalCount++;
            }
        }

        log.info("[ConnectorLoader] Loaded {} template descriptors ({} classpath, {} external) in {}ms",
                templates.size(),
                templates.size() - externalCount,
                externalCount,
                System.currentTimeMillis() - startTime);

        return List.copyOf(templates.values());
    }

    /**
     * Load template descriptors from the classpath resource.
     */
    @SuppressWarnings("unchecked")
    public List<TemplateDescriptor> loadFromClasspath() {
        try (InputStream is = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream(CLASSPATH_TEMPLATES)) {
            if (is == null) {
                log.warn("[ConnectorLoader] Classpath template manifest not found: {}", CLASSPATH_TEMPLATES);
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
                            log.warn("[ConnectorLoader] Template file not found on classpath: {}", classpathPath);
                            continue;
                        }
                        List<TemplateDescriptor> loaded = parseTemplateFile(fileIs);
                        loaded.forEach(t -> log.debug("[ConnectorLoader] Loaded template '{}' from {}",
                                t.templateId(), classpathPath));
                        result.addAll(loaded);
                    } catch (Exception e) {
                        log.error("[ConnectorLoader] Failed to parse template file {}: {}", classpathPath, e.getMessage(), e);
                        throw new SpectorConnectorException(ErrorCode.CONNECTOR_TEMPLATE_INVALID, e, classpathPath, e.getMessage());
                    }
                }
                log.info("[ConnectorLoader] Loaded {} templates from manifest ({} files)", result.size(), filesList.size());
                return result;
            }

            // ── Legacy mode: templates: [...] ──
            Object templatesObj = root.get("templates");
            if (templatesObj instanceof List<?>) {
                try (InputStream is2 = Thread.currentThread()
                        .getContextClassLoader()
                        .getResourceAsStream(CLASSPATH_TEMPLATES)) {
                    if (is2 != null) {
                        return parseTemplateFile(is2);
                    }
                }
            }

            log.warn("[ConnectorLoader] Classpath template file has neither 'files' nor 'templates' key");
            return List.of();
        } catch (IOException e) {
            log.error("[ConnectorLoader] Failed to load classpath templates: {}", e.getMessage(), e);
            throw new SpectorConnectorException(ErrorCode.CONNECTOR_INIT_FAILED, e, e.getMessage());
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
                    loaded.forEach(t -> log.debug("[ConnectorLoader] Loaded external template '{}' from {}", t.templateId(), file));
                    result.addAll(loaded);
                } catch (Exception e) {
                    log.error("[ConnectorLoader] Failed to parse external template file {}: {}", file, e.getMessage(), e);
                    throw new SpectorConnectorException(ErrorCode.CONNECTOR_TEMPLATE_INVALID, e, file.toString(), e.getMessage());
                }
            }
        } catch (IOException e) {
            log.error("[ConnectorLoader] Failed to scan templates directory {}: {}", dir, e.getMessage(), e);
        }

        return result;
    }

    /**
     * Load modular Camel YAML DSL route templates mapped by route ID.
     *
     * @param externalDir external templates directory (nullable)
     * @return map of templateId to route template YAML content
     */
    public Map<String, String> loadRouteTemplateMap(Path externalDir) {
        long startTime = System.currentTimeMillis();
        Map<String, String> routes = new LinkedHashMap<>();

        // 1. Load built-in route templates from classpath
        for (String id : BUILT_IN_ROUTE_IDS) {
            String routePath = CLASSPATH_ROUTES_DIR + id + ".yaml";
            try (InputStream is = Thread.currentThread()
                    .getContextClassLoader()
                    .getResourceAsStream(routePath)) {
                if (is != null) {
                    String yaml = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                    routes.put(id, yaml);
                    log.debug("[ConnectorLoader] Loaded classpath route template '{}' ({} bytes)", id, yaml.length());
                }
            } catch (IOException e) {
                log.error("[ConnectorLoader] Failed to read classpath route template {}: {}", routePath, e.getMessage());
                throw new SpectorConnectorException(ErrorCode.CONNECTOR_TEMPLATE_INVALID, e, id, e.getMessage());
            }
        }

        // 2. Load external route template overrides / additions
        int externalRoutesCount = 0;
        if (externalDir != null) {
            Path externalRoutesDir = externalDir.resolve("routes");
            Path targetDir = Files.isDirectory(externalRoutesDir) ? externalRoutesDir : externalDir;
            if (Files.isDirectory(targetDir)) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(targetDir, "*.{yaml,yml}")) {
                    for (Path file : stream) {
                        String fileName = file.getFileName().toString();
                        String templateId = fileName.substring(0, fileName.lastIndexOf('.'));
                        try {
                            String yaml = Files.readString(file, StandardCharsets.UTF_8);
                            if (yaml.contains("routeTemplate")) {
                                routes.put(templateId, yaml);
                                externalRoutesCount++;
                                log.info("[ConnectorLoader] Loaded external route template '{}' from {}", templateId, file);
                            }
                        } catch (IOException e) {
                            log.error("[ConnectorLoader] Failed to read external route file {}: {}", file, e.getMessage());
                        }
                    }
                } catch (IOException e) {
                    log.error("[ConnectorLoader] Error scanning external routes dir {}: {}", targetDir, e.getMessage());
                }
            }
        }

        log.info("[ConnectorLoader] Discovered {} modular route templates ({} classpath, {} external) in {}ms",
                routes.size(),
                routes.size() - externalRoutesCount,
                externalRoutesCount,
                System.currentTimeMillis() - startTime);

        return Collections.unmodifiableMap(routes);
    }

    /**
     * Load combined Camel YAML DSL route template content from classpath or filesystem.
     *
     * @param externalDir external templates directory (nullable)
     * @return combined route template YAML content, or empty if none found
     */
    public Optional<String> loadRouteTemplateYaml(Path externalDir) {
        Map<String, String> routeMap = loadRouteTemplateMap(externalDir);
        if (routeMap.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(String.join("\n\n", routeMap.values()));
    }

    // ═══════════════════════════════════════════════════════════════
    //  Internal Parsing
    // ═══════════════════════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    private List<TemplateDescriptor> parseTemplateFile(InputStream is) throws IOException {
        Map<String, Object> root = yamlMapper.readValue(is, new TypeReference<>() {});
        Object templatesObj = root.get("templates");
        if (!(templatesObj instanceof List<?> templatesList)) {
            log.warn("[ConnectorLoader] YAML file has no 'templates' array");
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
