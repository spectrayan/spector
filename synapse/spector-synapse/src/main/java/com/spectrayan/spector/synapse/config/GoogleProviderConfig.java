package com.spectrayan.spector.synapse.config;

import com.spectrayan.spector.provider.ProviderConfig;
import com.spectrayan.spector.provider.ProviderRegistry;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.generation.LlmProvider;
import com.spectrayan.spector.provider.google.GoogleProviderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * Registers Google Gemini as an {@link LlmProvider} / {@link EmbeddingProvider}
 * when {@code spector.provider.generation.type=google} (resp. {@code embedding.type=google}).
 *
 * <p>Mirrors {@link EmbeddingProviderConfig}'s Ollama wiring, but delegates
 * construction to {@link GoogleProviderFactory} instead of hardcoding Ollama.
 * The {@code @ConditionalOnMissingBean} guard on the default Ollama beans means
 * this backs Ollama off automatically once these beans are present.</p>
 */
@Configuration
@EnableConfigurationProperties({
        GoogleProviderConfig.GenerationProps.class,
        GoogleProviderConfig.EmbeddingProps.class
})
public class GoogleProviderConfig {

    private static final Logger log = LoggerFactory.getLogger(GoogleProviderConfig.class);
    private static final GoogleProviderFactory FACTORY = new GoogleProviderFactory();

    @Bean
    @ConditionalOnProperty(prefix = "spector.provider.generation", name = "type", havingValue = "google")
    @ConditionalOnMissingBean(LlmProvider.class)
    LlmProvider googleLlmProvider(ProviderRegistry registry, GenerationProps props) {
        ProviderConfig config = new ProviderConfig(
                "google", "google", props.model, props.apiKey, "", 0, props.properties);
        LlmProvider llm = FACTORY.createGenerationProvider(config)
                .orElseThrow(() -> new IllegalStateException("GoogleProviderFactory returned no generation provider"));
        registry.registerGeneration("google", llm);
        registry.activateGeneration("google");
        log.info("[GoogleProviderConfig] Registered + activated Gemini generation provider: model={}", props.model);
        return llm;
    }

    @Bean
    @ConditionalOnProperty(prefix = "spector.provider.embedding", name = "type", havingValue = "google")
    @ConditionalOnMissingBean(EmbeddingProvider.class)
    EmbeddingProvider googleEmbeddingProvider(ProviderRegistry registry, EmbeddingProps props) {
        ProviderConfig config = new ProviderConfig(
                "google", "google", props.model, props.apiKey, "", props.dimensions, Map.of());
        EmbeddingProvider embedder = FACTORY.createEmbeddingProvider(config)
                .orElseThrow(() -> new IllegalStateException("GoogleProviderFactory returned no embedding provider"));
        registry.registerEmbedding("google", embedder);
        registry.activateEmbedding("google");
        log.info("[GoogleProviderConfig] Registered + activated Gemini embedding provider: model={}", props.model);
        return embedder;
    }

    @ConfigurationProperties(prefix = "spector.provider.generation")
    public static class GenerationProps {
        private String model;
        private String apiKey;
        private Map<String, String> properties = Map.of();

        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public Map<String, String> getProperties() { return properties; }
        public void setProperties(Map<String, String> properties) { this.properties = properties; }
    }

    @ConfigurationProperties(prefix = "spector.provider.embedding")
    public static class EmbeddingProps {
        private String model;
        private String apiKey;
        private int dimensions;

        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public int getDimensions() { return dimensions; }
        public void setDimensions(int dimensions) { this.dimensions = dimensions; }
    }
}