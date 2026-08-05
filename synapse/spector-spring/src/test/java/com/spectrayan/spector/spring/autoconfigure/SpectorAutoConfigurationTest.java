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

import com.spectrayan.spector.client.SpectorClient;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.metrics.MeteredSpectorMemory;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import dev.langchain4j.model.embedding.EmbeddingModel;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration and unit tests for {@link SpectorAutoConfiguration} using {@link ApplicationContextRunner}.
 */
class SpectorAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withPropertyValues("spector.memory.enabled=true")
            .withConfiguration(AutoConfigurations.of(SpectorAutoConfiguration.class))
            ;
    @Configuration(proxyBeanMethods = false)
    static class TestDependenciesConfiguration {
        @Bean
        EmbeddingProvider embeddingProvider() {
            EmbeddingProvider mock = Mockito.mock(EmbeddingProvider.class);
            Mockito.when(mock.dimensions()).thenReturn(384);
            Mockito.when(mock.modelName()).thenReturn("mock-embed");
            return mock;
        }
    }
    @Configuration(proxyBeanMethods = false)
    static class TestMeterRegistryConfiguration {
        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }
    @Configuration
    static class TestWrapSpringAIAutoConfiguration{
        @Bean
        EmbeddingModel embeddingModel(){
            return  Mockito.mock(EmbeddingModel.class);
        }

    }
    @Test
    void defaultConfiguration_createsMemoryBean() {
        this.contextRunner
                .withPropertyValues("spector.memory.dimensions=384")
                .withUserConfiguration(TestDependenciesConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(SpectorMemory.class);
                    SpectorMemory memory = context.getBean(SpectorMemory.class);
                    assertThat(memory).isNotNull();
                });
    }

    @Test
    void withMeterRegistry_wrapsMemoryWithMeteredDecorator() {
        this.contextRunner
                .withUserConfiguration(TestDependenciesConfiguration.class)
                .withUserConfiguration(TestMeterRegistryConfiguration.class)
                .withPropertyValues("spector.memory.dimensions=384", "spector.metrics.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(SpectorMemory.class);
                    SpectorMemory memory = context.getBean(SpectorMemory.class);
                    assertThat(memory).isInstanceOf(MeteredSpectorMemory.class);
                });
    }

    @Test
    public void shouldCreateEmbeddingProviderBean(){
        contextRunner.withUserConfiguration(TestWrapSpringAIAutoConfiguration.class).run(context -> {
            assertThat(context).hasSingleBean(EmbeddingProvider.class);
        });
    }
    @Test
    void shouldNotCreateEmbeddingProviderBeanWithoutEmbeddingModelBean(){
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(EmbeddingProvider.class);
        });
    }
    @Test
    public void shouldCreateOllamaEmbeddingModelProvider(){
        contextRunner.withPropertyValues("spector.embedding.provider-name=Ollama")
                .run(context -> {
                    assertThat(context).hasBean("ollamaEmbeddingProvider");
                });
    }
    @Test
    void shouldNotCreateOllamaEmbeddingProviderWithFalsePropertiesValues(){
        contextRunner.withPropertyValues("spector.embedding.provider-name=Gpt")
                .run(context -> {
                    assertThat(context).doesNotHaveBean("ollamaEmbeddingProvider");
                });
    }
    @Test
    void shouldNotCreateOllamaEmbeddingProviderWithoutSettingPropertiesValues(){
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean("ollamaEmbeddingProvider");
        });
    }
    @Test
    public void shouldCreateOpenAIEmbeddingModelProvider(){
        contextRunner.withPropertyValues("spector.embedding.provider-name=OpenAi","spector.embedding.model=Gpt4.5","spector.embedding.type=LLM","spector.embedding.api_key=qdsf")
                .run(context -> {
                    assertThat(context).hasBean("openAiEmbeddingProvider");
                });
    }
    @Test
    void shouldNotCreateOpenAIEmbeddingProviderWithFalsePropertiesValues(){
        contextRunner.withPropertyValues("spector.embedding.provider-name=Mistral","spector.embedding.model=Gpt4.5","spector.embedding.type=LLM","spector.embedding.api_key=qdsf")
                .run(context -> {
                    assertThat(context).doesNotHaveBean("openAIEmbeddingProvider");
                });
    }
    @Test
    void shouldNotCreateOpenAIEmbeddingProviderWithoutSettingPropertiesValues(){
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean("openAIEmbeddingProvider");
        });
    }
    @Test
    public void shouldCreateMistralEmbeddingModelProvider(){
        contextRunner.withPropertyValues("spector.embedding.provider-name=Mistral","spector.embedding.model=Mistral Small 4","spector.embedding.type=LLM","spector.embedding.api_key=qdsf")
                .run(context -> {
                    assertThat(context).hasBean("mistralEmbeddingProvider");
                });
    }
    @Test
    void shouldNotCreateAnthropicEmbeddingProviderWithFalsePropertiesValues(){
        contextRunner.withPropertyValues("spector.embedding.provider-name=As","spector.embedding.model=Claude","spector.embedding.type=LLM","spector.embedding.api_key=qdsf")
                .run(context -> {
                    assertThat(context).doesNotHaveBean("mistralEmbeddingProvider");
                });
    }
    @Test
    void shouldNotCreateAnthropicEmbeddingProviderWithoutSettingPropertiesValues(){
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean("anthropicEmbeddingProvider");
        });
    }

    @Test
    void shouldNotCreateMistralEmbeddingProviderWithFalsePropertiesValues(){
        contextRunner.withPropertyValues("spector.embedding.provider-name=Mis","spector.embedding.model=Gpt4.5","spector.embedding.type=LLM","spector.embedding.api_key=qdsf")
                .run(context -> {
                    assertThat(context).doesNotHaveBean("mistralEmbeddingProvider");
                });
    }
    @Test
    void shouldNotCreateMistralEmbeddingProviderWithoutSettingPropertiesValues(){
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean("mistralEmbeddingProvider");
        });
    }
    @Test
    public void shouldCreateAzureOpenAiEmbeddingModelProvider(){
        contextRunner.withPropertyValues("spector.embedding.provider-name=AzureOpenAi","spector.embedding.model=gpt-4o","spector.embedding.type=LLM","spector.embedding.api_key=qdsf")
                .run(context -> {
                    assertThat(context).hasBean("azureOpenAiEmbeddingProvider");
                });
    }
    @Test
    void shouldNotCreateAzureOpenAiEmbeddingProviderWithFalsePropertiesValues(){
        contextRunner.withPropertyValues("spector.embedding.provider-name=As","spector.embedding.model=Claude","spector.embedding.type=LLM","spector.embedding.api_key=qdsf")
                .run(context -> {
                    assertThat(context).doesNotHaveBean("azureOpenAiEmbeddingProvider");
                });
    }
    @Test
    void shouldNotCreateAzureOpenAiEmbeddingProviderWithoutSettingPropertiesValues(){
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean("azureOpenAiEmbeddingProvider");
        });
    }
    @Test
    void shouldCreateVectorMemoryStoreBean(){
        contextRunner.withUserConfiguration(TestSpectorVectorMemoryStoreBeanConfiguration.class)
                .run(context -> {
                    assertThat(context).hasBean("spectorVectorMemoryStore");
                });
    }
    @Test
    void shouldCreateVectorClientStoreBean(){
        contextRunner.withUserConfiguration(TestSpectorVectorClientStoreBeanConfiguration.class)
                .run(context -> {
                    assertThat(context).hasBean("spectorVectorClientStore");
                });
    }
    @Configuration
    static class TestSpectorVectorMemoryStoreBeanConfiguration{
        @Bean
        SpectorMemory memory(){
            return Mockito.mock(SpectorMemory.class);
        }

    }
    @Configuration
    static class TestSpectorVectorClientStoreBeanConfiguration{
        @Bean
        SpectorClient client(){
            return Mockito.mock(SpectorClient.class);
        }
    }

    @Test
    void shouldNotCreateBedrockEmbeddingProviderWithFalsePropertiesValues(){
        contextRunner.withPropertyValues("spector.embedding.provider-name=bdrok","spector.embedding.model=Gpt4.5","spector.embedding.type=LLM","spector.embedding.api_key=qdsf")
                .run(context -> {
                    assertThat(context).doesNotHaveBean("bedrockEmbeddingProvider");
                });
    }
    @Test
    void shouldNotCreateBedrockEmbeddingProviderWithoutSettingPropertiesValues(){
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean("openAIEmbeddingProvider");
        });
    }
    @Test
    public void shouldCreateGoogleEmbeddingModelProvider(){
        contextRunner.withPropertyValues("spector.embedding.provider-name=Google","spector.embedding.model=Gemini 3.5","spector.embedding.type=LLM","spector.embedding.api_key=qdsf")
                .run(context -> {
                    assertThat(context).hasBean("googleEmbeddingProvider");
                });
    }
    @Test
    void shouldNotCreateGoogleEmbeddingProviderWithFalsePropertiesValues(){
        contextRunner.withPropertyValues("spector.embedding.provider-name=bdrok","spector.embedding.model=Gpt4.5","spector.embedding.type=LLM","spector.embedding.api_key=qdsf")
                .run(context -> {
                    assertThat(context).doesNotHaveBean("googleEmbeddingProvider");
                });
    }
    @Test
    void shouldNotCreateGoogleEmbeddingProviderWithoutSettingPropertiesValues(){
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean("googleEmbeddingProvider");
        });
    }
    @Test
    void shouldNotProvideSpectorClientBean(){
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(SpectorClient.class);
        });
    }
    @Test
    void shouldProvideSpectorClientWithClientPropertiesBean(){
        contextRunner.withPropertyValues("spector.client.host=192.168.1.1","spector.client.port=36","spector.client.api_key=dgg").run(context -> {
            assertThat(context).hasSingleBean(SpectorClient.class);
            SpectorClient client = context.getBean(SpectorClient.class);
            assertThat(client.getBaseUrl()).isEqualTo("http://192.168.1.1:36");
        });
    }


}