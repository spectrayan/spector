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

import com.spectrayan.spector.connector.model.ConnectorType;
import com.spectrayan.spector.connector.model.TemplateDescriptor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link TemplateRegistry} and {@link YamlTemplateLoader}.
 *
 * <p>Verifies that template descriptors are loaded from YAML files
 * (classpath {@code templates/built-in-templates.yaml}), and that
 * filesystem overrides work correctly.</p>
 */
class TemplateRegistryTest {

    private TemplateRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new TemplateRegistry(null); // classpath only
    }

    // ─────────────── YAML-Loaded Templates ───────────────

    @Test
    @DisplayName("All 20 built-in templates are loaded from YAML")
    void allBuiltInTemplatesLoaded() {
        var templates = registry.listTemplates();
        assertThat(templates).hasSize(20);
    }

    @Test
    @DisplayName("Each YAML template has required fields")
    void yamlTemplatesHaveRequiredFields() {
        for (var template : registry.listTemplates()) {
            assertThat(template.templateId()).isNotBlank();
            assertThat(template.displayName()).isNotBlank();
            assertThat(template.connectorType()).isNotBlank();
        }
    }

    @Test
    @DisplayName("Find template by ID from YAML")
    void findTemplateById() {
        Optional<TemplateDescriptor> found = registry.findTemplate("file-watch");
        assertThat(found).isPresent();
        assertThat(found.get().displayName()).isEqualTo("Local File Watch");
    }

    @Test
    @DisplayName("Find template by nonexistent ID returns empty")
    void findNonexistentTemplate() {
        assertThat(registry.findTemplate("does-not-exist")).isEmpty();
    }

    @Test
    @DisplayName("Find default template for connector type")
    void findDefaultTemplateForType() {
        Optional<TemplateDescriptor> found = registry.findDefaultTemplateForType(ConnectorType.S3);
        assertThat(found).isPresent();
        assertThat(found.get().templateId()).isEqualTo("s3-poll");
    }

    @Test
    @DisplayName("Find default for unknown type returns empty")
    void findDefaultForUnknownType() {
        assertThat(registry.findDefaultTemplateForType("NONEXISTENT")).isEmpty();
    }

    // ─────────────── Template Descriptors from YAML ───────────────

    @Test
    @DisplayName("File-watch template has correct parameters from YAML")
    void fileWatchParameters() {
        var tpl = registry.findTemplate("file-watch").orElseThrow();
        assertThat(tpl.parameters()).hasSizeGreaterThanOrEqualTo(3);
        assertThat(tpl.parameters().stream().map(p -> p.name()))
                .contains("path", "pattern", "recursive");
        assertThat(tpl.requiresCredential()).isFalse();
    }

    @Test
    @DisplayName("S3 template requires credentials per YAML")
    void s3RequiresCredentials() {
        var tpl = registry.findTemplate("s3-poll").orElseThrow();
        assertThat(tpl.requiresCredential()).isTrue();
        assertThat(tpl.parameters().stream().map(p -> p.name()))
                .contains("bucketName", "region");
    }

    @Test
    @DisplayName("REST API template has URL parameter from YAML")
    void restApiHasUrl() {
        var tpl = registry.findTemplate("rest-api-poll").orElseThrow();
        assertThat(tpl.connectorType()).isEqualTo(ConnectorType.REST_API);
        assertThat(tpl.parameters().stream().filter(p -> p.name().equals("url")).findFirst())
                .isPresent()
                .get().satisfies(p -> {
                    assertThat(p.required()).isTrue();
                    assertThat(p.type()).isEqualTo("url");
                });
    }

    @Test
    @DisplayName("Kafka template has required brokers and topic from YAML")
    void kafkaTemplateParameters() {
        var tpl = registry.findTemplate("kafka-consumer").orElseThrow();
        assertThat(tpl.parameters().stream().filter(p -> p.required()).map(p -> p.name()))
                .contains("topic", "brokers");
    }

    // ─────────────── Filesystem Override ───────────────

    @Test
    @DisplayName("Filesystem template overrides classpath with same templateId")
    void filesystemOverridesClasspath(@TempDir Path tempDir) throws Exception {
        String overrideYaml = """
                templates:
                  - templateId: file-watch
                    displayName: "Custom File Watch Override"
                    description: "Overridden from filesystem"
                    icon: folder-custom
                    category: Data
                    connectorType: FILE_WATCH
                    parameters: []
                """;
        Files.writeString(tempDir.resolve("overrides.yaml"), overrideYaml);

        var overriddenRegistry = new TemplateRegistry(tempDir);
        var tpl = overriddenRegistry.findTemplate("file-watch").orElseThrow();
        assertThat(tpl.displayName()).isEqualTo("Custom File Watch Override");
    }

    @Test
    @DisplayName("Filesystem adds new custom templates alongside built-ins")
    void filesystemAddsCustomTemplates(@TempDir Path tempDir) throws Exception {
        String customYaml = """
                templates:
                  - templateId: my-custom-sftp
                    displayName: "SFTP Connector"
                    description: "Custom SFTP data source"
                    icon: sftp
                    category: Data
                    connectorType: SFTP
                    parameters:
                      - name: host
                        displayName: "SFTP Host"
                        type: string
                        required: true
                """;
        Files.writeString(tempDir.resolve("custom-sftp.yaml"), customYaml);

        var extRegistry = new TemplateRegistry(tempDir);
        // Should have 20 built-in + 1 custom = 21
        assertThat(extRegistry.listTemplates()).hasSize(21);
        var sftp = extRegistry.findTemplate("my-custom-sftp").orElseThrow();
        assertThat(sftp.displayName()).isEqualTo("SFTP Connector");
    }

    // ─────────────── YAML Loader Direct ───────────────

    @Test
    @DisplayName("YamlTemplateLoader reads classpath templates")
    void yamlLoaderReadsClasspath() {
        var loader = new YamlTemplateLoader();
        var templates = loader.loadFromClasspath();
        assertThat(templates).hasSize(20);
    }

    @Test
    @DisplayName("YamlTemplateLoader reads route template YAML")
    void yamlLoaderReadsRouteTemplates() {
        var loader = new YamlTemplateLoader();
        var yaml = loader.loadRouteTemplateYaml(null);
        assertThat(yaml).isPresent();
        assertThat(yaml.get()).contains("routeTemplate");
        assertThat(yaml.get()).contains("file-watch");
        assertThat(yaml.get()).contains("s3-poll");
    }

    // ─────────────── Custom Template CRUD Guards ───────────────

    @Test
    @DisplayName("Cannot modify built-in template")
    void cannotModifyBuiltIn() {
        var modified = TemplateDescriptor.builder()
                .templateId("file-watch")
                .displayName("Modified")
                .build();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> registry.saveCustomTemplate(modified))
                .withMessageContaining("Cannot modify built-in");
    }

    @Test
    @DisplayName("Cannot delete built-in template")
    void cannotDeleteBuiltIn() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> registry.deleteCustomTemplate("file-watch"))
                .withMessageContaining("Cannot delete built-in");
    }

    @Test
    @DisplayName("Cannot save custom template without TemplateConfigProvider")
    void cannotSaveWithoutProvider() {
        var custom = TemplateDescriptor.builder()
                .templateId("my-custom")
                .displayName("Custom")
                .build();

        assertThatIllegalStateException()
                .isThrownBy(() -> registry.saveCustomTemplate(custom))
                .withMessageContaining("TemplateConfigProvider not configured");
    }
}
