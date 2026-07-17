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
package com.spectrayan.spector.provider.generation;

/**
 * Options for customizing text generation.
 *
 * @param temperature   sampling temperature (0.0 = deterministic, 1.0 = creative)
 * @param maxTokens     maximum tokens to generate (0 = model default)
 * @param topP          nucleus sampling threshold
 * @param stopSequences sequences that stop generation when encountered
 */
public record GenerationOptions(
        float temperature,
        int maxTokens,
        float topP,
        String[] stopSequences
) {
    /** Default generation options. */
    public static final GenerationOptions DEFAULT = new GenerationOptions(0.3f, 1024, 0.9f, new String[0]);

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private float temperature = DEFAULT.temperature;
        private int maxTokens = DEFAULT.maxTokens;
        private float topP = DEFAULT.topP;
        private String[] stopSequences = DEFAULT.stopSequences;

        public Builder temperature(float temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder maxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public Builder topP(float topP) {
            this.topP = topP;
            return this;
        }

        public Builder stopSequences(String... stopSequences) {
            this.stopSequences = stopSequences;
            return this;
        }

        public GenerationOptions build() {
            return new GenerationOptions(temperature, maxTokens, topP, stopSequences);
        }
    }
}
