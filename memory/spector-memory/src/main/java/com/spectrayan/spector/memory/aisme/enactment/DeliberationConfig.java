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
 * Configuration parameters for System 2 Bounded Deliberation and trade-off heuristics (ADR-0032).
 */
public record DeliberationConfig(
        String fallbackDogma,
        String defaultTradeOffDeprioritized,
        String defaultTradeOffRationale,
        float urgencyBlindSpotThreshold,
        float copingDefensivenessThreshold,
        String urgencyBlindSpotMessage,
        String copingDefensivenessMessage,
        float lowIntensitySkipThreshold
) {

    public static final String DEFAULT_FALLBACK_DOGMA = "Uphold system integrity and operational excellence";
    public static final String DEFAULT_TRADEOFF_DEPRIORITIZED = "Short-term convenience";
    public static final String DEFAULT_TRADEOFF_RATIONALE = "Prioritize fundamental stability over hasty temporary fixes";
    public static final float DEFAULT_URGENCY_BLIND_SPOT_THRESHOLD = 0.5f;
    public static final float DEFAULT_COPING_DEFENSIVENESS_THRESHOLD = 0.0f;
    public static final String DEFAULT_URGENCY_BLIND_SPOT_MESSAGE = "Heightened urgency may bias toward premature action";
    public static final String DEFAULT_COPING_DEFENSIVENESS_MESSAGE = "Low perceived coping potential may elevate defensiveness";
    public static final float DEFAULT_LOW_INTENSITY_SKIP_THRESHOLD = 0.15f;

    public DeliberationConfig {
        fallbackDogma = (fallbackDogma != null && !fallbackDogma.isBlank()) ? fallbackDogma : DEFAULT_FALLBACK_DOGMA;
        defaultTradeOffDeprioritized = (defaultTradeOffDeprioritized != null && !defaultTradeOffDeprioritized.isBlank())
                ? defaultTradeOffDeprioritized
                : DEFAULT_TRADEOFF_DEPRIORITIZED;
        defaultTradeOffRationale = (defaultTradeOffRationale != null && !defaultTradeOffRationale.isBlank())
                ? defaultTradeOffRationale
                : DEFAULT_TRADEOFF_RATIONALE;
        urgencyBlindSpotMessage = (urgencyBlindSpotMessage != null && !urgencyBlindSpotMessage.isBlank())
                ? urgencyBlindSpotMessage
                : DEFAULT_URGENCY_BLIND_SPOT_MESSAGE;
        copingDefensivenessMessage = (copingDefensivenessMessage != null && !copingDefensivenessMessage.isBlank())
                ? copingDefensivenessMessage
                : DEFAULT_COPING_DEFENSIVENESS_MESSAGE;
    }

    public DeliberationConfig(
            String fallbackDogma,
            String defaultTradeOffDeprioritized,
            String defaultTradeOffRationale,
            float urgencyBlindSpotThreshold,
            float copingDefensivenessThreshold,
            String urgencyBlindSpotMessage,
            String copingDefensivenessMessage) {
        this(
                fallbackDogma,
                defaultTradeOffDeprioritized,
                defaultTradeOffRationale,
                urgencyBlindSpotThreshold,
                copingDefensivenessThreshold,
                urgencyBlindSpotMessage,
                copingDefensivenessMessage,
                DEFAULT_LOW_INTENSITY_SKIP_THRESHOLD
        );
    }

    public static DeliberationConfig defaultConfig() {
        return new DeliberationConfig(
                DEFAULT_FALLBACK_DOGMA,
                DEFAULT_TRADEOFF_DEPRIORITIZED,
                DEFAULT_TRADEOFF_RATIONALE,
                DEFAULT_URGENCY_BLIND_SPOT_THRESHOLD,
                DEFAULT_COPING_DEFENSIVENESS_THRESHOLD,
                DEFAULT_URGENCY_BLIND_SPOT_MESSAGE,
                DEFAULT_COPING_DEFENSIVENESS_MESSAGE,
                DEFAULT_LOW_INTENSITY_SKIP_THRESHOLD
        );
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String fallbackDogma = DEFAULT_FALLBACK_DOGMA;
        private String defaultTradeOffDeprioritized = DEFAULT_TRADEOFF_DEPRIORITIZED;
        private String defaultTradeOffRationale = DEFAULT_TRADEOFF_RATIONALE;
        private float urgencyBlindSpotThreshold = DEFAULT_URGENCY_BLIND_SPOT_THRESHOLD;
        private float copingDefensivenessThreshold = DEFAULT_COPING_DEFENSIVENESS_THRESHOLD;
        private String urgencyBlindSpotMessage = DEFAULT_URGENCY_BLIND_SPOT_MESSAGE;
        private String copingDefensivenessMessage = DEFAULT_COPING_DEFENSIVENESS_MESSAGE;
        private float lowIntensitySkipThreshold = DEFAULT_LOW_INTENSITY_SKIP_THRESHOLD;

        public Builder fallbackDogma(String dogma) {
            this.fallbackDogma = dogma;
            return this;
        }

        public Builder defaultTradeOffDeprioritized(String deprioritized) {
            this.defaultTradeOffDeprioritized = deprioritized;
            return this;
        }

        public Builder defaultTradeOffRationale(String rationale) {
            this.defaultTradeOffRationale = rationale;
            return this;
        }

        public Builder urgencyBlindSpotThreshold(float threshold) {
            this.urgencyBlindSpotThreshold = threshold;
            return this;
        }

        public Builder copingDefensivenessThreshold(float threshold) {
            this.copingDefensivenessThreshold = threshold;
            return this;
        }

        public Builder urgencyBlindSpotMessage(String message) {
            this.urgencyBlindSpotMessage = message;
            return this;
        }

        public Builder copingDefensivenessMessage(String message) {
            this.copingDefensivenessMessage = message;
            return this;
        }

        public Builder lowIntensitySkipThreshold(float threshold) {
            this.lowIntensitySkipThreshold = threshold;
            return this;
        }

        public DeliberationConfig build() {
            return new DeliberationConfig(
                    fallbackDogma,
                    defaultTradeOffDeprioritized,
                    defaultTradeOffRationale,
                    urgencyBlindSpotThreshold,
                    copingDefensivenessThreshold,
                    urgencyBlindSpotMessage,
                    copingDefensivenessMessage,
                    lowIntensitySkipThreshold
            );
        }
    }
}
