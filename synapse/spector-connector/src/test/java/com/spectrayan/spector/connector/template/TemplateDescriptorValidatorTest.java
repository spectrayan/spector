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

import com.spectrayan.spector.connector.model.RouteConfig;
import com.spectrayan.spector.connector.model.TemplateDescriptor;
import com.spectrayan.spector.connector.model.TemplateDescriptor.ParameterDefinition;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link TemplateDescriptorValidator}.
 */
class TemplateDescriptorValidatorTest {

    // ─────────────── Required Parameters ───────────────

    @Test
    @DisplayName("Valid config with all required params passes")
    void validConfigPasses() {
        var descriptor = TemplateDescriptor.builder()
                .templateId("test")
                .displayName("Test")
                .parameters(List.of(
                        ParameterDefinition.builder().name("host").type("string").required(true).build(),
                        ParameterDefinition.builder().name("port").type("number").required(false).build()
                ))
                .build();

        var config = RouteConfig.builder("r1", "Route", "test")
                .properties(Map.of("host", "localhost"))
                .build();

        var errors = TemplateDescriptorValidator.validate(config, descriptor);
        assertThat(errors).isEmpty();
    }

    @Test
    @DisplayName("Missing required parameter fails")
    void missingRequiredFails() {
        var descriptor = TemplateDescriptor.builder()
                .templateId("test")
                .displayName("Test")
                .parameters(List.of(
                        ParameterDefinition.builder().name("host").displayName("Host").type("string").required(true).build()
                ))
                .build();

        var config = RouteConfig.builder("r1", "Route", "test").build();

        var errors = TemplateDescriptorValidator.validate(config, descriptor);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0)).contains("Host").contains("missing");
    }

    @Test
    @DisplayName("Blank required parameter fails")
    void blankRequiredFails() {
        var descriptor = TemplateDescriptor.builder()
                .templateId("test")
                .displayName("Test")
                .parameters(List.of(
                        ParameterDefinition.builder().name("host").displayName("Host").type("string").required(true).build()
                ))
                .build();

        var config = RouteConfig.builder("r1", "Route", "test")
                .properties(Map.of("host", "   "))
                .build();

        var errors = TemplateDescriptorValidator.validate(config, descriptor);
        assertThat(errors).hasSize(1);
    }

    @Test
    @DisplayName("Optional parameter can be missing")
    void optionalCanBeMissing() {
        var descriptor = TemplateDescriptor.builder()
                .templateId("test")
                .displayName("Test")
                .parameters(List.of(
                        ParameterDefinition.builder().name("timeout").type("number").required(false).build()
                ))
                .build();

        var config = RouteConfig.builder("r1", "Route", "test").build();

        assertThat(TemplateDescriptorValidator.validate(config, descriptor)).isEmpty();
    }

    // ─────────────── URL Validation ───────────────

    @Test
    @DisplayName("Valid HTTPS URL passes")
    void validHttpsUrlPasses() {
        var descriptor = TemplateDescriptor.builder()
                .templateId("test")
                .displayName("Test")
                .parameters(List.of(
                        ParameterDefinition.builder().name("url").displayName("URL").type("url").required(true).build()
                ))
                .build();

        var config = RouteConfig.builder("r1", "Route", "test")
                .properties(Map.of("url", "https://api.example.com/data"))
                .build();

        assertThat(TemplateDescriptorValidator.validate(config, descriptor)).isEmpty();
    }

    @Test
    @DisplayName("FTP URL fails URL validation")
    void ftpUrlFails() {
        var descriptor = TemplateDescriptor.builder()
                .templateId("test")
                .displayName("Test")
                .parameters(List.of(
                        ParameterDefinition.builder().name("url").displayName("API URL").type("url").required(true).build()
                ))
                .build();

        var config = RouteConfig.builder("r1", "Route", "test")
                .properties(Map.of("url", "ftp://files.example.com"))
                .build();

        var errors = TemplateDescriptorValidator.validate(config, descriptor);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0)).contains("http");
    }

    @Test
    @DisplayName("Gibberish URL fails")
    void gibberishUrlFails() {
        var descriptor = TemplateDescriptor.builder()
                .templateId("test")
                .displayName("Test")
                .parameters(List.of(
                        ParameterDefinition.builder().name("url").displayName("URL").type("url").required(true).build()
                ))
                .build();

        var config = RouteConfig.builder("r1", "Route", "test")
                .properties(Map.of("url", "not a url at all %%%"))
                .build();

        var errors = TemplateDescriptorValidator.validate(config, descriptor);
        assertThat(errors).isNotEmpty();
    }

    // ─────────────── Number Validation ───────────────

    @Test
    @DisplayName("Valid number passes")
    void validNumberPasses() {
        var descriptor = TemplateDescriptor.builder()
                .templateId("test")
                .displayName("Test")
                .parameters(List.of(
                        ParameterDefinition.builder().name("port").displayName("Port").type("number").required(true).build()
                ))
                .build();

        var config = RouteConfig.builder("r1", "Route", "test")
                .properties(Map.of("port", "8080"))
                .build();

        assertThat(TemplateDescriptorValidator.validate(config, descriptor)).isEmpty();
    }

    @Test
    @DisplayName("Non-numeric value fails number validation")
    void nonNumericFails() {
        var descriptor = TemplateDescriptor.builder()
                .templateId("test")
                .displayName("Test")
                .parameters(List.of(
                        ParameterDefinition.builder().name("port").displayName("Port").type("number").required(true).build()
                ))
                .build();

        var config = RouteConfig.builder("r1", "Route", "test")
                .properties(Map.of("port", "abc"))
                .build();

        var errors = TemplateDescriptorValidator.validate(config, descriptor);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0)).contains("number");
    }

    // ─────────────── Credential Validation ───────────────

    @Test
    @DisplayName("Missing credential ref fails when required")
    void missingCredentialFails() {
        var descriptor = TemplateDescriptor.builder()
                .templateId("test")
                .displayName("Test")
                .requiresCredential(true)
                .parameters(List.of())
                .build();

        var config = RouteConfig.builder("r1", "Route", "test").build();

        var errors = TemplateDescriptorValidator.validate(config, descriptor);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0)).contains("credential");
    }

    @Test
    @DisplayName("Present credential ref passes when required")
    void presentCredentialPasses() {
        var descriptor = TemplateDescriptor.builder()
                .templateId("test")
                .displayName("Test")
                .requiresCredential(true)
                .parameters(List.of())
                .build();

        var config = RouteConfig.builder("r1", "Route", "test")
                .credentialRef("env:MY_SECRET")
                .build();

        assertThat(TemplateDescriptorValidator.validate(config, descriptor)).isEmpty();
    }

    // ─────────────── Multiple Errors ───────────────

    @Test
    @DisplayName("Multiple validation errors reported together")
    void multipleErrors() {
        var descriptor = TemplateDescriptor.builder()
                .templateId("test")
                .displayName("Test")
                .requiresCredential(true)
                .parameters(List.of(
                        ParameterDefinition.builder().name("host").displayName("Host").type("string").required(true).build(),
                        ParameterDefinition.builder().name("url").displayName("URL").type("url").required(true).build()
                ))
                .build();

        var config = RouteConfig.builder("r1", "Route", "test").build(); // missing everything

        var errors = TemplateDescriptorValidator.validate(config, descriptor);
        assertThat(errors).hasSize(3); // host missing + url missing + credential missing
    }
}
