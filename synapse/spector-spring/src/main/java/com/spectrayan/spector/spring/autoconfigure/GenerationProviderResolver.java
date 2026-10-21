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
package com.spectrayan.spector.spring.autoconfigure;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorConfigException;
import com.spectrayan.spector.config.SpectorPropertyConstants;
import com.spectrayan.spector.config.properties.GenerationProperties;
import com.spectrayan.spector.provider.ProviderConfig;
import com.spectrayan.spector.provider.ProviderFactory;
import com.spectrayan.spector.provider.anthropic.AnthropicProviderFactory;
import com.spectrayan.spector.provider.azure.AzureOpenAiProviderFactory;
import com.spectrayan.spector.provider.bedrock.BedrockProviderFactory;
import com.spectrayan.spector.provider.generation.LlmProvider;
import com.spectrayan.spector.provider.google.GoogleProviderFactory;
import com.spectrayan.spector.provider.mistral.MistralProviderFactory;
import com.spectrayan.spector.provider.openai.OpenAiProviderFactory;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Builds the text generation ({@link LlmProvider}) for a non-Ollama {@code spector.provider.generation.type}.
 *
 * <p>The Ollama default is still created directly by {@link SpectorAutoConfiguration}. Every other type is
 * looked up among the cloud {@link ProviderFactory} implementations by {@link ProviderFactory#name()}, so
 * {@code type: openai} gets an OpenAI chat model through LangChain4j's OpenAI module.</p>
 *
 * <h3>OpenAI defaults</h3>
 * <ul>
 *   <li>API key: {@code spector.provider.generation.api-key} when set, otherwise the {@value #OPENAI_API_KEY_ENV}
 *       environment variable. The key is passed to the client and never logged or echoed in an error.</li>
 *   <li>Model: {@value #DEFAULT_OPENAI_MODEL} when the model is left at the Ollama default.</li>
 *   <li>Base URL: the OpenAI default when the base URL is left at the Ollama default
 *       ({@code http://localhost:11434}).</li>
 * </ul>
 */
public final class GenerationProviderResolver {

    /** Environment variable read for the OpenAI API key when none is configured. */
    public static final String OPENAI_API_KEY_ENV = "OPENAI_API_KEY";

    /** Model used for {@code type: openai} when the model is left at the Ollama default. */
    public static final String DEFAULT_OPENAI_MODEL = "gpt-4o-mini";

    private static final String OPENAI = "openai";

    private static final List<Supplier<ProviderFactory>> FACTORIES = List.of(
            OpenAiProviderFactory::new,
            AnthropicProviderFactory::new,
            GoogleProviderFactory::new,
            MistralProviderFactory::new,
            AzureOpenAiProviderFactory::new,
            BedrockProviderFactory::new);

    private GenerationProviderResolver() {
    }

    /**
     * A created provider and the name it should be registered under.
     *
     * @param name     registry name, the normalised provider type
     * @param model    model the provider was created for
     * @param provider the provider
     */
    public record Resolved(String name, String model, LlmProvider provider) {
    }

    /**
     * Creates the generation provider described by {@code gen}.
     *
     * @param gen generation properties; {@code type} must not be Ollama
     * @param env environment lookup, {@code System::getenv} in production
     * @return the created provider
     * @throws SpectorConfigException with {@link ErrorCode#GENERATION_PROVIDER_UNAVAILABLE} when the type is
     *                                unknown, cannot generate text, or has no API key
     */
    public static Resolved resolve(GenerationProperties gen, Function<String, String> env) {
        String type = gen.getType() == null ? "" : gen.getType().trim().toLowerCase(Locale.ROOT);
        if ("azure".equals(type)) {
            type = "azure-openai"; // the documented short name
        }
        String factoryName = type;
        ProviderFactory factory = FACTORIES.stream()
                .map(Supplier::get)
                .filter(f -> f.name().equalsIgnoreCase(factoryName))
                .findFirst()
                .orElseThrow(() -> unavailable(factoryName, "unknown generation provider type"));
        if (!factory.supportsGeneration()) {
            throw unavailable(factoryName, "provider does not support text generation");
        }

        String model = resolveModel(type, gen.getModel());
        String apiKey = resolveApiKey(type, gen.getApiKey(), env);
        if (OPENAI.equals(type) && apiKey.isBlank()) {
            throw unavailable(type, "no API key: set " + OPENAI_API_KEY_ENV
                    + " or spector.provider.generation.api-key");
        }
        String baseUrl = SpectorPropertyConstants.DEFAULT_PROVIDER_GENERATION_BASE_URL.equals(gen.getBaseUrl())
                ? ""
                : gen.getBaseUrl();
        Map<String, String> properties = gen.getProperties() != null ? gen.getProperties() : Map.of();

        ProviderConfig config = new ProviderConfig(type, type, model, apiKey, baseUrl, 0, properties);
        LlmProvider provider = factory.createGenerationProvider(config)
                .orElseThrow(() -> unavailable(factoryName, "factory returned no generation provider"));
        return new Resolved(type, model, provider);
    }

    private static String resolveModel(String type, String configured) {
        boolean unset = configured == null || configured.isBlank()
                || SpectorPropertyConstants.DEFAULT_PROVIDER_GENERATION_MODEL.equals(configured);
        if (OPENAI.equals(type) && unset) {
            return DEFAULT_OPENAI_MODEL;
        }
        return configured == null ? "" : configured;
    }

    private static String resolveApiKey(String type, String configured, Function<String, String> env) {
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        if (OPENAI.equals(type)) {
            String fromEnv = env.apply(OPENAI_API_KEY_ENV);
            return fromEnv == null ? "" : fromEnv.trim();
        }
        return "";
    }

    private static SpectorConfigException unavailable(String type, String reason) {
        return new SpectorConfigException(ErrorCode.GENERATION_PROVIDER_UNAVAILABLE,
                "spector.provider.generation.type=" + type, reason);
    }
}
