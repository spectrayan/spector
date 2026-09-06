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
package com.spectrayan.spector.cli;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;

class SpectorCliConfigInitializerTest {

    @Test
    void initialize_mapsCliOverrideFlagsToSpectorProperties() {
        String[] args = new String[]{
                "--dims", "512",
                "--capacity", "20000",
                "--data-dir", "/tmp/spector-test",
                "--namespace", "test-ns",
                "--ollama-url", "http://ollama.local:11434",
                "--ollama-model", "qwen3-embedding:custom"
        };

        var context = new AnnotationConfigApplicationContext();
        var initializer = new SpectorCliConfigInitializer(args);
        initializer.initialize(context);

        ConfigurableEnvironment env = context.getEnvironment();
        assertThat(env.getProperty("spector.memory.dimensions")).isEqualTo("512");
        assertThat(env.getProperty("spector.provider.embedding.dimensions")).isEqualTo("512");
        assertThat(env.getProperty("spector.memory.capacity")).isEqualTo("20000");
        assertThat(env.getProperty("spector.memory.persistence-path")).isEqualTo("/tmp/spector-test");
        assertThat(env.getProperty("spector.memory.persistence-mode")).isEqualTo("DISK");
        assertThat(env.getProperty("spector.memory.namespace")).isEqualTo("test-ns");
        assertThat(env.getProperty("spector.provider.embedding.base-url")).isEqualTo("http://ollama.local:11434");
        assertThat(env.getProperty("spector.provider.embedding.model")).isEqualTo("qwen3-embedding:custom");
    }

    @Test
    void initialize_mapsProfileFlagToActiveProfile() {
        String[] args = new String[]{"--profile", "staging"};

        var context = new AnnotationConfigApplicationContext();
        var initializer = new SpectorCliConfigInitializer(args);
        initializer.initialize(context);

        assertThat(context.getEnvironment().getActiveProfiles()).contains("staging");
    }
}
