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
package com.spectrayan.spector.memory.aisme.enactment;

import java.util.Objects;

/**
 * Master configuration aggregating all parameters for the Dual-Process Persona Enactment engine (ADR-0032).
 */
public record EnactmentConfig(
        RecallConfig recall,
        AppraisalConfig appraisal,
        StanceConfig stance,
        DeliberationConfig deliberation
) {

    public EnactmentConfig {
        recall = (recall != null) ? recall : RecallConfig.defaultConfig();
        appraisal = (appraisal != null) ? appraisal : AppraisalConfig.defaultConfig();
        stance = (stance != null) ? stance : StanceConfig.defaultConfig();
        deliberation = (deliberation != null) ? deliberation : DeliberationConfig.defaultConfig();
    }

    public static EnactmentConfig defaultConfig() {
        return new EnactmentConfig(
                RecallConfig.defaultConfig(),
                AppraisalConfig.defaultConfig(),
                StanceConfig.defaultConfig(),
                DeliberationConfig.defaultConfig()
        );
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private RecallConfig recall = RecallConfig.defaultConfig();
        private AppraisalConfig appraisal = AppraisalConfig.defaultConfig();
        private StanceConfig stance = StanceConfig.defaultConfig();
        private DeliberationConfig deliberation = DeliberationConfig.defaultConfig();

        public Builder recall(RecallConfig recall) {
            this.recall = recall;
            return this;
        }

        public Builder appraisal(AppraisalConfig appraisal) {
            this.appraisal = appraisal;
            return this;
        }

        public Builder stance(StanceConfig stance) {
            this.stance = stance;
            return this;
        }

        public Builder deliberation(DeliberationConfig deliberation) {
            this.deliberation = deliberation;
            return this;
        }

        public EnactmentConfig build() {
            return new EnactmentConfig(recall, appraisal, stance, deliberation);
        }
    }
}
