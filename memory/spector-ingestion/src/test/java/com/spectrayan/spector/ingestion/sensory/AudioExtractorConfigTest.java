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
package com.spectrayan.spector.ingestion.sensory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link AudioExtractorConfig}.
 */
@DisplayName("AudioExtractorConfig")
class AudioExtractorConfigTest {

    @Nested
    @DisplayName("Default Configuration")
    class DefaultTests {

        @Test
        @DisplayName("DEFAULT has correct values")
        void defaultValues() {
            var cfg = AudioExtractorConfig.DEFAULT;
            assertEquals("gemma4", cfg.model());
            assertEquals("http://localhost:11434", cfg.baseUrl());
            assertEquals(120, cfg.timeoutSeconds());
            assertEquals(0, cfg.maxSegmentSeconds());
            assertNull(cfg.language());
        }

        @Test
        @DisplayName("effectiveBaseUrl strips trailing slash")
        void effectiveBaseUrl() {
            var cfg = new AudioExtractorConfig("m", "http://localhost:11434/", 60, 0, null);
            assertEquals("http://localhost:11434", cfg.effectiveBaseUrl());
        }

        @Test
        @DisplayName("effectiveBaseUrl no-op without trailing slash")
        void effectiveBaseUrlNoTrail() {
            assertEquals("http://localhost:11434", AudioExtractorConfig.DEFAULT.effectiveBaseUrl());
        }
    }

    @Nested
    @DisplayName("Factory Methods")
    class FactoryTests {

        @Test
        @DisplayName("ofModel sets model, keeps defaults")
        void ofModel() {
            var cfg = AudioExtractorConfig.ofModel("whisper");
            assertEquals("whisper", cfg.model());
            assertEquals(AudioExtractorConfig.DEFAULT.baseUrl(), cfg.baseUrl());
            assertEquals(AudioExtractorConfig.DEFAULT.timeoutSeconds(), cfg.timeoutSeconds());
        }

        @Test
        @DisplayName("of(model, baseUrl) sets both")
        void ofModelAndUrl() {
            var cfg = AudioExtractorConfig.of("whisper", "http://remote:8080");
            assertEquals("whisper", cfg.model());
            assertEquals("http://remote:8080", cfg.baseUrl());
            assertEquals(AudioExtractorConfig.DEFAULT.timeoutSeconds(), cfg.timeoutSeconds());
        }

        @Test
        @DisplayName("of(model, baseUrl, timeout) sets all three")
        void ofModelUrlTimeout() {
            var cfg = AudioExtractorConfig.of("qwen2-audio", "http://gpu:11434", 300);
            assertEquals("qwen2-audio", cfg.model());
            assertEquals("http://gpu:11434", cfg.baseUrl());
            assertEquals(300, cfg.timeoutSeconds());
        }
    }

    @Nested
    @DisplayName("Compact Validation")
    class ValidationTests {

        @Test
        @DisplayName("Null model defaults to gemma4")
        void nullModelDefaults() {
            var cfg = new AudioExtractorConfig(null, "http://localhost:11434", 60, 0, null);
            assertEquals("gemma4", cfg.model());
        }

        @Test
        @DisplayName("Blank model defaults to gemma4")
        void blankModelDefaults() {
            var cfg = new AudioExtractorConfig("  ", "http://localhost:11434", 60, 0, null);
            assertEquals("gemma4", cfg.model());
        }

        @Test
        @DisplayName("Null baseUrl defaults")
        void nullBaseUrlDefaults() {
            var cfg = new AudioExtractorConfig("m", null, 60, 0, null);
            assertEquals("http://localhost:11434", cfg.baseUrl());
        }

        @Test
        @DisplayName("Zero timeout defaults to 120")
        void zeroTimeoutDefaults() {
            var cfg = new AudioExtractorConfig("m", "http://localhost:11434", 0, 0, null);
            assertEquals(120, cfg.timeoutSeconds());
        }

        @Test
        @DisplayName("Negative timeout defaults to 120")
        void negativeTimeoutDefaults() {
            var cfg = new AudioExtractorConfig("m", "http://localhost:11434", -5, 0, null);
            assertEquals(120, cfg.timeoutSeconds());
        }

        @Test
        @DisplayName("Negative maxSegment defaults to 0")
        void negativeMaxSegmentDefaults() {
            var cfg = new AudioExtractorConfig("m", "http://localhost:11434", 60, -10, null);
            assertEquals(0, cfg.maxSegmentSeconds());
        }

        @Test
        @DisplayName("Language hint preserved")
        void languageHint() {
            var cfg = new AudioExtractorConfig("m", "http://localhost:11434", 60, 30, "en-US");
            assertEquals("en-US", cfg.language());
        }

        @Test
        @DisplayName("Record equality works")
        void recordEquality() {
            var a = AudioExtractorConfig.DEFAULT;
            var b = new AudioExtractorConfig("gemma4", "http://localhost:11434", 120, 0, null);
            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }
    }
}
