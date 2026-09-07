/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-memory/LICENSE
 *
 * Change Date: May 27, 2030
 * Change License: Apache License, Version 2.0
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
