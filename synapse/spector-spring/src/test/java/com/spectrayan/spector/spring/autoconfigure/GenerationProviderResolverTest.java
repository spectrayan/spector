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
import com.spectrayan.spector.config.properties.GenerationProperties;
import com.spectrayan.spector.provider.langchain4j.LangChain4jGenerationAdapter;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GenerationProviderResolverTest {

    private static final String KEY = "sk-test-not-a-real-key-42";

    private static GenerationProperties openAi() {
        var gen = new GenerationProperties();
        gen.setType("openai");
        return gen;
    }

    private static Function<String, String> env(Map<String, String> vars) {
        return vars::get;
    }

    @Test
    void openAiReadsTheKeyFromTheEnvironmentAndDefaultsToGpt4oMini() {
        var resolved = GenerationProviderResolver.resolve(openAi(), env(Map.of("OPENAI_API_KEY", KEY)));

        assertThat(resolved.name()).isEqualTo("openai");
        assertThat(resolved.model()).isEqualTo("gpt-4o-mini");
        assertThat(resolved.provider()).isInstanceOf(LangChain4jGenerationAdapter.class);
        assertThat(resolved.provider().modelName()).isEqualTo("gpt-4o-mini");
        assertThat(resolved.provider().isAvailable()).isTrue();
    }

    @Test
    void configuredModelAndKeyWin() {
        var gen = openAi();
        gen.setModel("gpt-4.1-mini");
        gen.setApiKey(KEY);

        var resolved = GenerationProviderResolver.resolve(gen, env(Map.of()));

        assertThat(resolved.model()).isEqualTo("gpt-4.1-mini");
    }

    @Test
    void typeIsCaseInsensitive() {
        var gen = openAi();
        gen.setType("OpenAi");
        assertThat(GenerationProviderResolver.resolve(gen, env(Map.of("OPENAI_API_KEY", KEY))).name())
                .isEqualTo("openai");
    }

    @Test
    void openAiWithoutAKeyFailsWithoutEchoingAnything() {
        assertThatThrownBy(() -> GenerationProviderResolver.resolve(openAi(), env(Map.of())))
                .isInstanceOfSatisfying(SpectorConfigException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.GENERATION_PROVIDER_UNAVAILABLE))
                .hasMessageContaining("OPENAI_API_KEY");
    }

    @Test
    void unknownTypeFails() {
        var gen = new GenerationProperties();
        gen.setType("not-a-provider");

        assertThatThrownBy(() -> GenerationProviderResolver.resolve(gen, env(Map.of())))
                .isInstanceOfSatisfying(SpectorConfigException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.GENERATION_PROVIDER_UNAVAILABLE))
                .hasMessageContaining("not-a-provider");
    }
}
