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
package com.spectrayan.spector.config;

import com.spectrayan.spector.config.properties.MultimodalProperties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link MultimodalProperties}.
 */
@DisplayName("MultimodalProperties")
class MultimodalPropertiesTest {

    @Nested
    @DisplayName("Default Configuration")
    class DefaultTests {

        @Test
        @DisplayName("Default config has correct values")
        void defaultValues() {
            var config = MultimodalProperties.DEFAULT;
            assertFalse(config.enabled());
            assertEquals("moondream", config.visionModel());
            assertEquals("http://localhost:11434", config.visionBaseUrl());
            assertEquals(120, config.visionTimeout());
            assertEquals("gemma4", config.audioModel());
            assertEquals(180, config.audioTimeout());
            assertEquals("local", config.assetStoreType());
            assertEquals(Path.of(".spector", "assets"), config.assetBasePath());
        }

        @Test
        @DisplayName("Default is not enabled")
        void defaultNotEnabled() {
            assertFalse(MultimodalProperties.DEFAULT.enabled());
            assertFalse(MultimodalProperties.DEFAULT.isVisionConfigured());
            assertFalse(MultimodalProperties.DEFAULT.isAudioConfigured());
        }
    }

    @Nested
    @DisplayName("Configuration Queries")
    class QueryTests {

        @Test
        @DisplayName("isVisionConfigured when enabled with model")
        void visionConfigured() {
            var config = new MultimodalProperties(true, "llava", "http://localhost:11434",
                    120, "gemma4", 180, "local", Path.of("/tmp"));
            assertTrue(config.isVisionConfigured());
        }

        @Test
        @DisplayName("isVisionConfigured false when disabled")
        void visionNotConfiguredDisabled() {
            var config = new MultimodalProperties(false, "llava", "http://localhost:11434",
                    120, "gemma4", 180, "local", Path.of("/tmp"));
            assertFalse(config.isVisionConfigured());
        }

        @Test
        @DisplayName("isVisionConfigured false with blank model")
        void visionNotConfiguredBlankModel() {
            var config = new MultimodalProperties(true, "", "http://localhost:11434",
                    120, "gemma4", 180, "local", Path.of("/tmp"));
            assertFalse(config.isVisionConfigured());
        }

        @Test
        @DisplayName("isAudioConfigured when enabled with model")
        void audioConfigured() {
            var config = new MultimodalProperties(true, "llava", "http://localhost:11434",
                    120, "gemma4", 180, "local", Path.of("/tmp"));
            assertTrue(config.isAudioConfigured());
        }

        @Test
        @DisplayName("isAudioConfigured false with null model")
        void audioNotConfiguredNull() {
            var config = new MultimodalProperties(true, "llava", "http://localhost:11434",
                    120, null, 180, "local", Path.of("/tmp"));
            assertFalse(config.isAudioConfigured());
        }

        @Test
        @DisplayName("isLocalAssetStore for local type")
        void localAssetStore() {
            assertTrue(MultimodalProperties.DEFAULT.isLocalAssetStore());
        }

        @Test
        @DisplayName("isLocalAssetStore false for s3")
        void s3AssetStore() {
            var config = new MultimodalProperties(true, "llava", "http://localhost:11434",
                    120, "gemma4", 180, "s3", Path.of("/tmp"));
            assertFalse(config.isLocalAssetStore());
        }
    }

    @Nested
    @DisplayName("From Properties")
    class PropertiesTests {

        @Test
        @DisplayName("Loads from classpath defaults")
        void loadsFromDefaults() {
            SpectorProperties props = SpectorProperties.loadClasspathOnly();
            MultimodalProperties config = MultimodalProperties.from(props);

            // Should use defaults when not specified in spector-defaults.yml
            assertNotNull(config);
            assertEquals("moondream", config.visionModel());
        }

        @Test
        @DisplayName("Overrides from properties")
        void overridesFromProperties() {
            SpectorProperties props = SpectorProperties.builder()
                    .override("spector.multimodal.enabled", "true")
                    .override("spector.multimodal.vision.model", "llava")
                    .override("spector.multimodal.audio.model", "qwen2-audio")
                    .override("spector.multimodal.asset-store.type", "s3")
                    .build();

            MultimodalProperties config = MultimodalProperties.from(props);
            assertTrue(config.enabled());
            assertEquals("llava", config.visionModel());
            assertEquals("qwen2-audio", config.audioModel());
            assertEquals("s3", config.assetStoreType());
        }
    }
}
