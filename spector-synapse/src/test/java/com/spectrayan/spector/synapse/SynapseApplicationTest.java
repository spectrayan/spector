/*
 * Copyright 2025–2026 Spectrayan. Licensed under the Apache License, Version 2.0.
 */
package com.spectrayan.spector.synapse;

import com.spectrayan.spector.synapse.config.SynapseProperties;
import com.spectrayan.spector.synapse.config.SynapseProperties.*;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test — verifies the Synapse module compiles and basic wiring works.
 */
class SynapseApplicationTest {

    @Test
    void propertiesRecordDefaults() {
        var props = new SynapseProperties(0, null, null, null, null, null);
        assertThat(props.port()).isEqualTo(7070);
        assertThat(props.apiKey()).isEqualTo("spector-dev-key");
        assertThat(props.dataDir()).isEqualTo("./spector-data");
        assertThat(props.ollama()).isNotNull();
        assertThat(props.memory()).isNotNull();
        assertThat(props.cors()).isNotNull();
    }

    @Test
    void propertiesRecordCustomValues() {
        var props = new SynapseProperties(
                8080, "my-api-key", "/opt/spector",
                new OllamaProperties("https://ollama.example.com", "gpt-4", "bge-large"),
                new MemoryProperties(1000, 768),
                new CorsProperties("http://localhost:3000"));
        assertThat(props.port()).isEqualTo(8080);
        assertThat(props.apiKey()).isEqualTo("my-api-key");
        assertThat(props.dataDir()).isEqualTo("/opt/spector");
        assertThat(props.ollama().baseUrl()).isEqualTo("https://ollama.example.com");
        assertThat(props.ollama().model()).isEqualTo("gpt-4");
        assertThat(props.ollama().embedModel()).isEqualTo("bge-large");
        assertThat(props.memory().maxMemories()).isEqualTo(1000);
        assertThat(props.memory().dimensions()).isEqualTo(768);
        assertThat(props.cors().allowedOrigins()).isEqualTo("http://localhost:3000");
    }

    @Test
    void ollamaPropertiesDefaults() {
        var ollama = new OllamaProperties(null, null, null);
        assertThat(ollama.baseUrl()).isEqualTo("http://localhost:11434");
        assertThat(ollama.model()).isEqualTo("llama3.2");
        assertThat(ollama.embedModel()).isEqualTo("nomic-embed-text");
    }

    @Test
    void memoryPropertiesDefaults() {
        var memory = new MemoryProperties(0, 0);
        assertThat(memory.maxMemories()).isEqualTo(0);
        assertThat(memory.dimensions()).isEqualTo(0);
    }

    @Test
    void corsPropertiesDefaults() {
        var cors = new CorsProperties(null);
        assertThat(cors.allowedOrigins()).isEqualTo("http://localhost:4200");
    }
}
