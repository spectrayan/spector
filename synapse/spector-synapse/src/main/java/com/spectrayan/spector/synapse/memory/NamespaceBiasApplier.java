/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-synapse/LICENSE
 *
 * Change Date: July 6, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.synapse.memory;

import com.spectrayan.spector.memory.model.InterestDomain;
import com.spectrayan.spector.memory.model.InterestLevel;
import com.spectrayan.spector.memory.model.SalienceProfile;
import com.spectrayan.spector.synapse.catalog.NamespaceBias;

/**
 * Bind-time salience overlay (ADR-0029 §2.5.6). Domain focus becomes extra
 * {@link InterestDomain}s at MEDIUM; ICNU itself is unchanged.
 */
public final class NamespaceBiasApplier {

    private NamespaceBiasApplier() {
    }

    public static SalienceProfile apply(SalienceProfile base, NamespaceBias bias) {
        if (bias == null || bias == NamespaceBias.EMPTY) {
            return base;
        }
        if (bias.domainFocus() == null || bias.domainFocus().isEmpty()) {
            return base;
        }
        SalienceProfile src = base != null ? base : SalienceProfile.NEUTRAL;
        SalienceProfile.Builder builder = SalienceProfile.builder()
                .icnuWeights(src.icnuWeights())
                .alpha(src.alpha() != null ? src.alpha() : 0f)
                .beta(src.beta() != null ? src.beta() : 0f)
                .flashbulbThreshold(src.flashbulbThreshold())
                .recencyWeight(src.recencyWeight())
                .similarityThreshold(src.similarityThreshold())
                .persona(src.persona())
                .agentRelevanceBoost(src.agentRelevanceBoost());
        if (src.defaultProfile() != null) {
            builder.defaultProfile(src.defaultProfile());
        }
        for (InterestDomain domain : src.interests()) {
            builder.interest(domain);
        }
        for (InterestDomain domain : src.disinterests()) {
            builder.disinterest(domain);
        }
        for (String domain : bias.domainFocus()) {
            if (domain != null && !domain.isBlank()) {
                builder.interest(domain.trim(), InterestLevel.MEDIUM);
            }
        }
        return builder.build();
    }
}
