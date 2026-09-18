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
package com.spectrayan.spector.memory.model;

public record EmbodiedKinesicsDNA(
        float smileAsymmetry,
        float browAsymmetry,
        float eyeContactBaseline,
        float noddingCadence,
        float gesturalExpressiveness) {

    public static final EmbodiedKinesicsDNA NEUTRAL = new EmbodiedKinesicsDNA(0.0f, 0.0f, 0.70f, 0.50f, 0.50f);

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private float smileAsymmetry = 0.0f;
        private float browAsymmetry = 0.0f;
        private float eyeContactBaseline = 0.70f;
        private float noddingCadence = 0.50f;
        private float gesturalExpressiveness = 0.50f;

        public Builder smileAsymmetry(float smileAsymmetry) {
            this.smileAsymmetry = smileAsymmetry;
            return this;
        }

        public Builder browAsymmetry(float browAsymmetry) {
            this.browAsymmetry = browAsymmetry;
            return this;
        }

        public Builder eyeContactBaseline(float eyeContactBaseline) {
            this.eyeContactBaseline = eyeContactBaseline;
            return this;
        }

        public Builder noddingCadence(float noddingCadence) {
            this.noddingCadence = noddingCadence;
            return this;
        }

        public Builder gesturalExpressiveness(float gesturalExpressiveness) {
            this.gesturalExpressiveness = gesturalExpressiveness;
            return this;
        }

        public EmbodiedKinesicsDNA build() {
            return new EmbodiedKinesicsDNA(smileAsymmetry, browAsymmetry, eyeContactBaseline, noddingCadence, gesturalExpressiveness);
        }
    }
}
