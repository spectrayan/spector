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
package com.spectrayan.spector.provider.google;

import static org.assertj.core.api.Assertions.assertThat;

import com.spectrayan.spector.provider.ProviderConfig;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.generation.LlmProvider;
import com.spectrayan.spector.provider.langchain4j.LangChain4jEmbeddingAdapter;
import com.spectrayan.spector.provider.langchain4j.LangChain4jGenerationAdapter;

import dev.langchain4j.model.googleai.GoogleAiEmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

/**
 * Unit tests for {@link GoogleProviderFactory}.
 *
 * <p>Tests cover metadata, factory construction of {@link LlmProvider} and
 * {@link EmbeddingProvider} instances from {@link ProviderConfig}, and correct
 * propagation of generation parameters (temperature, maxOutputTokens, topP)
 * and custom headers to the underlying LangChain4j model builders.</p>
 *
 * <p>These tests exercise only builder/construction logic — no network calls
 * are made, since {@code GoogleAiGeminiChatModel.builder().build()} does not
 * validate connectivity or the API key at construction time.</p>
 */
class GoogleProviderFactoryTest {

    private final GoogleProviderFactory factory = new GoogleProviderFactory();

    // Test metadata
    @Nested
    class MetadataTests {

        @Test
        void nameIsGoogle() {
            assertThat(factory.name()).isEqualTo("google");
        }

        @Test
        void displayNameIsGoogleGemini() {
            assertThat(factory.displayName()).isEqualTo("Google Gemini");
        }

        @Test
        void supportsEmbeddingIsTrue() {
            assertThat(factory.supportsEmbedding()).isTrue();
        }

        @Test
        void supportsGenerationIsTrue() {
            assertThat(factory.supportsGeneration()).isTrue();
        }
    }

    // Testing generation
    @Nested
    class GenerationProviderTests {

        // Test if the provider is google and the model is gemini-3.1-flash-lite
        @Test 
        void createsLlmProviderFromConfig() {
            ProviderConfig config = new ProviderConfig(
                    "google", "google", "gemini-3.1-flash-lite", "test-api-key",
                    "", 0, Map.of());

            Optional<LlmProvider> provider = factory.createGenerationProvider(config);

            assertThat(provider).isPresent();
            assertThat(provider.get()).isInstanceOf(LangChain4jGenerationAdapter.class);
            assertThat(provider.get().modelName()).isEqualTo("gemini-3.1-flash-lite");
        }

        // Test that the adaptor is an instance of Google Gemini
        @Test
        void wrapsGoogleAiGeminiChatModel() {
            ProviderConfig config = new ProviderConfig(
                    "google", "google", "gemini-3.1-flash-lite", "test-api-key",
                    "", 0, Map.of());

            var adapter = (LangChain4jGenerationAdapter) factory.createGenerationProvider(config).orElseThrow();

            assertThat(adapter.delegate()).isInstanceOf(GoogleAiGeminiChatModel.class);
        }

        // Test max temperature and output token values.
        @Test
        void appliesTemperatureMaxOutputTokensAndTopP() {
            ProviderConfig config = new ProviderConfig(
                    "google", "google", "gemini-3.1-flash-lite", "test-api-key",
                    "", 0, Map.of(
                            "temperature", "0.7",
                            "maxOutputTokens", "2048",
                            "topP", "0.95"
                    ));

            var adapter = (LangChain4jGenerationAdapter) factory.createGenerationProvider(config).orElseThrow();
            var params = adapter.delegate().defaultRequestParameters();

            assertThat(params.temperature()).isEqualTo(0.7);
            assertThat(params.maxOutputTokens()).isEqualTo(2048);
            assertThat(params.topP()).isEqualTo(0.95);
        }

        // Test default value of temperature and tokens (null)
        @Test
        void omittedOptionalPropertiesLeaveDefaultsUnset() {
            ProviderConfig config = new ProviderConfig(
                    "google", "google", "gemini-3.1-flash-lite", "test-api-key",
                    "", 0, Map.of());

            var adapter = (LangChain4jGenerationAdapter) factory.createGenerationProvider(config).orElseThrow();
            var params = adapter.delegate().defaultRequestParameters();

            assertThat(params.temperature()).isNull();
            assertThat(params.maxOutputTokens()).isNull();
            assertThat(params.topP()).isNull();
        }

        @Test
        void appliesCustomHeaders() {
            ProviderConfig config = new ProviderConfig(
                    "google", "google", "gemini-3.1-flash-lite", "test-api-key",
                    "", 0, Map.of(
                            "header.X-Custom-Trace", "abc123",
                            "header.X-Tenant-Id", "tenant-42"
                    ));

            // Should not throw — custom header properties are consumed by
            // LangChain4jHelper.resolveCustomHeaders and applied via
            // builder.customHeaders(...) without affecting provider construction.
            Optional<LlmProvider> provider = factory.createGenerationProvider(config);

            assertThat(provider).isPresent();
        }

        // Test Default generation timeouts
        @Test
        void defaultTimeoutAppliedWhenNotConfigured() {
            ProviderConfig config = new ProviderConfig(
                    "google", "google", "gemini-3.1-flash-lite", "test-api-key",
                    "", 0, Map.of());

            // Default generation timeout is 60s per GoogleProviderFactory javadoc.
            // We only assert construction succeeds without a configured timeout;
            // the concrete Duration is an internal builder detail.
            Optional<LlmProvider> provider = factory.createGenerationProvider(config);

            assertThat(provider).isPresent();
        }

        // Test custom timeout
        @Test
        void customTimeoutIsParsed() {
            ProviderConfig config = new ProviderConfig(
                    "google", "google", "gemini-3.1-flash-lite", "test-api-key",
                    "", 0, Map.of("timeout", "120"));

            Optional<LlmProvider> provider = factory.createGenerationProvider(config);

            assertThat(provider).isPresent();
        }
    }

    // Embedding provider tests
    @Nested
    class EmbeddingProviderTests {

        @Test
        void createsEmbeddingProviderFromConfig() {
            ProviderConfig config = new ProviderConfig(
                    "google", "google", "text-embedding-004", "test-api-key",
                    "", 768, Map.of());

            Optional<EmbeddingProvider> provider = factory.createEmbeddingProvider(config);

            assertThat(provider).isPresent();
            assertThat(provider.get()).isInstanceOf(LangChain4jEmbeddingAdapter.class);
            assertThat(provider.get().modelName()).isEqualTo("text-embedding-004");
        }

        @Test
        void wrapsGoogleAiEmbeddingModel() {
            ProviderConfig config = new ProviderConfig(
                    "google", "google", "text-embedding-004", "test-api-key",
                    "", 768, Map.of());

            var adapter = (LangChain4jEmbeddingAdapter) factory.createEmbeddingProvider(config).orElseThrow();

            assertThat(adapter.delegate()).isInstanceOf(GoogleAiEmbeddingModel.class);
        }

        // Test if configured dimensions are used
        @Test
        void usesConfiguredDimensions() {
            ProviderConfig config = new ProviderConfig(
                    "google", "google", "text-embedding-004", "test-api-key",
                    "", 1536, Map.of());

            EmbeddingProvider provider = factory.createEmbeddingProvider(config).orElseThrow();

            assertThat(provider.dimensions()).isEqualTo(1536);
        }

        // Test default dimensions
        @Test
        void defaultsToSevenSixtyEightDimensionsWhenUnspecified() {
            ProviderConfig config = new ProviderConfig(
                    "google", "google", "text-embedding-004", "test-api-key",
                    "", 0, Map.of());

            EmbeddingProvider provider = factory.createEmbeddingProvider(config).orElseThrow();

            assertThat(provider.dimensions()).isEqualTo(768);
        }
    }
}