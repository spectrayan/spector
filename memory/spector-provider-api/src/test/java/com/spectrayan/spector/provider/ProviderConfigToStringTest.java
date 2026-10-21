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
package com.spectrayan.spector.provider;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderConfigToStringTest {

    @Test
    void toStringNeverContainsTheApiKey() {
        String key = "sk-test-0123456789abcdef";
        var config = new ProviderConfig("openai", "openai", "gpt-4o-mini", key, "", 0,
                Map.of("header.Authorization", "Bearer " + key));

        assertThat(config.toString())
                .doesNotContain(key)
                .contains("apiKey=***")
                .contains("model=gpt-4o-mini")
                .contains("header.Authorization");
        assertThat(config.apiKey()).isEqualTo(key);
    }

    @Test
    void toStringShowsAnEmptyKeyAsEmpty() {
        var config = ProviderConfig.local("ollama", "ollama", "llama3.2", "http://localhost:11434");
        assertThat(config.toString()).contains("apiKey=,");
    }
}
